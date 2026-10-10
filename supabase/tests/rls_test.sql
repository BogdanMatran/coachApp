-- RLS checks against the seed data. Run with: supabase test db
begin;
select plan(16);

-- Helper: act as a given user for the rest of the transaction
create or replace function pg_temp.act_as(p_user uuid) returns void language sql as $$
    select set_config('request.jwt.claims', jsonb_build_object('sub', p_user, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;

-- Anna (client) --------------------------------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000002');

select is((select count(*)::int from workout_plans), 1, 'client sees only their own workout plan');
select is((select count(*)::int from workout_plans where client_id is null), 0, 'client cannot see coach templates');
select is((select count(*)::int from profiles), 2, 'client sees self and coach');
select is((select count(*)::int from checkins), 2, 'client sees own check-ins');
select throws_ok(
    $$ insert into workout_plans (coach_id, client_id, name) values ('00000000-0000-4000-a000-000000000001', '00000000-0000-4000-a000-000000000002', 'hack') $$,
    '42501', null, 'client cannot write plans');
select throws_ok(
    $$ update profiles set coach_id = '00000000-0000-4000-a000-000000000004' where id = '00000000-0000-4000-a000-000000000002' $$,
    '42501', null, 'client cannot change their coach');
select lives_ok(
    $$ insert into meal_logs (client_id, meal_id) select '00000000-0000-4000-a000-000000000002', id from meals limit 1 $$,
    'client can log a meal');

-- Client cannot review their own check-in (trigger keeps status)
update checkins set status = 'reviewed', coach_feedback = 'self review'
where week_start = date_trunc('week', current_date)::date;
select is((select status::text from checkins where week_start = date_trunc('week', current_date)::date), 'submitted',
          'client cannot mark own check-in reviewed');

-- Ben (client) cannot see Anna's data -----------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000003');
select is((select count(*)::int from workout_plans), 0, 'other client sees no plans');
select is((select count(*)::int from checkins), 0, 'other client sees no check-ins');
select is((select count(*)::int from meal_logs), 0, 'other client sees no meal logs');

-- Demo coach --------------------------------------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000001');
select is((select count(*)::int from coach_client_overview), 2, 'coach sees both clients in overview');
select is((select count(*)::int from checkins where status = 'submitted'), 1, 'coach sees pending check-in');
update checkins set status = 'reviewed', coach_feedback = 'Nice', weight = 1
where week_start = date_trunc('week', current_date)::date;
select is((select weight from checkins where week_start = date_trunc('week', current_date)::date), 64.3::numeric(5,2),
          'coach cannot overwrite client metrics');

-- Other coach sees nothing of the demo coach --------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000004');
select is((select count(*)::int from coach_client_overview), 0, 'other coach sees no clients');
select throws_ok(
    $$ select public.assign_plan('workout', (select id from workout_plans limit 1), '00000000-0000-4000-a000-000000000002') $$,
    'P0001', 'Not your client', 'other coach cannot assign to foreign client');

select * from finish();
rollback;
