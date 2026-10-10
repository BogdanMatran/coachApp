-- Demo data for local development. All demo accounts use the password "password123".
--   coach@demo.app  (coach)
--   anna@demo.app   (client of the demo coach, with plans and a check-in)
--   ben@demo.app    (client of the demo coach, no plans yet)
--   other@demo.app  (a second coach, used by the RLS tests)

do $$
declare
    v_users jsonb := '[
        {"id": "00000000-0000-4000-a000-000000000001", "email": "coach@demo.app", "name": "Demo Coach", "role": "coach"},
        {"id": "00000000-0000-4000-a000-000000000002", "email": "anna@demo.app",  "name": "Anna Client", "role": "client"},
        {"id": "00000000-0000-4000-a000-000000000003", "email": "ben@demo.app",   "name": "Ben Client",  "role": "client"},
        {"id": "00000000-0000-4000-a000-000000000004", "email": "other@demo.app", "name": "Other Coach", "role": "coach"}
    ]';
    u jsonb;
begin
    for u in select * from jsonb_array_elements(v_users) loop
        insert into auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change
        ) values (
            '00000000-0000-0000-0000-000000000000', (u ->> 'id')::uuid, 'authenticated', 'authenticated',
            u ->> 'email', extensions.crypt('password123', extensions.gen_salt('bf')), now(),
            '{"provider": "email", "providers": ["email"]}',
            jsonb_build_object('full_name', u ->> 'name', 'role', u ->> 'role'),
            now(), now(), '', '', '', ''
        );
        insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
        values (gen_random_uuid(), (u ->> 'id')::uuid, u ->> 'id',
                jsonb_build_object('sub', u ->> 'id', 'email', u ->> 'email', 'email_verified', true),
                'email', now(), now(), now());
    end loop;
end $$;

update public.profiles set coach_id = '00000000-0000-4000-a000-000000000001'
where id in ('00000000-0000-4000-a000-000000000002', '00000000-0000-4000-a000-000000000003');

insert into public.invites (code, coach_id) values ('WELCOME1', '00000000-0000-4000-a000-000000000001');

-- Global library ------------------------------------------------------------
insert into public.exercises (id, name, muscle_group, equipment) values
    ('10000000-0000-4000-a000-000000000001', 'Back Squat',          'Legs',      'Barbell'),
    ('10000000-0000-4000-a000-000000000002', 'Romanian Deadlift',   'Hamstrings','Barbell'),
    ('10000000-0000-4000-a000-000000000003', 'Bench Press',         'Chest',     'Barbell'),
    ('10000000-0000-4000-a000-000000000004', 'Overhead Press',      'Shoulders', 'Barbell'),
    ('10000000-0000-4000-a000-000000000005', 'Pull-up',             'Back',      'Bodyweight'),
    ('10000000-0000-4000-a000-000000000006', 'Seated Cable Row',    'Back',      'Cable'),
    ('10000000-0000-4000-a000-000000000007', 'Walking Lunge',       'Legs',      'Dumbbell'),
    ('10000000-0000-4000-a000-000000000008', 'Lateral Raise',       'Shoulders', 'Dumbbell'),
    ('10000000-0000-4000-a000-000000000009', 'Plank',               'Core',      'Bodyweight'),
    ('10000000-0000-4000-a000-000000000010', 'Incline DB Press',    'Chest',     'Dumbbell');

insert into public.foods (id, name, kcal, protein, carbs, fat) values
    ('20000000-0000-4000-a000-000000000001', 'Oats',                 389, 16.9, 66.3, 6.9),
    ('20000000-0000-4000-a000-000000000002', 'Greek Yogurt 0%',       59, 10.0,  3.6, 0.4),
    ('20000000-0000-4000-a000-000000000003', 'Chicken Breast',       165, 31.0,  0.0, 3.6),
    ('20000000-0000-4000-a000-000000000004', 'White Rice (cooked)',  130,  2.7, 28.0, 0.3),
    ('20000000-0000-4000-a000-000000000005', 'Broccoli',              34,  2.8,  6.6, 0.4),
    ('20000000-0000-4000-a000-000000000006', 'Salmon',               208, 20.0,  0.0, 13.0),
    ('20000000-0000-4000-a000-000000000007', 'Sweet Potato',          86,  1.6, 20.0, 0.1),
    ('20000000-0000-4000-a000-000000000008', 'Banana',                89,  1.1, 22.8, 0.3),
    ('20000000-0000-4000-a000-000000000009', 'Whole Eggs',           143, 12.6,  0.7, 9.5),
    ('20000000-0000-4000-a000-000000000010', 'Almonds',              579, 21.2, 21.6, 49.9);

insert into public.supplements (id, name, default_dosage, unit) values
    ('30000000-0000-4000-a000-000000000001', 'Creatine Monohydrate', '5',    'g'),
    ('30000000-0000-4000-a000-000000000002', 'Vitamin D3',           '2000', 'IU'),
    ('30000000-0000-4000-a000-000000000003', 'Omega-3 Fish Oil',     '2',    'caps'),
    ('30000000-0000-4000-a000-000000000004', 'Magnesium Glycinate',  '300',  'mg'),
    ('30000000-0000-4000-a000-000000000005', 'Whey Protein',         '30',   'g');

-- Demo coach: templates + check-in questions, assigned to Anna ----------------
do $$
declare
    v_coach uuid := '00000000-0000-4000-a000-000000000001';
    v_anna  uuid := '00000000-0000-4000-a000-000000000002';
    v_tpl   uuid;
begin
    perform set_config('request.jwt.claims', jsonb_build_object('sub', v_coach, 'role', 'authenticated')::text, true);

    v_tpl := public.save_workout_plan('{
        "name": "Upper / Lower 4-day",
        "notes": "Progress load when all sets hit the top of the rep range.",
        "days": [
            {"title": "Lower A", "exercises": [
                {"exercise_id": "10000000-0000-4000-a000-000000000001", "sets": 4, "reps": "5-8",  "rest_sec": 180, "target_rpe": 8},
                {"exercise_id": "10000000-0000-4000-a000-000000000002", "sets": 3, "reps": "8-10", "rest_sec": 120},
                {"exercise_id": "10000000-0000-4000-a000-000000000007", "sets": 3, "reps": "12",   "rest_sec": 90}
            ]},
            {"title": "Upper A", "exercises": [
                {"exercise_id": "10000000-0000-4000-a000-000000000003", "sets": 4, "reps": "6-8",  "rest_sec": 150, "target_rpe": 8},
                {"exercise_id": "10000000-0000-4000-a000-000000000005", "sets": 3, "reps": "AMRAP","rest_sec": 120},
                {"exercise_id": "10000000-0000-4000-a000-000000000008", "sets": 3, "reps": "12-15","rest_sec": 60}
            ]},
            {"title": "Lower B", "exercises": [
                {"exercise_id": "10000000-0000-4000-a000-000000000002", "sets": 4, "reps": "6-8",  "rest_sec": 150},
                {"exercise_id": "10000000-0000-4000-a000-000000000007", "sets": 3, "reps": "10",   "rest_sec": 90},
                {"exercise_id": "10000000-0000-4000-a000-000000000009", "sets": 3, "reps": "45s",  "rest_sec": 60}
            ]},
            {"title": "Upper B", "exercises": [
                {"exercise_id": "10000000-0000-4000-a000-000000000004", "sets": 4, "reps": "6-8",  "rest_sec": 150},
                {"exercise_id": "10000000-0000-4000-a000-000000000006", "sets": 3, "reps": "10-12","rest_sec": 90},
                {"exercise_id": "10000000-0000-4000-a000-000000000010", "sets": 3, "reps": "10-12","rest_sec": 90}
            ]}
        ]
    }'::jsonb);
    perform public.assign_plan('workout', v_tpl, v_anna);

    v_tpl := public.save_meal_plan('{
        "name": "Lean bulk 2400 kcal",
        "target_kcal": 2400, "target_protein": 160, "target_carbs": 280, "target_fat": 70,
        "meals": [
            {"name": "Breakfast", "time_hint": "07:30", "items": [
                {"food_id": "20000000-0000-4000-a000-000000000001", "grams": 80},
                {"food_id": "20000000-0000-4000-a000-000000000002", "grams": 250},
                {"food_id": "20000000-0000-4000-a000-000000000008", "grams": 120}
            ]},
            {"name": "Lunch", "time_hint": "12:30", "items": [
                {"food_id": "20000000-0000-4000-a000-000000000003", "grams": 180},
                {"food_id": "20000000-0000-4000-a000-000000000004", "grams": 250},
                {"food_id": "20000000-0000-4000-a000-000000000005", "grams": 150}
            ]},
            {"name": "Dinner", "time_hint": "19:00", "items": [
                {"food_id": "20000000-0000-4000-a000-000000000006", "grams": 180},
                {"food_id": "20000000-0000-4000-a000-000000000007", "grams": 300}
            ]},
            {"name": "Snack", "time_hint": "16:00", "items": [
                {"food_id": "20000000-0000-4000-a000-000000000010", "grams": 30}
            ]}
        ]
    }'::jsonb);
    perform public.assign_plan('meal', v_tpl, v_anna);

    v_tpl := public.save_supplement_plan('{
        "name": "Foundations",
        "items": [
            {"supplement_id": "30000000-0000-4000-a000-000000000001", "dosage": "5 g",     "timing": "morning"},
            {"supplement_id": "30000000-0000-4000-a000-000000000002", "dosage": "2000 IU", "timing": "with_meals"},
            {"supplement_id": "30000000-0000-4000-a000-000000000003", "dosage": "2 caps",  "timing": "with_meals"},
            {"supplement_id": "30000000-0000-4000-a000-000000000004", "dosage": "300 mg",  "timing": "bedtime"}
        ]
    }'::jsonb);
    perform public.assign_plan('supplement', v_tpl, v_anna);

    insert into public.checkin_templates (coach_id, questions) values (v_coach, '[
        {"id": "energy",    "label": "Energy levels this week",   "type": "scale"},
        {"id": "sleep",     "label": "Average sleep quality",     "type": "scale"},
        {"id": "adherence", "label": "Did you follow the plan?",  "type": "yesno"},
        {"id": "steps",     "label": "Average daily steps",       "type": "number"},
        {"id": "wins",      "label": "Wins and struggles",        "type": "text"}
    ]');

    perform set_config('request.jwt.claims', '', true);
end $$;

-- Two weeks of check-ins for Anna (last week reviewed, this week waiting)
insert into public.checkins (client_id, coach_id, week_start, weight, body_fat, measurements, answers, status, coach_feedback, reviewed_at)
values
    ('00000000-0000-4000-a000-000000000002', '00000000-0000-4000-a000-000000000001',
     date_trunc('week', current_date)::date - 7, 64.8, 24.5,
     '{"waist": 72, "hips": 98, "chest": 90}',
     '{"energy": "3", "sleep": "4", "adherence": "yes", "steps": "8500", "wins": "Hit every session."}',
     'reviewed', 'Great consistency! Bumping squat load by 2.5 kg next week.', now() - interval '5 days'),
    ('00000000-0000-4000-a000-000000000002', '00000000-0000-4000-a000-000000000001',
     date_trunc('week', current_date)::date, 64.3, 24.1,
     '{"waist": 71, "hips": 97.5, "chest": 90}',
     '{"energy": "4", "sleep": "3", "adherence": "yes", "steps": "9100", "wins": "Felt strong, slept a bit less."}',
     'submitted', null, null);

-- About ten weeks of activity for Anna so the activity heatmap and rings have data.
do $$
declare
    v_anna uuid := '00000000-0000-4000-a000-000000000002';
    v_day  date;
    v_n    int := 0;
    v_days uuid[];
begin
    select array_agg(d.id order by d.day_index) into v_days
    from workout_days d join workout_plans p on p.id = d.plan_id
    where p.client_id = v_anna and p.active;

    for v_day in select generate_series(current_date - 70, current_date - 1, interval '1 day')::date loop
        -- Workouts on most Mon/Tue/Thu/Fri, with a few skipped weeks.
        if extract(isodow from v_day) in (1, 2, 4, 5) and (extract(doy from v_day)::int % 9) <> 0 then
            insert into public.workout_logs (client_id, workout_day_id, day_title, performed_at)
            select v_anna, v_days[(v_n % array_length(v_days, 1)) + 1], d.title, v_day + time '18:00'
            from public.workout_days d where d.id = v_days[(v_n % array_length(v_days, 1)) + 1];
            v_n := v_n + 1;
        end if;

        -- Meals and supplements: completion varies by day.
        insert into public.meal_logs (client_id, meal_id, log_date, completed)
        select v_anna, ml.id, v_day, true
        from public.meals ml join public.meal_plans mp on mp.id = ml.plan_id
        where mp.client_id = v_anna and mp.active
          and (abs(hashtext(ml.id::text || v_day::text)) % 10) < 7;

        insert into public.supplement_logs (client_id, supplement_item_id, log_date, taken)
        select v_anna, si.id, v_day, true
        from public.supplement_items si join public.supplement_plans sp on sp.id = si.plan_id
        where sp.client_id = v_anna and sp.active
          and (abs(hashtext(si.id::text || v_day::text)) % 10) < 8;
    end loop;
end $$;
