-- Private per-person intake forms + status helper for first-session gating.

alter type public.notification_type add value if not exists 'intake_completed';

create table if not exists public.relationship_intakes (
  id uuid primary key default gen_random_uuid(),
  relationship_id uuid not null references public.relationships (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  answers jsonb not null default '{}'::jsonb,
  completed_at timestamptz not null default timezone('utc', now()),
  unique (relationship_id, user_id)
);

create index if not exists relationship_intakes_relationship_id_idx
  on public.relationship_intakes (relationship_id);

alter table public.relationship_intakes enable row level security;

-- Own row only: partner must never read another person's raw answers.
drop policy if exists "relationship_intakes_select_own" on public.relationship_intakes;
create policy "relationship_intakes_select_own"
on public.relationship_intakes for select
using (user_id = auth.uid());

drop policy if exists "relationship_intakes_insert_own" on public.relationship_intakes;
create policy "relationship_intakes_insert_own"
on public.relationship_intakes for insert
with check (
  user_id = auth.uid()
  and public.is_relationship_member(relationship_id)
);

drop policy if exists "relationship_intakes_update_own" on public.relationship_intakes;
create policy "relationship_intakes_update_own"
on public.relationship_intakes for update
using (user_id = auth.uid())
with check (user_id = auth.uid());

-- Status without leaking partner answers (for Home gating).
create or replace function public.get_intake_status(rel_id uuid)
returns json
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  member boolean;
  ended_count int;
  me_done boolean;
  partner_done boolean;
begin
  if me is null then
    return null;
  end if;

  select exists (
    select 1 from public.relationships r
    where r.id = rel_id
      and (r.partner1_id = me or r.partner2_id = me)
  ) into member;

  if not member then
    return null;
  end if;

  select count(*)::int into ended_count
  from public.sessions s
  where s.relationship_id = rel_id
    and s.status = 'ended';

  select exists (
    select 1 from public.relationship_intakes i
    where i.relationship_id = rel_id and i.user_id = me
  ) into me_done;

  select exists (
    select 1 from public.relationship_intakes i
    where i.relationship_id = rel_id and i.user_id <> me
  ) into partner_done;

  return json_build_object(
    'me_done', me_done,
    'partner_done', partner_done,
    'required', ended_count = 0
  );
end;
$$;

grant execute on function public.get_intake_status(uuid) to authenticated;
grant execute on function public.get_intake_status(uuid) to service_role;
