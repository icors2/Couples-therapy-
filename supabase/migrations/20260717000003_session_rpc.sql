-- Session lifecycle RPCs

create or replace function public.start_therapy_session()
returns public.therapy_sessions
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
    rel_id uuid;
    me_profile public.profiles%rowtype;
    partner_id uuid;
    existing public.therapy_sessions%rowtype;
    created public.therapy_sessions%rowtype;
begin
    if me is null then
        raise exception 'Not authenticated';
    end if;

    select * into me_profile from public.profiles where id = me;
    rel_id := me_profile.relationship_id;
    if rel_id is null then
        raise exception 'Pair with a partner before starting therapy';
    end if;

    select * into existing
    from public.therapy_sessions
    where relationship_id = rel_id
      and status in ('pending', 'active')
    order by created_at desc
    limit 1;

    if existing.id is not null then
        return existing;
    end if;

    select case
        when r.partner1_id = me then r.partner2_id
        else r.partner1_id
    end into partner_id
    from public.relationships r
    where r.id = rel_id;

    insert into public.therapy_sessions (relationship_id, started_by, status)
    values (rel_id, me, 'pending')
    returning * into created;

    insert into public.notifications (user_id, type, title, body, payload)
    values (
        partner_id,
        'session_invite',
        'Therapy invite',
        coalesce(me_profile.display_name, 'Your partner') || ' wants to begin a therapy session.',
        jsonb_build_object('session_id', created.id, 'relationship_id', rel_id)
    );

    insert into public.messages (session_id, sender_role, content)
    values (created.id, 'system', 'Session invite sent. Waiting for partner to join.');

    return created;
end;
$$;

create or replace function public.respond_to_session(p_session_id uuid, accept boolean)
returns public.therapy_sessions
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
    sess public.therapy_sessions%rowtype;
    starter public.profiles%rowtype;
begin
    select * into sess from public.therapy_sessions where id = p_session_id;
    if sess.id is null then
        raise exception 'Session not found';
    end if;
    if not public.is_relationship_member(sess.relationship_id) then
        raise exception 'Not a member of this relationship';
    end if;
    if sess.started_by = me then
        raise exception 'You started this session';
    end if;
    if sess.status <> 'pending' then
        raise exception 'Session is not pending';
    end if;

    if accept then
        update public.therapy_sessions
        set status = 'active',
            partner_joined_at = now(),
            last_activity_at = now()
        where id = p_session_id
        returning * into sess;

        select * into starter from public.profiles where id = sess.started_by;

        insert into public.notifications (user_id, type, title, body, payload)
        values (
            sess.started_by,
            'partner_joined',
            'Partner joined',
            'Your partner joined the therapy session.',
            jsonb_build_object('session_id', sess.id)
        );

        insert into public.messages (session_id, sender_role, content)
        values (sess.id, 'system', 'Both partners are here. The AI therapist will join when helpful.');
    else
        update public.therapy_sessions
        set status = 'declined',
            ended_at = now(),
            ended_by = me,
            duration_seconds = 0
        where id = p_session_id
        returning * into sess;

        insert into public.notifications (user_id, type, title, body, payload)
        values (
            sess.started_by,
            'session_ended',
            'Invite declined',
            'Your partner declined the therapy session.',
            jsonb_build_object('session_id', sess.id)
        );
    end if;

    return sess;
end;
$$;

create or replace function public.end_therapy_session(p_session_id uuid, reason text default 'manual')
returns public.therapy_sessions
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
    sess public.therapy_sessions%rowtype;
    partner_id uuid;
begin
    select * into sess from public.therapy_sessions where id = p_session_id;
    if sess.id is null then
        raise exception 'Session not found';
    end if;
    if not public.is_relationship_member(sess.relationship_id) then
        raise exception 'Not a member of this relationship';
    end if;
    if sess.status not in ('pending', 'active') then
        return sess;
    end if;

    update public.therapy_sessions
    set status = case when reason = 'timeout' then 'expired'::public.session_status else 'ended'::public.session_status end,
        ended_at = now(),
        ended_by = me,
        duration_seconds = greatest(0, extract(epoch from (now() - started_at))::int)
    where id = p_session_id
    returning * into sess;

    select case
        when r.partner1_id = me then r.partner2_id
        else r.partner1_id
    end into partner_id
    from public.relationships r
    where r.id = sess.relationship_id;

    insert into public.notifications (user_id, type, title, body, payload)
    values (
        partner_id,
        case when reason = 'timeout' then 'session_timeout'::public.notification_type else 'session_ended'::public.notification_type end,
        case when reason = 'timeout' then 'Session timed out' else 'Session ended' end,
        case when reason = 'timeout'
            then 'The therapy session ended after 10 minutes of inactivity.'
            else 'Your partner ended the therapy session.'
        end,
        jsonb_build_object('session_id', sess.id, 'reason', reason)
    );

    insert into public.messages (session_id, sender_role, content)
    values (
        sess.id,
        'system',
        case when reason = 'timeout'
            then 'Session ended due to inactivity.'
            else 'Session ended. Generating therapeutic handoff…'
        end
    );

    return sess;
end;
$$;

create or replace function public.touch_session_activity(p_session_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.therapy_sessions
    set last_activity_at = now()
    where id = p_session_id
      and status = 'active'
      and public.is_relationship_member(relationship_id);
end;
$$;

grant execute on function public.start_therapy_session() to authenticated;
grant execute on function public.respond_to_session(uuid, boolean) to authenticated;
grant execute on function public.end_therapy_session(uuid, text) to authenticated;
grant execute on function public.touch_session_activity(uuid) to authenticated;
