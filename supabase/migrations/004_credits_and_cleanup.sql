create table if not exists public.pro_credits (
  user_id uuid primary key references auth.users on delete cascade,
  balance int not null default 5 check (balance >= 0),
  ref_code text unique not null default substr(md5(random()::text || clock_timestamp()::text), 1, 8),
  referred_by uuid,
  updated_at timestamptz not null default now()
);
alter table public.pro_credits enable row level security;

create table if not exists public.redeem_codes (
  code text primary key,
  credits int not null check (credits > 0),
  used_by uuid,
  used_at timestamptz,
  created_at timestamptz not null default now()
);
alter table public.redeem_codes enable row level security;

create or replace function public.my_credits()
returns table(balance int, ref_code text)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.pro_credits(user_id) values (auth.uid()) on conflict do nothing;
  return query select c.balance, c.ref_code from public.pro_credits c where c.user_id = auth.uid();
end $$;

create or replace function public.redeem_code(p_code text)
returns int
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  r public.redeem_codes;
  refu uuid;
  bal int;
  norm text := upper(trim(p_code));
begin
  if uid is null then raise exception 'not signed in'; end if;
  insert into public.pro_credits(user_id) values (uid) on conflict do nothing;
  update public.redeem_codes rc set used_by = uid, used_at = now()
    where rc.code = norm and rc.used_by is null returning * into r;
  if found then
    update public.pro_credits set balance = balance + r.credits, updated_at = now() where user_id = uid returning balance into bal;
    return bal;
  end if;
  select user_id into refu from public.pro_credits where upper(ref_code) = norm and user_id <> uid;
  if refu is not null then
    if exists (select 1 from public.pro_credits where user_id = uid and referred_by is not null) then
      raise exception 'already_referred';
    end if;
    update public.pro_credits set balance = balance + 10, referred_by = refu, updated_at = now() where user_id = uid returning balance into bal;
    update public.pro_credits set balance = balance + 10, updated_at = now() where user_id = refu;
    return bal;
  end if;
  raise exception 'invalid_code';
end $$;

create or replace function public.spend_credit(uid uuid)
returns boolean
language plpgsql security definer set search_path = public as $$
declare ok boolean := false;
begin
  insert into public.pro_credits(user_id) values (uid) on conflict do nothing;
  update public.pro_credits set balance = balance - 1, updated_at = now()
    where user_id = uid and balance > 0 returning true into ok;
  return coalesce(ok, false);
end $$;

create or replace function public.admin_create_codes(n int, credits int)
returns setof text
language plpgsql security definer set search_path = public as $$
declare i int; c text;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  for i in 1..least(n, 200) loop
    c := 'AR-' || upper(substr(md5(random()::text || clock_timestamp()::text || i::text), 1, 8));
    insert into public.redeem_codes(code, credits) values (c, admin_create_codes.credits);
    return next c;
  end loop;
end $$;

revoke all on function public.my_credits() from public, anon;
revoke all on function public.redeem_code(text) from public, anon;
revoke all on function public.spend_credit(uuid) from public, anon, authenticated;
revoke all on function public.admin_create_codes(int, int) from public, anon;
grant execute on function public.my_credits() to authenticated;
grant execute on function public.redeem_code(text) to authenticated;
grant execute on function public.spend_credit(uuid) to service_role;
grant execute on function public.admin_create_codes(int, int) to authenticated;

delete from public.textbook_pages where coalesce(summary, '') = '';
