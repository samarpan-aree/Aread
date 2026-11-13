drop policy if exists "admin read all" on public.profiles;

create or replace function public.is_admin()
returns boolean language sql security definer stable set search_path = public as $$
  select coalesce((select is_admin from public.profiles where id = auth.uid()), false)
$$;
grant execute on function public.is_admin() to authenticated;

create policy "admin read all" on public.profiles for select using (public.is_admin());

create or replace function public.save_profile(
  p_name text, p_phone text, p_class text, p_roll text, p_grade text,
  p_school text, p_type text, p_goals text[]
) returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.profiles (id, name, phone, class_level, roll_no, avg_grade, school_name, school_type, goals)
  values (
    auth.uid(), p_name, p_phone, p_class, p_roll, p_grade, p_school,
    case when p_type in ('government', 'private') then p_type else 'government' end,
    coalesce(p_goals, '{}')
  )
  on conflict (id) do update set
    name = excluded.name, phone = excluded.phone, class_level = excluded.class_level,
    roll_no = excluded.roll_no, avg_grade = excluded.avg_grade, school_name = excluded.school_name,
    school_type = excluded.school_type, goals = excluded.goals;
end $$;
grant execute on function public.save_profile(text, text, text, text, text, text, text, text[]) to authenticated;
