-- Strength progress: the best estimated one-rep max (Epley: weight × (1 + reps / 30)) per
-- exercise per day, for the client's most-trained exercises. Days are in the client's zone.
-- Runs as the caller, so RLS limits it to yourself (client) or your clients (coach).

create or replace function public.strength_progress(p_client uuid, p_days int default 180, p_exercises int default 6)
returns table (
    exercise_id   uuid,
    exercise_name text,
    day           date,
    e1rm          numeric,
    weight        numeric,
    reps          int
)
language sql stable set search_path = public
as $$
    with tz as (select public.user_timezone(p_client) as name),
    sets as (
        select s.exercise_id, (l.performed_at at time zone tz.name)::date as day, s.weight, s.reps,
               round(s.weight * (1 + s.reps / 30.0), 1) as e1rm
        from set_logs s
        join workout_logs l on l.id = s.log_id
        cross join tz
        where l.client_id = p_client
          and l.performed_at >= now() - make_interval(days => p_days)
          and s.weight > 0 and s.reps between 1 and 30
    ),
    top as (
        select exercise_id from sets group by exercise_id
        order by count(distinct day) desc, max(e1rm) desc
        limit p_exercises
    ),
    best as (
        select distinct on (s.exercise_id, s.day) s.*
        from sets s join top using (exercise_id)
        order by s.exercise_id, s.day, s.e1rm desc
    )
    select b.exercise_id, e.name, b.day, b.e1rm, b.weight, b.reps
    from best b join exercises e on e.id = b.exercise_id
    order by e.name, b.day
$$;

grant execute on function public.strength_progress(uuid, int, int) to authenticated;
