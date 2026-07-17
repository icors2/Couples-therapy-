-- Live DB was created with read_at instead of is_read; the Android app filters on is_read.
alter table public.notifications
  add column if not exists is_read boolean not null default false;

-- Keep unread/read state consistent if read_at already exists.
do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'notifications'
      and column_name = 'read_at'
  ) then
    update public.notifications
    set is_read = (read_at is not null)
    where is_read = false and read_at is not null;
  end if;
end $$;
