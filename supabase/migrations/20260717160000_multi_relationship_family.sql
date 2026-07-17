-- Multi-relationship (couples + parent_child), age gate fields, parental consent, membership RLS.

-- ---------------------------------------------------------------------------
-- Enums / relationship columns
-- ---------------------------------------------------------------------------
do $$ begin
  create type public.relationship_type as enum ('couples', 'parent_child');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.member_role as enum ('partner', 'parent', 'child');
exception when duplicate_object then null;
end $$;

alter table public.relationships
  add column if not exists relationship_type public.relationship_type not null default 'couples';

alter table public.relationships
  add column if not exists partner1_role public.member_role not null default 'partner';

alter table public.relationships
  add column if not exists partner2_role public.member_role not null default 'partner';

-- Unique unordered pair (both members set)
create unique index if not exists relationships_unique_pair_uidx
  on public.relationships (
    least(partner1_id, partner2_id),
    greatest(partner1_id, partner2_id)
  )
  where partner2_id is not null;

-- ---------------------------------------------------------------------------
-- Profiles: active selection + age attestation
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column if not exists active_relationship_id uuid references public.relationships (id) on delete set null;

alter table public.profiles
  add column if not exists date_of_birth date;

alter table public.profiles
  add column if not exists age_attested_at timestamptz;

alter table public.profiles
  add column if not exists is_minor boolean not null default false;

-- Backfill active from legacy singleton slot
update public.profiles
set active_relationship_id = relationship_id
where relationship_id is not null
  and active_relationship_id is null;

-- Existing relationships are couples
update public.relationships
set relationship_type = 'couples',
    partner1_role = 'partner',
    partner2_role = 'partner'
where relationship_type is null
   or partner1_role is null;

-- Clear singleton membership (membership = partner1/partner2)
update public.profiles set relationship_id = null where relationship_id is not null;

-- ---------------------------------------------------------------------------
-- Parental consents
-- ---------------------------------------------------------------------------
create table if not exists public.parental_consents (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null unique references public.relationships (id) on delete cascade,
  guardian_id uuid not null references public.profiles (id) on delete cascade,
  minor_id uuid not null references public.profiles (id) on delete cascade,
  consented_at timestamptz not null default timezone('utc', now()),
  consent_version text not null default 'v1'
);

create index if not exists parental_consents_minor_id_idx on public.parental_consents (minor_id);

alter type public.notification_type add value if not exists 'parental_consent_granted';

-- ---------------------------------------------------------------------------
-- Membership helpers
-- ---------------------------------------------------------------------------
create or replace function public.is_relationship_member(rel_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.relationships r
    where r.id = rel_id
      and (r.partner1_id = auth.uid() or r.partner2_id = auth.uid())
  );
$$;

create or replace function public.shares_relationship_with(other_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.relationships r
    where (r.partner1_id = auth.uid() and r.partner2_id = other_user_id)
       or (r.partner2_id = auth.uid() and r.partner1_id = other_user_id)
  );
$$;

-- Keep helper for any leftover callers; prefer membership functions.
create or replace function public.current_user_relationship_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select active_relationship_id from public.profiles where id = auth.uid();
$$;

create or replace function public.relationship_needs_parental_consent(rel_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.relationships r
    join public.profiles p1 on p1.id = r.partner1_id
    left join public.profiles p2 on p2.id = r.partner2_id
    where r.id = rel_id
      and r.relationship_type = 'parent_child'
      and (p1.is_minor or coalesce(p2.is_minor, false))
      and not exists (
        select 1 from public.parental_consents c where c.relationship_id = r.id
      )
  );
$$;

-- ---------------------------------------------------------------------------
-- RLS: rewrite membership-based policies
-- ---------------------------------------------------------------------------
drop policy if exists "profiles_select_own_or_partner" on public.profiles;
create policy "profiles_select_own_or_partner"
on public.profiles for select
using (
  id = auth.uid()
  or public.shares_relationship_with(id)
);

drop policy if exists "sessions_select_member" on public.sessions;
create policy "sessions_select_member"
on public.sessions for select
using (public.is_relationship_member(relationship_id));

drop policy if exists "sessions_insert_member" on public.sessions;
create policy "sessions_insert_member"
on public.sessions for insert
with check (
  public.is_relationship_member(relationship_id)
  and started_by = auth.uid()
);

drop policy if exists "sessions_update_member" on public.sessions;
create policy "sessions_update_member"
on public.sessions for update
using (public.is_relationship_member(relationship_id));

drop policy if exists "messages_select_member" on public.messages;
create policy "messages_select_member"
on public.messages for select
using (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and public.is_relationship_member(s.relationship_id)
  )
);

drop policy if exists "messages_insert_member" on public.messages;
create policy "messages_insert_member"
on public.messages for insert
with check (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and public.is_relationship_member(s.relationship_id)
      and s.status in ('pending', 'active')
  )
  and (
    sender_user_id = auth.uid()
    or sender = 'system'
  )
);

drop policy if exists "messages_update_member" on public.messages;
create policy "messages_update_member"
on public.messages for update
using (
  exists (
    select 1 from public.sessions s
    where s.id = session_id
      and public.is_relationship_member(s.relationship_id)
  )
);

drop policy if exists "ai_memory_select_member" on public.ai_memory;
create policy "ai_memory_select_member"
on public.ai_memory for select
using (public.is_relationship_member(relationship_id));

drop policy if exists "ai_archives_select_member" on public.ai_archives;
create policy "ai_archives_select_member"
on public.ai_archives for select
using (public.is_relationship_member(relationship_id));

alter table public.parental_consents enable row level security;

drop policy if exists "parental_consents_select_member" on public.parental_consents;
create policy "parental_consents_select_member"
on public.parental_consents for select
using (
  guardian_id = auth.uid()
  or minor_id = auth.uid()
  or public.is_relationship_member(relationship_id)
);
