create table if not exists public.app_config (key text primary key, value text not null);
alter table public.app_config enable row level security;
drop policy if exists "read config" on public.app_config;
create policy "read config" on public.app_config for select to authenticated using (true);
insert into public.app_config(key, value) values
  ('buy_note', 'To buy credits, message Ar Studio with the pack you want and pay by eSewa or Khalti. You will get a code to enter on this screen.'),
  ('buy_url', ''),
  ('packs', '[{"name":"Starter","credits":25,"price":"NPR 40"},{"name":"Student","credits":80,"price":"NPR 99","note":"Most popular"},{"name":"Exam season","credits":250,"price":"NPR 249","note":"Best value"}]')
on conflict (key) do nothing;

alter table public.pro_credits add column if not exists last_daily date;

create or replace function public.claim_daily_credit()
returns int
language plpgsql security definer set search_path = public as $$
declare bal int;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.pro_credits(user_id) values (auth.uid()) on conflict do nothing;
  update public.pro_credits
    set balance = balance + 1, last_daily = current_date, updated_at = now()
    where user_id = auth.uid() and (last_daily is null or last_daily < current_date) and balance < 30
    returning balance into bal;
  if bal is null then
    if exists (select 1 from public.pro_credits where user_id = auth.uid() and balance >= 30) then
      raise exception 'balance_full';
    end if;
    raise exception 'already_claimed';
  end if;
  return bal;
end $$;

revoke all on function public.claim_daily_credit() from public, anon;
grant execute on function public.claim_daily_credit() to authenticated;

update public.profiles set is_admin = true where id in (select id from auth.users where email = 'test@gmail.com');
