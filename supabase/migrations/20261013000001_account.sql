-- Account self-service: time zone, data export and deletion.

-- ---------------------------------------------------------------------------
-- Time zone. The app writes the device's IANA zone on sign-in; server-side day
-- boundaries (heatmap, adherence, reminders) use it instead of the server's UTC.
-- ---------------------------------------------------------------------------
alter table public.profiles
    add column timezone text not null default 'UTC'
        -- An unknown zone name makes the expression raise, which rejects the write.
        check (timestamptz '2000-01-01 00:00Z' at time zone timezone is not null);

grant update (timezone) on public.profiles to authenticated;

-- The calendar date it currently is for a user.
create or replace function public.local_today(p_user uuid)
returns date
language sql stable security definer set search_path = public
as $$
    select (now() at time zone coalesce((select timezone from public.profiles where id = p_user), 'UTC'))::date
$$;

create or replace function public.user_timezone(p_user uuid)
returns text
language sql stable security definer set search_path = public
as $$ select coalesce((select timezone from public.profiles where id = p_user), 'UTC') $$;

create or replace function public.daily_activity(p_client uuid, p_days int default 182)
returns table (
    day                 date,
    workouts            int,
    meals               int,
    supplements         int,
    planned_meals       int,
    planned_supplements int
)
language sql stable set search_path = public
as $$
    with ctx as (
        select public.local_today(p_client) as today, public.user_timezone(p_client) as tz
    ),
    days as (
        select generate_series(ctx.today - (p_days - 1), ctx.today, interval '1 day')::date as day from ctx
    ),
    w as (
        select (performed_at at time zone ctx.tz)::date as day, count(*) as n
        from workout_logs, ctx
        where client_id = p_client and performed_at >= (ctx.today - p_days)::timestamp at time zone ctx.tz
        group by 1
    ),
    m as (
        select log_date as day, count(*) as n
        from meal_logs, ctx
        where client_id = p_client and completed and log_date > ctx.today - p_days
        group by 1
    ),
    s as (
        select log_date as day, count(*) as n
        from supplement_logs, ctx
        where client_id = p_client and taken and log_date > ctx.today - p_days
        group by 1
    ),
    planned as (
        select
            (select count(*) from meals ml join meal_plans mp on mp.id = ml.plan_id
             where mp.client_id = p_client and mp.active) as meals,
            (select count(*) from supplement_items si join supplement_plans sp on sp.id = si.plan_id
             where sp.client_id = p_client and sp.active) as supplements
    )
    select d.day,
           coalesce(w.n, 0)::int,
           coalesce(m.n, 0)::int,
           coalesce(s.n, 0)::int,
           planned.meals::int,
           planned.supplements::int
    from days d
    cross join planned
    left join w on w.day = d.day
    left join m on m.day = d.day
    left join s on s.day = d.day
    order by d.day
$$;

-- Same view as before, with the 7-day adherence window in the client's own zone.
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
    ) as adherence_7d
from public.profiles p
where p.role = 'client' and p.coach_id is not null;

-- ---------------------------------------------------------------------------
-- Data export (GDPR art. 20 / nFADP art. 28). Runs as the caller, so RLS limits
-- it to rows the user may read anyway; it collects everything they own.
-- ---------------------------------------------------------------------------
create or replace function public.export_my_data()
returns jsonb
language sql stable security invoker set search_path = public
as $$
    select jsonb_build_object(
        'exported_at', now(),
        'profile', (select to_jsonb(p) from profiles p where p.id = auth.uid()),
        'workout_plans', coalesce((
            select jsonb_agg(to_jsonb(wp) || jsonb_build_object('days', (
                select coalesce(jsonb_agg(to_jsonb(d) || jsonb_build_object('exercises', (
                    select coalesce(jsonb_agg(to_jsonb(e) order by e.position), '[]')
                    from workout_exercises e where e.day_id = d.id)) order by d.day_index), '[]')
                from workout_days d where d.plan_id = wp.id)))
            from workout_plans wp where wp.client_id = auth.uid() or wp.coach_id = auth.uid()), '[]'),
        'meal_plans', coalesce((
            select jsonb_agg(to_jsonb(mp) || jsonb_build_object('meals', (
                select coalesce(jsonb_agg(to_jsonb(m) || jsonb_build_object('items', (
                    select coalesce(jsonb_agg(to_jsonb(i) order by i.position), '[]')
                    from meal_items i where i.meal_id = m.id)) order by m.position), '[]')
                from meals m where m.plan_id = mp.id)))
            from meal_plans mp where mp.client_id = auth.uid() or mp.coach_id = auth.uid()), '[]'),
        'supplement_plans', coalesce((
            select jsonb_agg(to_jsonb(sp) || jsonb_build_object('items', (
                select coalesce(jsonb_agg(to_jsonb(i) order by i.position), '[]')
                from supplement_items i where i.plan_id = sp.id)))
            from supplement_plans sp where sp.client_id = auth.uid() or sp.coach_id = auth.uid()), '[]'),
        'workout_logs', coalesce((
            select jsonb_agg(to_jsonb(l) || jsonb_build_object('sets', (
                select coalesce(jsonb_agg(to_jsonb(s) order by s.set_no), '[]')
                from set_logs s where s.log_id = l.id)) order by l.performed_at)
            from workout_logs l where l.client_id = auth.uid()), '[]'),
        'meal_logs', coalesce((
            select jsonb_agg(to_jsonb(l) order by l.log_date) from meal_logs l where l.client_id = auth.uid()), '[]'),
        'supplement_logs', coalesce((
            select jsonb_agg(to_jsonb(l) order by l.log_date) from supplement_logs l where l.client_id = auth.uid()), '[]'),
        'checkins', coalesce((
            select jsonb_agg(to_jsonb(c) || jsonb_build_object('photos', (
                select coalesce(jsonb_agg(to_jsonb(ph)), '[]') from checkin_photos ph where ph.checkin_id = c.id))
                order by c.week_start)
            from checkins c where c.client_id = auth.uid()), '[]'),
        'checkin_template', (select to_jsonb(t) from checkin_templates t where t.coach_id = auth.uid()),
        'invites', coalesce((
            select jsonb_agg(to_jsonb(i) order by i.created_at) from invites i where i.coach_id = auth.uid()), '[]'),
        'library', jsonb_build_object(
            'exercises', coalesce((select jsonb_agg(to_jsonb(x)) from exercises x where x.coach_id = auth.uid()), '[]'),
            'foods', coalesce((select jsonb_agg(to_jsonb(x)) from foods x where x.coach_id = auth.uid()), '[]'),
            'supplements', coalesce((select jsonb_agg(to_jsonb(x)) from supplements x where x.coach_id = auth.uid()), '[]'))
    )
$$;

grant execute on function public.export_my_data() to authenticated;

-- ---------------------------------------------------------------------------
-- Account deletion (App Store guideline 5.1.1(v), GDPR art. 17). Deleting the
-- auth user cascades through profiles to every owned row; a coach's clients
-- keep their accounts and data, with coach_id set to null. Storage objects
-- don't cascade, so the app removes the user's photos through the Storage API
-- first (direct deletes from storage.objects are blocked).
-- ---------------------------------------------------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql security definer set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'Not signed in';
    end if;
    delete from auth.users where id = auth.uid();
end;
$$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
