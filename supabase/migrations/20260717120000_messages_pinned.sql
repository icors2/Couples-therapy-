-- Explicit pin: partners can mark messages that must be retained as AI key facts.
alter table public.messages
  add column if not exists pinned boolean not null default false;

create index if not exists messages_pinned_idx
  on public.messages (pinned)
  where pinned = true;
