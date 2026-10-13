-- RLS checks against the seed data. Run with: supabase test db
begin;
select plan(30);

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
select is((select count(*)::int from checkins), 10, 'client sees own check-ins');
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

-- Coach exercise videos ----------------------------------------------------------
insert into exercise_videos (coach_id, exercise_id, external_url)
select '00000000-0000-4000-a000-000000000001', id, 'https://example.com/squat.mp4' from exercises where name = 'Back Squat' limit 1;
select pg_temp.act_as('00000000-0000-4000-a000-000000000002');
select is((select count(*)::int from exercise_videos), 1, 'client sees their coach''s video');
select pg_temp.act_as('00000000-0000-4000-a000-000000000004');
select is((select count(*)::int from exercise_videos), 0, 'another coach''s client videos stay private');
select throws_ok(
    $$ insert into exercise_videos (coach_id, exercise_id, external_url)
       select '00000000-0000-4000-a000-000000000001', id, 'https://x' from exercises limit 1 $$,
    '42501', null, 'coach cannot add videos as another coach');
select pg_temp.act_as('00000000-0000-4000-a000-000000000001');

-- Other coach sees nothing of the demo coach --------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000004');
select is((select count(*)::int from coach_client_overview), 0, 'other coach sees no clients');
select throws_ok(
    $$ select public.assign_plan('workout', (select id from workout_plans limit 1), '00000000-0000-4000-a000-000000000002') $$,
    'P0001', 'Not your client', 'other coach cannot assign to foreign client');

-- Account self-service -----------------------------------------------------------
select pg_temp.act_as('00000000-0000-4000-a000-000000000002');
select is((select jsonb_array_length(public.export_my_data() -> 'checkins')), 10, 'export contains own check-ins');
select is((select public.export_my_data() -> 'profile' ->> 'id'), '00000000-0000-4000-a000-000000000002', 'export is of the caller');
select throws_ok(
    $$ update profiles set timezone = 'Mars/Olympus' where id = '00000000-0000-4000-a000-000000000002' $$,
    '22023', null, 'unknown time zone is rejected');
update profiles set timezone = 'Pacific/Kiritimati' where id = '00000000-0000-4000-a000-000000000002';
select is((select max(day) from public.daily_activity('00000000-0000-4000-a000-000000000002', 3)),
          (now() at time zone 'Pacific/Kiritimati')::date, 'activity days follow the client time zone');

-- Onboarding -----------------------------------------------------------------------
select is(public.intake_pending(), false, 'client who answered the intake form is not gated');
select is((select public.export_my_data() -> 'intake_response' ->> 'client_id'), '00000000-0000-4000-a000-000000000002',
          'export includes intake answers');

select pg_temp.act_as('00000000-0000-4000-a000-000000000003');
select is(public.intake_pending(), true, 'new client must answer the intake form');
select is((select count(*)::int from intake_responses), 0, 'client cannot read other intake answers');
insert into intake_responses (client_id, coach_id, answers)
values ('00000000-0000-4000-a000-000000000003', '00000000-0000-4000-a000-000000000001', '{"goal": "Run 10k"}');
select is(public.intake_pending(), false, 'answering the intake form clears the gate');

select lives_ok($$ select public.delete_my_account() $$, 'client can delete own account');
reset role;
select is((select count(*)::int from auth.users where id = '00000000-0000-4000-a000-000000000003'), 0,
          'deleted account is gone');

select * from finish();
rollback;
