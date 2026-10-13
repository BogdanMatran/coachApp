-- Check-in schedule: the coach picks the weekday each client checks in on.
-- Check-ins stay keyed by the week's Monday; the day only decides when one is due.

alter table public.profiles
    add column checkin_day smallint not null default 1 check (checkin_day between 1 and 7); -- ISO: 1 = Monday

-- profiles is only writable for your own row, so the coach goes through this.
create or replace function public.set_checkin_day(p_client uuid, p_day int)
returns void
language plpgsql security definer set search_path = public
as $$
begin
    if not public.is_coach_of(p_client) then
        raise exception 'Not your client';
    end if;
    update public.profiles set checkin_day = p_day where id = p_client;
end;
$$;

revoke execute on function public.set_checkin_day(uuid, int) from public, anon;
grant execute on function public.set_checkin_day(uuid, int) to authenticated;

-- This week's due date for a client, in their own time zone.
create or replace function public.checkin_due_date(p_client uuid)
returns date
language sql stable security definer set search_path = public
as $$
    select date_trunc('week', public.local_today(p_client))::date + (p.checkin_day - 1)
    from public.profiles p where p.id = p_client
$$;

-- True once the due day has passed without a check-in for the week. A client who joined
-- after this week's due day has their first check-in next week, so they aren't overdue yet.
create or replace function public.checkin_overdue(p_client uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select public.local_today(p_client) > public.checkin_due_date(p_client)
       and (select (p.created_at at time zone p.timezone)::date from public.profiles p where p.id = p_client)
           <= public.checkin_due_date(p_client)
       and not exists (
           select 1 from public.checkins c
           where c.client_id = p_client
             and c.week_start = date_trunc('week', public.local_today(p_client))::date)
$$;

-- New columns go last: create or replace view can only append.
create or replace view public.coach_client_overview
with (security_invoker = true)
as
select
    p.id        as client_id,
    p.coach_id,
    p.full_name,
    p.email,
    p.created_at,
    (select max(c.week_start) from public.checkins c where c.client_id = p.id)                        as last_checkin,
    (select count(*) from public.checkins c where c.client_id = p.id and c.status = 'submitted')::int as pending_reviews,
    (select max(w.performed_at) from public.workout_logs w where w.client_id = p.id)                  as last_workout,
    (
        select case when e.n = 0 then null else least(100, round(100.0 * d.n / (e.n * 7)))::int end
        from (
            select (select count(*) from public.meals m join public.meal_plans mp on mp.id = m.plan_id
                    where mp.client_id = p.id and mp.active)
                 + (select count(*) from public.supplement_items si join public.supplement_plans sp on sp.id = si.plan_id
                    where sp.client_id = p.id and sp.active) as n
        ) e,
        (
            select (select count(*) from public.meal_logs ml
                    where ml.client_id = p.id and ml.completed and ml.log_date > public.local_today(p.id) - 7)
                 + (select count(*) from public.supplement_logs sl
                    where sl.client_id = p.id and sl.taken and sl.log_date > public.local_today(p.id) - 7) as n
        ) d
    ) as adherence_7d,
    p.checkin_day,
    public.checkin_overdue(p.id) as checkin_overdue
from public.profiles p
where p.role = 'client' and p.coach_id is not null;
