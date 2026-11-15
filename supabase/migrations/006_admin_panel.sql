-- Stop users promoting themselves: only definer functions / service role may change is_admin.
create or replace function public.guard_admin_flag()
returns trigger language plpgsql as $$
begin
  if new.is_admin is distinct from old.is_admin and current_user in ('authenticated', 'anon') then
    new.is_admin := old.is_admin;
  end if;
  return new;
end $$;
drop trigger if exists guard_admin_flag on public.profiles;
create trigger guard_admin_flag before update on public.profiles
  for each row execute function public.guard_admin_flag();

create or replace function public.admin_overview()
returns jsonb language plpgsql security definer set search_path = public as $$
declare r jsonb;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  select jsonb_build_object(
    'users', (select count(*) from profiles),
    'new_today', (select count(*) from profiles where created_at >= date_trunc('day', now())),
    'new_week', (select count(*) from profiles where created_at >= now() - interval '7 days'),
    'active_today', (select count(*) from profiles where last_study_date = current_date),
    'focus_minutes_week', (select coalesce(sum(focus_minutes_week), 0) from profiles),
    'pages', (select count(*) from textbook_pages),
    'tutor_today', (select coalesce(sum(calls), 0) from ai_usage where day = current_date and kind = 'tutor'),
    'analyze_today', (select coalesce(sum(calls), 0) from ai_usage where day = current_date and kind = 'analyze'),
    'codes_total', (select count(*) from redeem_codes),
    'codes_unused', (select count(*) from redeem_codes where used_by is null),
    'credits_held', (select coalesce(sum(balance), 0) from pro_credits),
    'credits_sold', (select coalesce(sum(credits), 0) from redeem_codes where used_by is not null)
  ) into r;
  return r;
end $$;

create or replace function public.admin_users(p_search text default '')
returns table(id uuid, name text, email text, class_level text, school_name text, streak_days int,
              focus_minutes_week int, balance int, is_admin boolean, created_at timestamptz)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select p.id, p.name, u.email::text, p.class_level, p.school_name, p.streak_days,
           p.focus_minutes_week, coalesce(c.balance, 0), p.is_admin, p.created_at
    from profiles p
    join auth.users u on u.id = p.id
    left join pro_credits c on c.user_id = p.id
    where coalesce(trim(p_search), '') = ''
       or p.name ilike '%' || trim(p_search) || '%'
       or u.email ilike '%' || trim(p_search) || '%'
       or p.school_name ilike '%' || trim(p_search) || '%'
    order by p.created_at desc
    limit 100;
end $$;

create or replace function public.admin_grant_credits(p_user uuid, p_amount int)
returns int language plpgsql security definer set search_path = public as $$
declare bal int;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  insert into pro_credits(user_id) values (p_user) on conflict do nothing;
  update pro_credits set balance = greatest(0, balance + p_amount), updated_at = now()
    where user_id = p_user returning balance into bal;
  return bal;
end $$;

create or replace function public.admin_set_admin(p_user uuid, p_flag boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if p_user = auth.uid() and not p_flag then raise exception 'cannot remove yourself'; end if;
  update profiles set is_admin = p_flag where id = p_user;
end $$;

create or replace function public.admin_codes_list()
returns table(code text, credits int, used boolean, used_by_name text, used_at timestamptz, created_at timestamptz)
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
    select r.code, r.credits, r.used_by is not null, p.name, r.used_at, r.created_at
    from redeem_codes r left join profiles p on p.id = r.used_by
    order by r.created_at desc limit 100;
end $$;

create or replace function public.admin_delete_code(p_code text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  delete from redeem_codes where redeem_codes.code = p_code and used_by is null;
end $$;

create or replace function public.admin_set_config(p_key text, p_value text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if p_key not in ('buy_note', 'buy_url', 'packs') then raise exception 'bad key'; end if;
  insert into app_config(key, value) values (p_key, p_value)
    on conflict (key) do update set value = excluded.value;
end $$;

revoke all on function public.admin_overview(), public.admin_users(text), public.admin_grant_credits(uuid, int),
  public.admin_set_admin(uuid, boolean), public.admin_codes_list(), public.admin_delete_code(text),
  public.admin_set_config(text, text) from public, anon;
grant execute on function public.admin_overview(), public.admin_users(text), public.admin_grant_credits(uuid, int),
  public.admin_set_admin(uuid, boolean), public.admin_codes_list(), public.admin_delete_code(text),
  public.admin_set_config(text, text) to authenticated;
