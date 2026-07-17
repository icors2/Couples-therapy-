-- Align live Supabase project with the Android app + Edge Functions schema
-- (repo: 20260717000000_init.sql). Preserves auth.users, profiles, relationships.

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Helpers expected by the app
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
-- Drop divergent / unused objects (session/message data was empty or incompatible)
-- ---------------------------------------------------------------------------
do $$
begin
  if exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'therapy_sessions'
  ) then
    alter publication supabase_realtime drop table public.therapy_sessions;
  end if;
  if exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'messages'
  ) then
    alter publication supabase_realtime drop table public.messages;
  end if;
  if exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'notifications'
  ) then
    alter publication supabase_realtime drop table public.notifications;
  end if;
end $$;

drop trigger if exists messages_touch_session on public.messages;
drop trigger if exists on_auth_user_created on auth.users;

drop function if exists public.touch_session_activity() cascade;
drop function if exists public.start_therapy_session(uuid) cascade;
drop function if exists public.respond_to_session(uuid, text) cascade;
drop function if exists public.end_therapy_session(uuid) cascade;
drop function if exists public.pair_with_code(text) cascade;
drop function if exists public.is_relationship_member(uuid) cascade;
drop function if exists public.my_relationship_id() cascade;
drop function if exists public.expire_inactive_sessions(int) cascade;

drop table if exists public.messages cascade;
drop table if exists public.therapy_sessions cascade;
drop table if exists public.sessions cascade;
drop table if exists public.device_tokens cascade;
drop table if exists public.user_settings cascade;
drop table if exists public.settings cascade;
drop table if exists public.ai_archives cascade;
drop table if exists public.ai_memory cascade;

-- Keep notification rows but reshape columns to match the app.
alter table public.notifications
  add column if not exists is_read boolean not null default false;

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'notifications' and column_name = 'read_at'
  ) then
    update public.notifications
    set is_read = true
    where read_at is not null and is_read = false;
  end if;
end $$;

alter table public.notifications
  alter column payload drop not null;

alter table public.notifications
  alter column payload set default null;

-- Enums already exist with correct labels in this project; ensure they do.
do $$ begin
  create type public.session_status as enum ('pending', 'active', 'ended', 'declined', 'expired');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.message_sender as enum ('partner_a', 'partner_b', 'ai', 'system');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.notification_type as enum (
    'session_invite', 'partner_joined', 'session_ended', 'partner_paired', 'session_timeout'
  );
exception when duplicate_object then null;
end $$;

-- Soften profiles/relationships constraints to match app nullability.
alter table public.profiles alter column email drop not null;
alter table public.relationships alter column partner2_id drop not null;

-- ---------------------------------------------------------------------------
-- App tables
-- ---------------------------------------------------------------------------
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

drop trigger if exists ai_memory_set_updated_at on public.ai_memory;
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

create table public.settings (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  dark_mode boolean,
  notifications_enabled boolean not null default true,
  ai_provider text not null default 'openai',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

drop trigger if exists settings_set_updated_at on public.settings;
create trigger settings_set_updated_at
before update on public.settings
for each row execute function public.set_updated_at();

insert into public.settings (user_id)
select id from public.profiles
on conflict (user_id) do nothing;

-- Ensure profiles have updated_at trigger
drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Auth bootstrap
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
-- RLS: replace old policies with app policies
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.relationships enable row level security;
alter table public.sessions enable row level security;
alter table public.messages enable row level security;
alter table public.ai_memory enable row level security;
alter table public.ai_archives enable row level security;
alter table public.notifications enable row level security;
alter table public.settings enable row level security;

do $$
declare
  r record;
begin
  for r in
    select policyname, tablename
    from pg_policies
    where schemaname = 'public'
      and tablename in (
        'profiles', 'relationships', 'sessions', 'messages',
        'ai_memory', 'ai_archives', 'notifications', 'settings'
      )
  loop
    execute format('drop policy if exists %I on public.%I', r.policyname, r.tablename);
  end loop;
end $$;

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

create policy "relationships_select_member"
on public.relationships for select
using (partner1_id = auth.uid() or partner2_id = auth.uid());

create policy "relationships_insert_self"
on public.relationships for insert
with check (partner1_id = auth.uid());

create policy "relationships_update_member"
on public.relationships for update
using (partner1_id = auth.uid() or partner2_id = auth.uid());

create policy "sessions_select_member"
on public.sessions for select
using (relationship_id = public.current_user_relationship_id());

create policy "sessions_insert_member"
on public.sessions for insert
with check (
  relationship_id = public.current_user_relationship_id()
  and started_by = auth.uid()
);

create policy "sessions_update_member"
on public.sessions for update
using (relationship_id = public.current_user_relationship_id());

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

create policy "ai_memory_select_member"
on public.ai_memory for select
using (relationship_id = public.current_user_relationship_id());

-- Edge Functions use service role for writes; allow members to read archives.
create policy "ai_archives_select_member"
on public.ai_archives for select
using (relationship_id = public.current_user_relationship_id());

create policy "notifications_select_own"
on public.notifications for select
using (user_id = auth.uid());

create policy "notifications_update_own"
on public.notifications for update
using (user_id = auth.uid());

create policy "settings_select_own"
on public.settings for select
using (user_id = auth.uid());

create policy "settings_upsert_own"
on public.settings for insert
with check (user_id = auth.uid());

create policy "settings_update_own"
on public.settings for update
using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Realtime
-- ---------------------------------------------------------------------------
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.sessions;
alter publication supabase_realtime add table public.notifications;

-- ---------------------------------------------------------------------------
-- Session timeout helper (used by session-timeout Edge Function)
-- ---------------------------------------------------------------------------
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
