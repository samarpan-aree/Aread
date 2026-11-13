create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default '',
  phone text not null default '',
  class_level text not null default '',
  roll_no text not null default '',
  avg_grade text not null default '',
  school_name text not null default '',
  school_type text not null default 'government' check (school_type in ('government','private')),
  goals text[] not null default '{}',
  is_admin boolean not null default false,
  streak_days int not null default 0,
  best_streak int not null default 0,
  focus_minutes_week int not null default 0,
  last_study_date date,
  week_start date not null default date_trunc('week', now())::date,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "own profile read" on public.profiles for select using (auth.uid() = id);
create policy "own profile insert" on public.profiles for insert with check (auth.uid() = id and is_admin = false);
create policy "own profile update" on public.profiles for update using (auth.uid() = id);
create policy "admin read all" on public.profiles for select
  using (exists (select 1 from public.profiles p where p.id = auth.uid() and p.is_admin));

revoke update on public.profiles from authenticated;
grant update (name, phone, class_level, roll_no, avg_grade, school_name, school_type, goals) on public.profiles to authenticated;

create or replace function public.log_focus_session(minutes int)
returns void language plpgsql security definer set search_path = public as $$
declare
  p public.profiles;
  today date := (now() at time zone 'Asia/Kathmandu')::date;
  this_week date := date_trunc('week', now() at time zone 'Asia/Kathmandu')::date;
begin
  if minutes < 1 or minutes > 240 then raise exception 'invalid minutes'; end if;
  select * into p from public.profiles where id = auth.uid() for update;
  if not found then raise exception 'no profile'; end if;

  if p.week_start < this_week then
    p.focus_minutes_week := 0;
    p.week_start := this_week;
  end if;
  p.focus_minutes_week := p.focus_minutes_week + minutes;

  if p.last_study_date is null or p.last_study_date < today - 1 then
    p.streak_days := 1;
  elsif p.last_study_date = today - 1 then
    p.streak_days := p.streak_days + 1;
  end if;
  p.last_study_date := today;
  p.best_streak := greatest(p.best_streak, p.streak_days);

  update public.profiles set
    focus_minutes_week = p.focus_minutes_week, week_start = p.week_start,
    streak_days = p.streak_days, best_streak = p.best_streak, last_study_date = p.last_study_date
  where id = auth.uid();
end $$;

create or replace view public.leaderboard_weekly with (security_invoker = false) as
  select name, school_name, focus_minutes_week, streak_days
  from public.profiles
  where name <> '' and week_start = date_trunc('week', now() at time zone 'Asia/Kathmandu')::date;
grant select on public.leaderboard_weekly to authenticated;

create table public.textbook_pages (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject text not null default 'General',
  path text not null,
  created_at timestamptz not null default now()
);
alter table public.textbook_pages enable row level security;
create policy "own pages" on public.textbook_pages for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

insert into storage.buckets (id, name, public) values ('textbooks', 'textbooks', false) on conflict do nothing;
create policy "own textbook files read" on storage.objects for select
  using (bucket_id = 'textbooks' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "own textbook files write" on storage.objects for insert
  with check (bucket_id = 'textbooks' and (storage.foldername(name))[1] = auth.uid()::text);

create table public.tutor_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null default current_date,
  calls int not null default 0,
  primary key (user_id, day)
);
alter table public.tutor_usage enable row level security;

create or replace function public.bump_tutor_usage(uid uuid)
returns int language sql security definer set search_path = public as $$
  insert into public.tutor_usage (user_id, day, calls) values (uid, current_date, 1)
  on conflict (user_id, day) do update set calls = tutor_usage.calls + 1
  returning calls;
$$;
revoke all on function public.bump_tutor_usage(uuid) from public, anon, authenticated;

create or replace function public.admin_stats()
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from public.profiles where id = auth.uid() and is_admin) then
    raise exception 'not authorised';
  end if;
  return jsonb_build_object(
    'total_students', (select count(*) from public.profiles),
    'active_this_week', (select count(*) from public.profiles where focus_minutes_week > 0),
    'tutor_questions_today', (select coalesce(sum(calls),0) from public.tutor_usage where day = current_date),
    'textbook_pages', (select count(*) from public.textbook_pages)
  );
end $$;
