-- AI Couples Therapy — initial schema
-- Apply via Supabase SQL editor or: supabase db push

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Profiles (extends auth.users)
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null,
    display_name text,
    photo_url text,
    google_id text,
    pair_code text not null unique,
    relationship_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists profiles_pair_code_idx on public.profiles (pair_code);
create index if not exists profiles_relationship_id_idx on public.profiles (relationship_id);

-- ---------------------------------------------------------------------------
-- Relationships
-- ---------------------------------------------------------------------------
create table if not exists public.relationships (
    id uuid primary key default gen_random_uuid(),
    partner1_id uuid not null references public.profiles (id) on delete cascade,
    partner2_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint relationships_distinct_partners check (partner1_id <> partner2_id),
    constraint relationships_unique_pair unique (partner1_id, partner2_id)
);

alter table public.profiles
    drop constraint if exists profiles_relationship_id_fkey;

alter table public.profiles
    add constraint profiles_relationship_id_fkey
    foreign key (relationship_id) references public.relationships (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Sessions
-- ---------------------------------------------------------------------------
create type public.session_status as enum (
    'pending',
    'active',
    'ended',
    'declined',
    'expired'
);

create table if not exists public.therapy_sessions (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references public.relationships (id) on delete cascade,
    started_by uuid not null references public.profiles (id) on delete cascade,
    started_at timestamptz not null default now(),
    ended_at timestamptz,
    duration_seconds integer,
    ended_by uuid references public.profiles (id),
    status public.session_status not null default 'pending',
    last_activity_at timestamptz not null default now(),
    partner_joined_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists therapy_sessions_relationship_idx
    on public.therapy_sessions (relationship_id, started_at desc);

-- ---------------------------------------------------------------------------
-- Messages
-- ---------------------------------------------------------------------------
create type public.message_sender as enum ('partner_a', 'partner_b', 'ai', 'system');

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references public.therapy_sessions (id) on delete cascade,
    sender_id uuid references public.profiles (id) on delete set null,
    sender_role public.message_sender not null,
    content text not null,
    tokens integer,
    model text,
    read_by_partner1 boolean not null default false,
    read_by_partner2 boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists messages_session_created_idx
    on public.messages (session_id, created_at);

-- ---------------------------------------------------------------------------
-- AI Memory (versioned living therapeutic memory)
-- ---------------------------------------------------------------------------
create table if not exists public.ai_memory (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references public.relationships (id) on delete cascade,
    version integer not null,
    memory_json jsonb not null,
    sessions_included integer not null default 0,
    is_current boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ai_memory_unique_version unique (relationship_id, version)
);

create index if not exists ai_memory_current_idx
    on public.ai_memory (relationship_id)
    where is_current = true;

-- ---------------------------------------------------------------------------
-- AI Archives (compressed blocks of 5 sessions)
-- ---------------------------------------------------------------------------
create table if not exists public.ai_archives (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references public.relationships (id) on delete cascade,
    archive_number integer not null,
    summary_json jsonb not null,
    sessions_from integer not null,
    sessions_to integer not null,
    created_at timestamptz not null default now(),
    constraint ai_archives_unique_number unique (relationship_id, archive_number)
);

-- ---------------------------------------------------------------------------
-- Notifications (in-app; FCM tokens optional)
-- ---------------------------------------------------------------------------
create type public.notification_type as enum (
    'session_invite',
    'partner_joined',
    'session_ended',
    'session_timeout',
    'partner_paired'
);

create table if not exists public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    type public.notification_type not null,
    title text not null,
    body text not null,
    payload jsonb not null default '{}'::jsonb,
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists notifications_user_idx
    on public.notifications (user_id, created_at desc);

create table if not exists public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    token text not null unique,
    platform text not null default 'android',
    created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Settings
-- ---------------------------------------------------------------------------
create table if not exists public.user_settings (
    user_id uuid primary key references public.profiles (id) on delete cascade,
    dark_mode boolean not null default false,
    ai_model text not null default 'gpt-4o-mini',
    notify_session_invite boolean not null default true,
    notify_session_end boolean not null default true,
    updated_at timestamptz not null default now()
);

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
    i integer;
begin
    for i in 1..6 loop
        result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    return result;
end;
$$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    code text;
    attempts integer := 0;
begin
    loop
        code := public.generate_pair_code();
        begin
            insert into public.profiles (id, email, display_name, photo_url, google_id, pair_code)
            values (
                new.id,
                coalesce(new.email, ''),
                coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', split_part(coalesce(new.email, 'user'), '@', 1)),
                new.raw_user_meta_data->>'avatar_url',
                new.raw_user_meta_data->>'provider_id',
                code
            );
            insert into public.user_settings (user_id) values (new.id)
            on conflict (user_id) do nothing;
            exit;
        exception when unique_violation then
            attempts := attempts + 1;
            if attempts > 10 then
                raise;
            end if;
        end;
    end loop;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_updated_at on public.profiles;
create trigger profiles_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

drop trigger if exists ai_memory_updated_at on public.ai_memory;
create trigger ai_memory_updated_at
    before update on public.ai_memory
    for each row execute function public.set_updated_at();

-- Pair partners by code
create or replace function public.pair_with_code(partner_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
    partner public.profiles%rowtype;
    my_profile public.profiles%rowtype;
    rel_id uuid;
begin
    if me is null then
        raise exception 'Not authenticated';
    end if;

    select * into my_profile from public.profiles where id = me;
    if my_profile.relationship_id is not null then
        raise exception 'You are already paired';
    end if;

    select * into partner
    from public.profiles
    where upper(pair_code) = upper(partner_code)
    limit 1;

    if partner.id is null then
        raise exception 'Invalid pair code';
    end if;

    if partner.id = me then
        raise exception 'You cannot pair with yourself';
    end if;

    if partner.relationship_id is not null then
        raise exception 'That partner is already paired';
    end if;

    insert into public.relationships (partner1_id, partner2_id)
    values (me, partner.id)
    returning id into rel_id;

    update public.profiles set relationship_id = rel_id where id in (me, partner.id);

    insert into public.notifications (user_id, type, title, body, payload)
    values
        (partner.id, 'partner_paired', 'Partner connected',
         coalesce(my_profile.display_name, 'Your partner') || ' paired with you.',
         jsonb_build_object('relationship_id', rel_id)),
        (me, 'partner_paired', 'Partner connected',
         coalesce(partner.display_name, 'Your partner') || ' is now paired with you.',
         jsonb_build_object('relationship_id', rel_id));

    -- Seed empty therapeutic memory
    insert into public.ai_memory (relationship_id, version, memory_json, sessions_included, is_current)
    values (
        rel_id,
        1,
        jsonb_build_object(
            'relationship_summary', 'New couple beginning therapy.',
            'major_conflicts', '[]'::jsonb,
            'communication_patterns', '[]'::jsonb,
            'wins', '[]'::jsonb,
            'goals', '[]'::jsonb,
            'follow_up_topics', '[]'::jsonb,
            'emotional_progress', '',
            'next_session_focus', 'Build rapport and understand each partner''s hopes for therapy.',
            'agreed_commitments', '[]'::jsonb,
            'unresolved_issues', '[]'::jsonb,
            'sessions_included', 0
        ),
        0,
        true
    );

    return rel_id;
end;
$$;

-- Membership helpers for RLS
create or replace function public.is_relationship_member(rel uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.relationships r
        where r.id = rel
          and (r.partner1_id = auth.uid() or r.partner2_id = auth.uid())
    );
$$;

create or replace function public.my_relationship_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select relationship_id from public.profiles where id = auth.uid();
$$;

grant usage on schema public to anon, authenticated;
grant select, update on public.profiles to authenticated;
grant select on public.relationships to authenticated;
grant select, insert, update on public.therapy_sessions to authenticated;
grant select, insert, update on public.messages to authenticated;
grant select on public.ai_memory to authenticated;
grant select on public.ai_archives to authenticated;
grant select, insert, update on public.notifications to authenticated;
grant select, insert, delete on public.device_tokens to authenticated;
grant select, insert, update on public.user_settings to authenticated;
grant execute on function public.pair_with_code(text) to authenticated;
