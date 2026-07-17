-- AI Couples Therapy — initial schema
-- Separates full transcripts (messages) from structured therapeutic memory (ai_memory).

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
create or replace function public.generate_pair_code()
returns text
language plpgsql
as $$
declare
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  result text := '';
  i int;
begin
  for i in 1..6 loop
    result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return result;
end;
$$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = timezone('utc', now());
  return new;
end;
$$;

create or replace function public.current_user_relationship_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select relationship_id from public.profiles where id = auth.uid();
$$;

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text,
  display_name text,
  photo_url text,
  google_id text,
  pair_code text not null unique default public.generate_pair_code(),
  relationship_id uuid,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create unique index profiles_pair_code_idx on public.profiles (pair_code);
create index profiles_relationship_id_idx on public.profiles (relationship_id);

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Relationships
-- ---------------------------------------------------------------------------
create table public.relationships (
  id uuid primary key default gen_random_uuid(),
  partner1_id uuid not null references public.profiles (id) on delete cascade,
  partner2_id uuid references public.profiles (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  constraint relationships_two_distinct check (partner2_id is null or partner1_id <> partner2_id)
);

alter table public.profiles
  add constraint profiles_relationship_id_fkey
  foreign key (relationship_id) references public.relationships (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Sessions
-- ---------------------------------------------------------------------------
create type public.session_status as enum ('pending', 'active', 'ended', 'declined', 'expired');

create table public.sessions (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null references public.relationships (id) on delete cascade,
  started_by uuid not null references public.profiles (id),
  started_at timestamptz not null default timezone('utc', now()),
  ended_at timestamptz,
  duration_seconds int,
  ended_by uuid references public.profiles (id),
  status public.session_status not null default 'pending',
  last_user_message_at timestamptz,
  partner_a_joined boolean not null default false,
  partner_b_joined boolean not null default false
);

create index sessions_relationship_id_idx on public.sessions (relationship_id);
create index sessions_status_idx on public.sessions (status);

-- ---------------------------------------------------------------------------
-- Messages (full transcript — never deleted by memory compression)
-- ---------------------------------------------------------------------------
create type public.message_sender as enum ('partner_a', 'partner_b', 'ai', 'system');

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.sessions (id) on delete cascade,
  sender public.message_sender not null,
  sender_user_id uuid references public.profiles (id),
  content text not null check (char_length(content) > 0 and char_length(content) <= 8000),
  created_at timestamptz not null default timezone('utc', now()),
  read_by uuid[] not null default '{}',
  token_count int,
  model text
);

create index messages_session_id_created_at_idx on public.messages (session_id, created_at);

create or replace function public.touch_session_on_user_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.sender in ('partner_a', 'partner_b') then
    update public.sessions
      set last_user_message_at = new.created_at
      where id = new.session_id;
  end if;
  return new;
end;
$$;

create trigger messages_touch_session
after insert on public.messages
for each row execute function public.touch_session_on_user_message();

-- ---------------------------------------------------------------------------
-- AI Memory (versioned structured handoffs)
-- ---------------------------------------------------------------------------
create table public.ai_memory (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null references public.relationships (id) on delete cascade,
  version int not null,
  memory_json jsonb not null default '{}'::jsonb,
  sessions_included int not null default 0,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  unique (relationship_id, version)
);

create trigger ai_memory_set_updated_at
before update on public.ai_memory
for each row execute function public.set_updated_at();

create table public.ai_archives (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null references public.relationships (id) on delete cascade,
  archive_number int not null,
  summary jsonb not null,
  created_at timestamptz not null default timezone('utc', now()),
  unique (relationship_id, archive_number)
);

-- ---------------------------------------------------------------------------
-- Notifications & settings
-- ---------------------------------------------------------------------------
create type public.notification_type as enum (
  'session_invite',
  'partner_joined',
  'session_ended',
  'partner_paired',
  'session_timeout'
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  type public.notification_type not null,
  title text not null,
  body text not null,
  payload jsonb,
  is_read boolean not null default false,
  created_at timestamptz not null default timezone('utc', now())
);

create index notifications_user_id_created_at_idx on public.notifications (user_id, created_at desc);

create table public.settings (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  dark_mode boolean,
  notifications_enabled boolean not null default true,
  ai_provider text not null default 'openai',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

create trigger settings_set_updated_at
before update on public.settings
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Auth bootstrap: create profile on signup
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  code text;
  attempts int := 0;
begin
  loop
    code := public.generate_pair_code();
    begin
      insert into public.profiles (id, email, display_name, photo_url, google_id, pair_code)
      values (
        new.id,
        new.email,
        coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data->>'avatar_url', new.raw_user_meta_data->>'picture'),
        coalesce(new.raw_user_meta_data->>'sub', new.raw_user_meta_data->>'provider_id'),
        code
      );
      insert into public.settings (user_id) values (new.id)
      on conflict (user_id) do nothing;
      exit;
    exception when unique_violation then
      attempts := attempts + 1;
      if attempts > 8 then
        raise;
      end if;
    end;
  end loop;
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.relationships enable row level security;
alter table public.sessions enable row level security;
alter table public.messages enable row level security;
alter table public.ai_memory enable row level security;
alter table public.ai_archives enable row level security;
alter table public.notifications enable row level security;
alter table public.settings enable row level security;

-- Profiles
create policy "profiles_select_own_or_partner"
on public.profiles for select
using (
  id = auth.uid()
  or relationship_id = public.current_user_relationship_id()
);

create policy "profiles_update_own"
on public.profiles for update
using (id = auth.uid())
with check (id = auth.uid());

create policy "profiles_insert_own"
on public.profiles for insert
with check (id = auth.uid());

-- Relationships
create policy "relationships_select_member"
on public.relationships for select
using (partner1_id = auth.uid() or partner2_id = auth.uid());

create policy "relationships_insert_self"
on public.relationships for insert
with check (partner1_id = auth.uid());

create policy "relationships_update_member"
on public.relationships for update
using (partner1_id = auth.uid() or partner2_id = auth.uid());

-- Sessions
create policy "sessions_select_member"
on public.sessions for select
using (
  relationship_id = public.current_user_relationship_id()
);

create policy "sessions_insert_member"
on public.sessions for insert
with check (
  relationship_id = public.current_user_relationship_id()
  and started_by = auth.uid()
);

create policy "sessions_update_member"
on public.sessions for update
using (relationship_id = public.current_user_relationship_id());

-- Messages
create policy "messages_select_member"
on public.messages for select
using (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and s.relationship_id = public.current_user_relationship_id()
  )
);

create policy "messages_insert_member"
on public.messages for insert
with check (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and s.relationship_id = public.current_user_relationship_id()
      and s.status in ('pending', 'active')
  )
  and (
    sender_user_id = auth.uid()
    or sender = 'system'
  )
);

create policy "messages_update_member"
on public.messages for update
using (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and s.relationship_id = public.current_user_relationship_id()
  )
);

-- Memory / archives
create policy "ai_memory_select_member"
on public.ai_memory for select
using (relationship_id = public.current_user_relationship_id());

create policy "ai_archives_select_member"
on public.ai_archives for select
using (relationship_id = public.current_user_relationship_id());

-- Notifications
create policy "notifications_select_own"
on public.notifications for select
using (user_id = auth.uid());

create policy "notifications_update_own"
on public.notifications for update
using (user_id = auth.uid());

-- Settings
create policy "settings_select_own"
on public.settings for select
using (user_id = auth.uid());

create policy "settings_upsert_own"
on public.settings for insert
with check (user_id = auth.uid());

create policy "settings_update_own"
on public.settings for update
using (user_id = auth.uid());

-- Realtime
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.sessions;
alter publication supabase_realtime add table public.notifications;

-- Inactivity helper (call from scheduled edge function / cron)
create or replace function public.expire_inactive_sessions(timeout_minutes int default 10)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  affected int;
begin
  with expired as (
    update public.sessions
      set status = 'expired',
          ended_at = timezone('utc', now()),
          duration_seconds = extract(epoch from (timezone('utc', now()) - started_at))::int
      where status = 'active'
        and coalesce(last_user_message_at, started_at) < timezone('utc', now()) - make_interval(mins => timeout_minutes)
      returning id, relationship_id
  ),
  partners as (
    select e.id as session_id, r.partner1_id, r.partner2_id
    from expired e
    join public.relationships r on r.id = e.relationship_id
  )
  insert into public.notifications (user_id, type, title, body, payload)
  select partner1_id, 'session_timeout', 'Session ended', 'Your therapy session ended after 10 minutes of inactivity.',
         jsonb_build_object('session_id', session_id)
  from partners
  union all
  select partner2_id, 'session_timeout', 'Session ended', 'Your therapy session ended after 10 minutes of inactivity.',
         jsonb_build_object('session_id', session_id)
  from partners
  where partner2_id is not null;

  get diagnostics affected = row_count;
  return affected;
end;
$$;
