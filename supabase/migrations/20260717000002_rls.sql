-- Row Level Security policies

alter table public.profiles enable row level security;
alter table public.relationships enable row level security;
alter table public.therapy_sessions enable row level security;
alter table public.messages enable row level security;
alter table public.ai_memory enable row level security;
alter table public.ai_archives enable row level security;
alter table public.notifications enable row level security;
alter table public.device_tokens enable row level security;
alter table public.user_settings enable row level security;

-- Profiles
create policy "Users can view own profile"
    on public.profiles for select
    using (id = auth.uid());

create policy "Users can view partner profile"
    on public.profiles for select
    using (
        relationship_id is not null
        and relationship_id = public.my_relationship_id()
    );

create policy "Users can update own profile"
    on public.profiles for update
    using (id = auth.uid())
    with check (id = auth.uid());

-- Relationships
create policy "Members can view their relationship"
    on public.relationships for select
    using (partner1_id = auth.uid() or partner2_id = auth.uid());

-- Sessions
create policy "Members can view sessions"
    on public.therapy_sessions for select
    using (public.is_relationship_member(relationship_id));

create policy "Members can create sessions"
    on public.therapy_sessions for insert
    with check (
        public.is_relationship_member(relationship_id)
        and started_by = auth.uid()
    );

create policy "Members can update sessions"
    on public.therapy_sessions for update
    using (public.is_relationship_member(relationship_id));

-- Messages
create policy "Members can view messages"
    on public.messages for select
    using (
        exists (
            select 1 from public.therapy_sessions s
            where s.id = session_id
              and public.is_relationship_member(s.relationship_id)
        )
    );

create policy "Members can insert messages"
    on public.messages for insert
    with check (
        exists (
            select 1 from public.therapy_sessions s
            where s.id = session_id
              and public.is_relationship_member(s.relationship_id)
              and s.status = 'active'
        )
        and (
            sender_role = 'ai'
            or sender_role = 'system'
            or sender_id = auth.uid()
        )
    );

create policy "Members can update message receipts"
    on public.messages for update
    using (
        exists (
            select 1 from public.therapy_sessions s
            where s.id = session_id
              and public.is_relationship_member(s.relationship_id)
        )
    );

-- Memory / archives (read for members; writes via service role / edge functions)
create policy "Members can view memory"
    on public.ai_memory for select
    using (public.is_relationship_member(relationship_id));

create policy "Members can view archives"
    on public.ai_archives for select
    using (public.is_relationship_member(relationship_id));

-- Allow authenticated inserts/updates for memory from the app when using
-- the user's JWT (Edge Functions with service role are preferred in prod).
create policy "Members can insert memory versions"
    on public.ai_memory for insert
    with check (public.is_relationship_member(relationship_id));

create policy "Members can update current memory flag"
    on public.ai_memory for update
    using (public.is_relationship_member(relationship_id));

create policy "Members can insert archives"
    on public.ai_archives for insert
    with check (public.is_relationship_member(relationship_id));

-- Notifications
create policy "Users manage own notifications"
    on public.notifications for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- Device tokens
create policy "Users manage own device tokens"
    on public.device_tokens for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- Settings
create policy "Users manage own settings"
    on public.user_settings for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- Realtime publication
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.therapy_sessions;
alter publication supabase_realtime add table public.notifications;
