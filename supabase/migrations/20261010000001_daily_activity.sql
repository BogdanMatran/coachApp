-- Per-day activity for the GitHub-style heatmap: workouts logged, meals completed and
-- supplements taken, plus how many meals/supplements the client's active plans schedule per day.
-- Runs as the caller, so RLS limits it to yourself (client) or your clients (coach).

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
    with days as (
        select generate_series(current_date - (p_days - 1), current_date, interval '1 day')::date as day
    ),
    w as (
        select performed_at::date as day, count(*) as n
        from workout_logs
        where client_id = p_client and performed_at >= current_date - p_days
        group by 1
    ),
    m as (
        select log_date as day, count(*) as n
        from meal_logs
        where client_id = p_client and completed and log_date > current_date - p_days
        group by 1
    ),
    s as (
        select log_date as day, count(*) as n
        from supplement_logs
        where client_id = p_client and taken and log_date > current_date - p_days
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
