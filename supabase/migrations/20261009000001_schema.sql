-- Core schema for CoachApp: profiles, invites, libraries, plans, logs and check-ins.

create type public.user_role as enum ('coach', 'client');
create type public.supplement_timing as enum ('morning', 'pre_workout', 'post_workout', 'with_meals', 'evening', 'bedtime');
create type public.checkin_status as enum ('submitted', 'reviewed');
create type public.photo_pose as enum ('front', 'side', 'back');

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
create table public.profiles (
    id         uuid primary key references auth.users (id) on delete cascade,
    role       public.user_role not null default 'client',
    full_name  text not null default '',
    email      text,
    avatar_url text,
    coach_id   uuid references public.profiles (id) on delete set null,
    units      text not null default 'metric' check (units in ('metric', 'imperial')),
    created_at timestamptz not null default now()
);
create index profiles_coach_id_idx on public.profiles (coach_id);

create table public.invites (
    id         uuid primary key default gen_random_uuid(),
    code       text not null unique default upper(substr(md5(gen_random_uuid()::text), 1, 8)),
    coach_id   uuid not null references public.profiles (id) on delete cascade,
    email      text,
    expires_at timestamptz not null default now() + interval '14 days',
    used_by    uuid references public.profiles (id) on delete set null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index invites_coach_id_idx on public.invites (coach_id);

-- ---------------------------------------------------------------------------
-- Libraries (coach_id null = global item visible to everyone)
-- ---------------------------------------------------------------------------
create table public.exercises (
    id           uuid primary key default gen_random_uuid(),
    coach_id     uuid references public.profiles (id) on delete cascade,
    name         text not null,
    muscle_group text,
    equipment    text,
    video_url    text,
    instructions text,
    created_at   timestamptz not null default now()
);
create index exercises_coach_id_idx on public.exercises (coach_id);

create table public.foods (
    id           uuid primary key default gen_random_uuid(),
    coach_id     uuid references public.profiles (id) on delete cascade,
    name         text not null,
    kcal         numeric(7, 2) not null default 0, -- per 100 g
    protein      numeric(6, 2) not null default 0,
    carbs        numeric(6, 2) not null default 0,
    fat          numeric(6, 2) not null default 0,
    serving_unit text not null default 'g',
    created_at   timestamptz not null default now()
);
create index foods_coach_id_idx on public.foods (coach_id);

create table public.supplements (
    id             uuid primary key default gen_random_uuid(),
    coach_id       uuid references public.profiles (id) on delete cascade,
    name           text not null,
    default_dosage text,
    unit           text,
    notes          text,
    created_at     timestamptz not null default now()
);
create index supplements_coach_id_idx on public.supplements (coach_id);

-- ---------------------------------------------------------------------------
-- Plans (client_id null = reusable template)
-- ---------------------------------------------------------------------------
create table public.workout_plans (
    id         uuid primary key default gen_random_uuid(),
    coach_id   uuid not null references public.profiles (id) on delete cascade,
    client_id  uuid references public.profiles (id) on delete cascade,
    name       text not null,
    notes      text,
    active     boolean not null default true,
    start_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index workout_plans_coach_id_idx on public.workout_plans (coach_id);
create unique index workout_plans_one_active on public.workout_plans (client_id) where client_id is not null and active;

create table public.workout_days (
    id        uuid primary key default gen_random_uuid(),
    plan_id   uuid not null references public.workout_plans (id) on delete cascade,
    day_index int not null default 0,
    title     text not null,
    notes     text
);
create index workout_days_plan_id_idx on public.workout_days (plan_id);

create table public.workout_exercises (
    id          uuid primary key default gen_random_uuid(),
    day_id      uuid not null references public.workout_days (id) on delete cascade,
    exercise_id uuid not null references public.exercises (id) on delete cascade,
    position    int not null default 0,
    sets        int not null default 3,
    reps        text not null default '10', -- text so coaches can write ranges like 8-12
    rest_sec    int not null default 90,
    tempo       text,
    target_rpe  numeric(3, 1),
    notes       text
);
create index workout_exercises_day_id_idx on public.workout_exercises (day_id);

create table public.meal_plans (
    id             uuid primary key default gen_random_uuid(),
    coach_id       uuid not null references public.profiles (id) on delete cascade,
    client_id      uuid references public.profiles (id) on delete cascade,
    name           text not null,
    notes          text,
    target_kcal    int,
    target_protein int,
    target_carbs   int,
    target_fat     int,
    active         boolean not null default true,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index meal_plans_coach_id_idx on public.meal_plans (coach_id);
create unique index meal_plans_one_active on public.meal_plans (client_id) where client_id is not null and active;

create table public.meals (
    id        uuid primary key default gen_random_uuid(),
    plan_id   uuid not null references public.meal_plans (id) on delete cascade,
    name      text not null,
    position  int not null default 0,
    time_hint text
);
create index meals_plan_id_idx on public.meals (plan_id);

create table public.meal_items (
    id       uuid primary key default gen_random_uuid(),
    meal_id  uuid not null references public.meals (id) on delete cascade,
    food_id  uuid not null references public.foods (id) on delete cascade,
    grams    numeric(7, 1) not null default 100,
    notes    text,
    position int not null default 0
);
create index meal_items_meal_id_idx on public.meal_items (meal_id);

create table public.supplement_plans (
    id         uuid primary key default gen_random_uuid(),
    coach_id   uuid not null references public.profiles (id) on delete cascade,
    client_id  uuid references public.profiles (id) on delete cascade,
    name       text not null,
    notes      text,
    active     boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index supplement_plans_coach_id_idx on public.supplement_plans (coach_id);
create unique index supplement_plans_one_active on public.supplement_plans (client_id) where client_id is not null and active;

create table public.supplement_items (
    id            uuid primary key default gen_random_uuid(),
    plan_id       uuid not null references public.supplement_plans (id) on delete cascade,
    supplement_id uuid not null references public.supplements (id) on delete cascade,
    dosage        text,
    timing        public.supplement_timing not null default 'morning',
    notes         text,
    position      int not null default 0
);
create index supplement_items_plan_id_idx on public.supplement_items (plan_id);

-- ---------------------------------------------------------------------------
-- Client logs
-- ---------------------------------------------------------------------------
create table public.workout_logs (
    id             uuid primary key default gen_random_uuid(),
    client_id      uuid not null references public.profiles (id) on delete cascade,
    workout_day_id uuid references public.workout_days (id) on delete set null,
    day_title      text,
    performed_at   timestamptz not null default now(),
    notes          text
);
create index workout_logs_client_idx on public.workout_logs (client_id, performed_at desc);

create table public.set_logs (
    id                  uuid primary key default gen_random_uuid(),
    log_id              uuid not null references public.workout_logs (id) on delete cascade,
    workout_exercise_id uuid references public.workout_exercises (id) on delete set null,
    exercise_id         uuid not null references public.exercises (id) on delete cascade,
    set_no              int not null,
    reps                int,
    weight              numeric(6, 2),
    rpe                 numeric(3, 1)
);
create index set_logs_log_id_idx on public.set_logs (log_id);
create index set_logs_exercise_id_idx on public.set_logs (exercise_id);

create table public.meal_logs (
    id        uuid primary key default gen_random_uuid(),
    client_id uuid not null references public.profiles (id) on delete cascade,
    meal_id   uuid not null references public.meals (id) on delete cascade,
    log_date  date not null default current_date,
    completed boolean not null default true,
    notes     text,
    unique (client_id, meal_id, log_date)
);

create table public.supplement_logs (
    id                 uuid primary key default gen_random_uuid(),
    client_id          uuid not null references public.profiles (id) on delete cascade,
    supplement_item_id uuid not null references public.supplement_items (id) on delete cascade,
    log_date           date not null default current_date,
    taken              boolean not null default true,
    unique (client_id, supplement_item_id, log_date)
);

-- ---------------------------------------------------------------------------
-- Check-ins
-- ---------------------------------------------------------------------------
create table public.checkin_templates (
    id         uuid primary key default gen_random_uuid(),
    coach_id   uuid not null unique references public.profiles (id) on delete cascade,
    -- [{ "id": "energy", "label": "Energy levels", "type": "scale" | "text" | "number" | "yesno" }]
    questions  jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default now()
);

create table public.checkins (
    id             uuid primary key default gen_random_uuid(),
    client_id      uuid not null references public.profiles (id) on delete cascade,
    coach_id       uuid not null references public.profiles (id) on delete cascade,
    week_start     date not null,
    weight         numeric(5, 2),
    body_fat       numeric(4, 1),
    measurements   jsonb not null default '{}'::jsonb,
    answers        jsonb not null default '{}'::jsonb,
    status         public.checkin_status not null default 'submitted',
    coach_feedback text,
    submitted_at   timestamptz not null default now(),
    reviewed_at    timestamptz,
    unique (client_id, week_start)
);
create index checkins_coach_status_idx on public.checkins (coach_id, status);

create table public.checkin_photos (
    id           uuid primary key default gen_random_uuid(),
    checkin_id   uuid not null references public.checkins (id) on delete cascade,
    storage_path text not null,
    pose         public.photo_pose not null,
    created_at   timestamptz not null default now(),
    unique (checkin_id, pose)
);
