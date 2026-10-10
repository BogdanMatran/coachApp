-- Row level security. Coaches own their libraries, templates and clients' plans;
-- clients read their own plans and write only their own logs and check-ins.

-- Helpers are SECURITY DEFINER so policies on profiles don't recurse into themselves.
create or replace function public.auth_role()
returns public.user_role
language sql stable security definer set search_path = public
as $$ select role from public.profiles where id = auth.uid() $$;

create or replace function public.my_coach_id()
returns uuid
language sql stable security definer set search_path = public
as $$ select coach_id from public.profiles where id = auth.uid() $$;

create or replace function public.is_coach_of(p_client uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where id = p_client and coach_id = auth.uid() and coach_id is not null
    )
$$;

alter table public.profiles          enable row level security;
alter table public.invites           enable row level security;
alter table public.exercises         enable row level security;
alter table public.foods             enable row level security;
alter table public.supplements       enable row level security;
alter table public.workout_plans     enable row level security;
alter table public.workout_days      enable row level security;
alter table public.workout_exercises enable row level security;
alter table public.meal_plans        enable row level security;
alter table public.meals             enable row level security;
alter table public.meal_items        enable row level security;
alter table public.supplement_plans  enable row level security;
alter table public.supplement_items  enable row level security;
alter table public.workout_logs      enable row level security;
alter table public.set_logs          enable row level security;
alter table public.meal_logs         enable row level security;
alter table public.supplement_logs   enable row level security;
alter table public.checkin_templates enable row level security;
alter table public.checkins          enable row level security;
alter table public.checkin_photos    enable row level security;

-- ---------------------------------------------------------------------------
-- Profiles: you, your clients, your coach. Role and coach_id are only changed
-- by trusted functions, so authenticated users may update a few columns only.
-- ---------------------------------------------------------------------------
create policy profiles_select on public.profiles for select to authenticated
    using (id = auth.uid() or coach_id = auth.uid() or id = public.my_coach_id());

create policy profiles_update on public.profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

revoke insert, update, delete on public.profiles from anon, authenticated;
grant update (full_name, avatar_url, units) on public.profiles to authenticated;

-- ---------------------------------------------------------------------------
-- Invites: coach only. Redemption goes through redeem_invite()/check_invite().
-- ---------------------------------------------------------------------------
create policy invites_coach on public.invites for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');

-- ---------------------------------------------------------------------------
-- Libraries
-- ---------------------------------------------------------------------------
create policy exercises_select on public.exercises for select to authenticated
    using (coach_id is null or coach_id = auth.uid() or coach_id = public.my_coach_id());
create policy exercises_write on public.exercises for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');

create policy foods_select on public.foods for select to authenticated
    using (coach_id is null or coach_id = auth.uid() or coach_id = public.my_coach_id());
create policy foods_write on public.foods for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');

create policy supplements_select on public.supplements for select to authenticated
    using (coach_id is null or coach_id = auth.uid() or coach_id = public.my_coach_id());
create policy supplements_write on public.supplements for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');

-- ---------------------------------------------------------------------------
-- Plans. Child tables piggy-back on the parent's policies through EXISTS.
-- ---------------------------------------------------------------------------
create policy workout_plans_select on public.workout_plans for select to authenticated
    using (coach_id = auth.uid() or client_id = auth.uid());
create policy workout_plans_write on public.workout_plans for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and (client_id is null or public.is_coach_of(client_id)));

create policy workout_days_select on public.workout_days for select to authenticated
    using (exists (select 1 from public.workout_plans p where p.id = plan_id));
create policy workout_days_write on public.workout_days for all to authenticated
    using (exists (select 1 from public.workout_plans p where p.id = plan_id and p.coach_id = auth.uid()))
    with check (exists (select 1 from public.workout_plans p where p.id = plan_id and p.coach_id = auth.uid()));

create policy workout_exercises_select on public.workout_exercises for select to authenticated
    using (exists (select 1 from public.workout_days d where d.id = day_id));
create policy workout_exercises_write on public.workout_exercises for all to authenticated
    using (exists (
        select 1 from public.workout_days d join public.workout_plans p on p.id = d.plan_id
        where d.id = day_id and p.coach_id = auth.uid()))
    with check (exists (
        select 1 from public.workout_days d join public.workout_plans p on p.id = d.plan_id
        where d.id = day_id and p.coach_id = auth.uid()));

create policy meal_plans_select on public.meal_plans for select to authenticated
    using (coach_id = auth.uid() or client_id = auth.uid());
create policy meal_plans_write on public.meal_plans for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and (client_id is null or public.is_coach_of(client_id)));

create policy meals_select on public.meals for select to authenticated
    using (exists (select 1 from public.meal_plans p where p.id = plan_id));
create policy meals_write on public.meals for all to authenticated
    using (exists (select 1 from public.meal_plans p where p.id = plan_id and p.coach_id = auth.uid()))
    with check (exists (select 1 from public.meal_plans p where p.id = plan_id and p.coach_id = auth.uid()));

create policy meal_items_select on public.meal_items for select to authenticated
    using (exists (select 1 from public.meals m where m.id = meal_id));
create policy meal_items_write on public.meal_items for all to authenticated
    using (exists (
        select 1 from public.meals m join public.meal_plans p on p.id = m.plan_id
        where m.id = meal_id and p.coach_id = auth.uid()))
    with check (exists (
        select 1 from public.meals m join public.meal_plans p on p.id = m.plan_id
        where m.id = meal_id and p.coach_id = auth.uid()));

create policy supplement_plans_select on public.supplement_plans for select to authenticated
    using (coach_id = auth.uid() or client_id = auth.uid());
create policy supplement_plans_write on public.supplement_plans for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and (client_id is null or public.is_coach_of(client_id)));

create policy supplement_items_select on public.supplement_items for select to authenticated
    using (exists (select 1 from public.supplement_plans p where p.id = plan_id));
create policy supplement_items_write on public.supplement_items for all to authenticated
    using (exists (select 1 from public.supplement_plans p where p.id = plan_id and p.coach_id = auth.uid()))
    with check (exists (select 1 from public.supplement_plans p where p.id = plan_id and p.coach_id = auth.uid()));

-- ---------------------------------------------------------------------------
-- Logs: client writes own, coach reads clients'.
-- ---------------------------------------------------------------------------
create policy workout_logs_client on public.workout_logs for all to authenticated
    using (client_id = auth.uid()) with check (client_id = auth.uid());
create policy workout_logs_coach on public.workout_logs for select to authenticated
    using (public.is_coach_of(client_id));

create policy set_logs_select on public.set_logs for select to authenticated
    using (exists (select 1 from public.workout_logs l where l.id = log_id));
create policy set_logs_client on public.set_logs for all to authenticated
    using (exists (select 1 from public.workout_logs l where l.id = log_id and l.client_id = auth.uid()))
    with check (exists (select 1 from public.workout_logs l where l.id = log_id and l.client_id = auth.uid()));

create policy meal_logs_client on public.meal_logs for all to authenticated
    using (client_id = auth.uid()) with check (client_id = auth.uid());
create policy meal_logs_coach on public.meal_logs for select to authenticated
    using (public.is_coach_of(client_id));

create policy supplement_logs_client on public.supplement_logs for all to authenticated
    using (client_id = auth.uid()) with check (client_id = auth.uid());
create policy supplement_logs_coach on public.supplement_logs for select to authenticated
    using (public.is_coach_of(client_id));

-- ---------------------------------------------------------------------------
-- Check-ins
-- ---------------------------------------------------------------------------
create policy checkin_templates_coach on public.checkin_templates for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');
create policy checkin_templates_client on public.checkin_templates for select to authenticated
    using (coach_id = public.my_coach_id());

create policy checkins_client_select on public.checkins for select to authenticated
    using (client_id = auth.uid());
create policy checkins_client_insert on public.checkins for insert to authenticated
    with check (client_id = auth.uid() and coach_id = public.my_coach_id() and status = 'submitted');
create policy checkins_client_update on public.checkins for update to authenticated
    using (client_id = auth.uid() and status = 'submitted')
    with check (client_id = auth.uid() and coach_id = public.my_coach_id());
create policy checkins_coach_select on public.checkins for select to authenticated
    using (coach_id = auth.uid() and public.is_coach_of(client_id));
create policy checkins_coach_update on public.checkins for update to authenticated
    using (coach_id = auth.uid() and public.is_coach_of(client_id))
    with check (coach_id = auth.uid());

-- Clients can't write feedback/review state; coaches can't rewrite the client's data.
create or replace function public.guard_checkin_update()
returns trigger
language plpgsql
as $$
begin
    if auth.uid() = old.client_id then
        new.status := old.status;
        new.coach_feedback := old.coach_feedback;
        new.reviewed_at := old.reviewed_at;
        new.coach_id := old.coach_id;
    elsif auth.uid() = old.coach_id then
        new.client_id := old.client_id;
        new.week_start := old.week_start;
        new.weight := old.weight;
        new.body_fat := old.body_fat;
        new.measurements := old.measurements;
        new.answers := old.answers;
        new.submitted_at := old.submitted_at;
        if new.status = 'reviewed' and old.status <> 'reviewed' then
            new.reviewed_at := now();
        end if;
    end if;
    return new;
end;
$$;

create trigger checkins_guard before update on public.checkins
    for each row execute function public.guard_checkin_update();

create policy checkin_photos_select on public.checkin_photos for select to authenticated
    using (exists (select 1 from public.checkins c where c.id = checkin_id));
create policy checkin_photos_client on public.checkin_photos for all to authenticated
    using (exists (
        select 1 from public.checkins c
        where c.id = checkin_id and c.client_id = auth.uid() and c.status = 'submitted'))
    with check (exists (
        select 1 from public.checkins c
        where c.id = checkin_id and c.client_id = auth.uid() and c.status = 'submitted'));
