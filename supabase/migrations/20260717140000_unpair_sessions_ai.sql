-- Unpair notification type, session working summary, memory idempotency,
-- and expire both idle active (10m) and stale pending (30m) sessions.

alter type public.notification_type add value if not exists 'partner_unpaired';

alter table public.sessions
  add column if not exists working_summary text;

alter table public.ai_memory
  add column if not exists source_session_id uuid references public.sessions (id) on delete set null;

create unique index if not exists ai_memory_source_session_id_uidx
  on public.ai_memory (source_session_id)
  where source_session_id is not null;

-- Drop 1-arg overload from earlier migrations so PostgREST can resolve the call.
drop function if exists public.expire_inactive_sessions(int);

create or replace function public.expire_inactive_sessions(
  timeout_minutes int default 10,
  pending_timeout_minutes int default 30
)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  expired_ids uuid[] := '{}';
  cancelled_count int := 0;
  expired_count int := 0;
begin
  select coalesce(array_agg(id), '{}')
  into expired_ids
  from public.sessions
  where status = 'active'
    and coalesce(last_user_message_at, started_at)
        < timezone('utc', now()) - make_interval(mins => timeout_minutes);

  update public.sessions
    set status = 'expired',
        ended_at = timezone('utc', now()),
        duration_seconds = extract(epoch from (timezone('utc', now()) - started_at))::int
  where id = any (expired_ids);

  expired_count := coalesce(cardinality(expired_ids), 0);

  insert into public.notifications (user_id, type, title, body, payload)
  select r.partner1_id, 'session_timeout'::public.notification_type, 'Session ended',
         'Your therapy session ended after 10 minutes of inactivity.',
         jsonb_build_object('session_id', s.id)
  from public.sessions s
  join public.relationships r on r.id = s.relationship_id
  where s.id = any (expired_ids)
  union all
  select r.partner2_id, 'session_timeout'::public.notification_type, 'Session ended',
         'Your therapy session ended after 10 minutes of inactivity.',
         jsonb_build_object('session_id', s.id)
  from public.sessions s
  join public.relationships r on r.id = s.relationship_id
  where s.id = any (expired_ids)
    and r.partner2_id is not null;

  with cancelled as (
    update public.sessions
      set status = 'declined',
          ended_at = timezone('utc', now()),
          duration_seconds = extract(epoch from (timezone('utc', now()) - started_at))::int
      where status = 'pending'
        and started_at < timezone('utc', now()) - make_interval(mins => pending_timeout_minutes)
      returning id
  )
  select count(*)::int into cancelled_count from cancelled;

  return expired_count + cancelled_count;
end;
$$;
