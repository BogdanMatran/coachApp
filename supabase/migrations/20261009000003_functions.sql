-- Signup, invites, plan saving/assignment and the coach dashboard view.

-- ---------------------------------------------------------------------------
-- Profile bootstrap. Signup metadata: full_name, role ('coach' | 'client'), invite_code.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer set search_path = public
as $$
declare
    v_role   public.user_role := case when new.raw_user_meta_data ->> 'role' = 'coach' then 'coach' else 'client' end;
    v_code   text := upper(trim(coalesce(new.raw_user_meta_data ->> 'invite_code', '')));
    v_invite public.invites;
begin
    insert into public.profiles (id, role, full_name, email)
    values (new.id, v_role, coalesce(new.raw_user_meta_data ->> 'full_name', ''), new.email);

    if v_role = 'client' and v_code <> '' then
        select * into v_invite from public.invites
        where code = v_code and used_by is null and expires_at > now()
        for update;
        if found then
            update public.profiles set coach_id = v_invite.coach_id where id = new.id;
            update public.invites set used_by = new.id, used_at = now() where id = v_invite.id;
        end if;
    end if;
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Lets the signup screen validate a code before creating the account.
create or replace function public.check_invite(p_code text)
returns text
language sql stable security definer set search_path = public
as $$
    select p.full_name
    from public.invites i join public.profiles p on p.id = i.coach_id
    where i.code = upper(trim(p_code)) and i.used_by is null and i.expires_at > now()
$$;
grant execute on function public.check_invite(text) to anon, authenticated;

-- For an existing client account that joins (or switches to) a coach.
create or replace function public.redeem_invite(p_code text)
returns uuid
language plpgsql security definer set search_path = public
as $$
declare
    v_invite public.invites;
begin
    if auth.uid() is null then
        raise exception 'Not signed in';
    end if;
    if public.auth_role() = 'coach' then
        raise exception 'Coaches cannot redeem invites';
    end if;
    select * into v_invite from public.invites
    where code = upper(trim(p_code)) and used_by is null and expires_at > now()
    for update;
    if not found then
        raise exception 'Invite code is invalid or expired';
    end if;
    update public.profiles set coach_id = v_invite.coach_id where id = auth.uid();
    update public.invites set used_by = auth.uid(), used_at = now() where id = v_invite.id;
    return v_invite.coach_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Plans: keep a single active plan per client and kind, and track updated_at.
-- ---------------------------------------------------------------------------
create or replace function public.plan_before_write()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'UPDATE' then
        new.updated_at := now();
    end if;
    if new.active and new.client_id is not null then
        execute format('update %I.%I set active = false where client_id = $1 and active and id <> $2',
                       tg_table_schema, tg_table_name)
            using new.client_id, new.id;
    end if;
    return new;
end;
$$;

create trigger workout_plans_before_write before insert or update on public.workout_plans
    for each row execute function public.plan_before_write();
create trigger meal_plans_before_write before insert or update on public.meal_plans
    for each row execute function public.plan_before_write();
create trigger supplement_plans_before_write before insert or update on public.supplement_plans
    for each row execute function public.plan_before_write();

-- Plan builders send the whole tree as JSON. Existing child ids are updated in
-- place (so logs keep pointing at them), new ones inserted, missing ones deleted.
-- These run as the caller, so RLS decides what may be written.

create or replace function public.save_workout_plan(p jsonb)
returns uuid
language plpgsql set search_path = public
as $$
declare
    v_plan      uuid := nullif(p ->> 'id', '')::uuid;
    v_day       jsonb;
    v_day_pos   bigint;
    v_day_id    uuid;
    v_ex        jsonb;
    v_ex_pos    bigint;
    v_ex_id     uuid;
    v_keep_days uuid[] := '{}';
    v_keep_ex   uuid[];
begin
    if v_plan is null then
        insert into public.workout_plans (coach_id, client_id, name, notes, active, start_date)
        values (auth.uid(), nullif(p ->> 'client_id', '')::uuid, p ->> 'name', p ->> 'notes',
                coalesce((p ->> 'active')::boolean, true),
                coalesce(nullif(p ->> 'start_date', '')::date, current_date))
        returning id into v_plan;
    else
        update public.workout_plans set name = p ->> 'name', notes = p ->> 'notes'
        where id = v_plan and coach_id = auth.uid();
        if not found then
            raise exception 'Workout plan not found';
        end if;
    end if;

    for v_day, v_day_pos in
        select value, ordinality from jsonb_array_elements(coalesce(p -> 'days', '[]')) with ordinality
    loop
        v_day_id := nullif(v_day ->> 'id', '')::uuid;
        if v_day_id is not null then
            update public.workout_days
            set day_index = v_day_pos - 1, title = v_day ->> 'title', notes = v_day ->> 'notes'
            where id = v_day_id and plan_id = v_plan;
            if not found then v_day_id := null; end if;
        end if;
        if v_day_id is null then
            insert into public.workout_days (plan_id, day_index, title, notes)
            values (v_plan, v_day_pos - 1, v_day ->> 'title', v_day ->> 'notes')
            returning id into v_day_id;
        end if;
        v_keep_days := v_keep_days || v_day_id;

        v_keep_ex := '{}';
        for v_ex, v_ex_pos in
            select value, ordinality from jsonb_array_elements(coalesce(v_day -> 'exercises', '[]')) with ordinality
        loop
            v_ex_id := nullif(v_ex ->> 'id', '')::uuid;
            if v_ex_id is not null then
                update public.workout_exercises
                set day_id      = v_day_id,
                    exercise_id = (v_ex ->> 'exercise_id')::uuid,
                    position    = v_ex_pos - 1,
                    sets        = coalesce((v_ex ->> 'sets')::int, 3),
                    reps        = coalesce(v_ex ->> 'reps', '10'),
                    rest_sec    = coalesce((v_ex ->> 'rest_sec')::int, 90),
                    tempo       = v_ex ->> 'tempo',
                    target_rpe  = (v_ex ->> 'target_rpe')::numeric,
                    notes       = v_ex ->> 'notes'
                where id = v_ex_id
                  and day_id in (select id from public.workout_days where plan_id = v_plan);
                if not found then v_ex_id := null; end if;
            end if;
            if v_ex_id is null then
                insert into public.workout_exercises (day_id, exercise_id, position, sets, reps, rest_sec, tempo, target_rpe, notes)
                values (v_day_id, (v_ex ->> 'exercise_id')::uuid, v_ex_pos - 1,
                        coalesce((v_ex ->> 'sets')::int, 3), coalesce(v_ex ->> 'reps', '10'),
                        coalesce((v_ex ->> 'rest_sec')::int, 90), v_ex ->> 'tempo',
                        (v_ex ->> 'target_rpe')::numeric, v_ex ->> 'notes')
                returning id into v_ex_id;
            end if;
            v_keep_ex := v_keep_ex || v_ex_id;
        end loop;
        delete from public.workout_exercises where day_id = v_day_id and id <> all (v_keep_ex);
    end loop;
    delete from public.workout_days where plan_id = v_plan and id <> all (v_keep_days);
    return v_plan;
end;
$$;

create or replace function public.save_meal_plan(p jsonb)
returns uuid
language plpgsql set search_path = public
as $$
declare
    v_plan       uuid := nullif(p ->> 'id', '')::uuid;
    v_meal       jsonb;
    v_meal_pos   bigint;
    v_meal_id    uuid;
    v_item       jsonb;
    v_item_pos   bigint;
    v_item_id    uuid;
    v_keep_meals uuid[] := '{}';
    v_keep_items uuid[];
begin
    if v_plan is null then
        insert into public.meal_plans (coach_id, client_id, name, notes, target_kcal, target_protein, target_carbs, target_fat, active)
        values (auth.uid(), nullif(p ->> 'client_id', '')::uuid, p ->> 'name', p ->> 'notes',
                (p ->> 'target_kcal')::int, (p ->> 'target_protein')::int,
                (p ->> 'target_carbs')::int, (p ->> 'target_fat')::int,
                coalesce((p ->> 'active')::boolean, true))
        returning id into v_plan;
    else
        update public.meal_plans
        set name = p ->> 'name', notes = p ->> 'notes',
            target_kcal = (p ->> 'target_kcal')::int, target_protein = (p ->> 'target_protein')::int,
            target_carbs = (p ->> 'target_carbs')::int, target_fat = (p ->> 'target_fat')::int
        where id = v_plan and coach_id = auth.uid();
        if not found then
            raise exception 'Meal plan not found';
        end if;
    end if;

    for v_meal, v_meal_pos in
        select value, ordinality from jsonb_array_elements(coalesce(p -> 'meals', '[]')) with ordinality
    loop
        v_meal_id := nullif(v_meal ->> 'id', '')::uuid;
        if v_meal_id is not null then
            update public.meals
            set position = v_meal_pos - 1, name = v_meal ->> 'name', time_hint = v_meal ->> 'time_hint'
            where id = v_meal_id and plan_id = v_plan;
            if not found then v_meal_id := null; end if;
        end if;
        if v_meal_id is null then
            insert into public.meals (plan_id, position, name, time_hint)
            values (v_plan, v_meal_pos - 1, v_meal ->> 'name', v_meal ->> 'time_hint')
            returning id into v_meal_id;
        end if;
        v_keep_meals := v_keep_meals || v_meal_id;

        v_keep_items := '{}';
        for v_item, v_item_pos in
            select value, ordinality from jsonb_array_elements(coalesce(v_meal -> 'items', '[]')) with ordinality
        loop
            v_item_id := nullif(v_item ->> 'id', '')::uuid;
            if v_item_id is not null then
                update public.meal_items
                set meal_id = v_meal_id, food_id = (v_item ->> 'food_id')::uuid, position = v_item_pos - 1,
                    grams = coalesce((v_item ->> 'grams')::numeric, 100), notes = v_item ->> 'notes'
                where id = v_item_id
                  and meal_id in (select id from public.meals where plan_id = v_plan);
                if not found then v_item_id := null; end if;
            end if;
            if v_item_id is null then
                insert into public.meal_items (meal_id, food_id, position, grams, notes)
                values (v_meal_id, (v_item ->> 'food_id')::uuid, v_item_pos - 1,
                        coalesce((v_item ->> 'grams')::numeric, 100), v_item ->> 'notes')
                returning id into v_item_id;
            end if;
            v_keep_items := v_keep_items || v_item_id;
        end loop;
        delete from public.meal_items where meal_id = v_meal_id and id <> all (v_keep_items);
    end loop;
    delete from public.meals where plan_id = v_plan and id <> all (v_keep_meals);
    return v_plan;
end;
$$;

create or replace function public.save_supplement_plan(p jsonb)
returns uuid
language plpgsql set search_path = public
as $$
declare
    v_plan     uuid := nullif(p ->> 'id', '')::uuid;
    v_item     jsonb;
    v_item_pos bigint;
    v_item_id  uuid;
    v_keep     uuid[] := '{}';
begin
    if v_plan is null then
        insert into public.supplement_plans (coach_id, client_id, name, notes, active)
        values (auth.uid(), nullif(p ->> 'client_id', '')::uuid, p ->> 'name', p ->> 'notes',
                coalesce((p ->> 'active')::boolean, true))
        returning id into v_plan;
    else
        update public.supplement_plans set name = p ->> 'name', notes = p ->> 'notes'
        where id = v_plan and coach_id = auth.uid();
        if not found then
            raise exception 'Supplement plan not found';
        end if;
    end if;

    for v_item, v_item_pos in
        select value, ordinality from jsonb_array_elements(coalesce(p -> 'items', '[]')) with ordinality
    loop
        v_item_id := nullif(v_item ->> 'id', '')::uuid;
        if v_item_id is not null then
            update public.supplement_items
            set supplement_id = (v_item ->> 'supplement_id')::uuid, position = v_item_pos - 1,
                dosage = v_item ->> 'dosage',
                timing = coalesce(v_item ->> 'timing', 'morning')::public.supplement_timing,
                notes = v_item ->> 'notes'
            where id = v_item_id and plan_id = v_plan;
            if not found then v_item_id := null; end if;
        end if;
        if v_item_id is null then
            insert into public.supplement_items (plan_id, supplement_id, position, dosage, timing, notes)
            values (v_plan, (v_item ->> 'supplement_id')::uuid, v_item_pos - 1, v_item ->> 'dosage',
                    coalesce(v_item ->> 'timing', 'morning')::public.supplement_timing, v_item ->> 'notes')
            returning id into v_item_id;
        end if;
        v_keep := v_keep || v_item_id;
    end loop;
    delete from public.supplement_items where plan_id = v_plan and id <> all (v_keep);
    return v_plan;
end;
$$;

-- Copies a template (or another plan) to a client as their new active plan.
create or replace function public.assign_plan(p_kind text, p_template uuid, p_client uuid)
returns uuid
language plpgsql set search_path = public
as $$
declare
    v_payload jsonb;
begin
    if not public.is_coach_of(p_client) then
        raise exception 'Not your client';
    end if;

    if p_kind = 'workout' then
        select jsonb_build_object(
                   'name', wp.name, 'notes', wp.notes, 'client_id', p_client, 'active', true,
                   'days', coalesce((
                       select jsonb_agg(jsonb_build_object(
                                  'title', d.title, 'notes', d.notes,
                                  'exercises', coalesce((
                                      select jsonb_agg(to_jsonb(e) - 'id' - 'day_id' order by e.position)
                                      from public.workout_exercises e where e.day_id = d.id), '[]'))
                              order by d.day_index)
                       from public.workout_days d where d.plan_id = wp.id), '[]'))
        into v_payload
        from public.workout_plans wp where wp.id = p_template and wp.coach_id = auth.uid();
        if v_payload is null then raise exception 'Template not found'; end if;
        return public.save_workout_plan(v_payload);

    elsif p_kind = 'meal' then
        select jsonb_build_object(
                   'name', mp.name, 'notes', mp.notes, 'client_id', p_client, 'active', true,
                   'target_kcal', mp.target_kcal, 'target_protein', mp.target_protein,
                   'target_carbs', mp.target_carbs, 'target_fat', mp.target_fat,
                   'meals', coalesce((
                       select jsonb_agg(jsonb_build_object(
                                  'name', m.name, 'time_hint', m.time_hint,
                                  'items', coalesce((
                                      select jsonb_agg(to_jsonb(i) - 'id' - 'meal_id' order by i.position)
                                      from public.meal_items i where i.meal_id = m.id), '[]'))
                              order by m.position)
                       from public.meals m where m.plan_id = mp.id), '[]'))
        into v_payload
        from public.meal_plans mp where mp.id = p_template and mp.coach_id = auth.uid();
        if v_payload is null then raise exception 'Template not found'; end if;
        return public.save_meal_plan(v_payload);

    elsif p_kind = 'supplement' then
        select jsonb_build_object(
                   'name', sp.name, 'notes', sp.notes, 'client_id', p_client, 'active', true,
                   'items', coalesce((
                       select jsonb_agg(to_jsonb(i) - 'id' - 'plan_id' order by i.position)
                       from public.supplement_items i where i.plan_id = sp.id), '[]'))
        into v_payload
        from public.supplement_plans sp where sp.id = p_template and sp.coach_id = auth.uid();
        if v_payload is null then raise exception 'Template not found'; end if;
        return public.save_supplement_plan(v_payload);
    end if;

    raise exception 'Unknown plan kind %', p_kind;
end;
$$;

-- ---------------------------------------------------------------------------
-- Coach dashboard. security_invoker keeps the caller's RLS in force.
-- ---------------------------------------------------------------------------
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
                    where ml.client_id = p.id and ml.completed and ml.log_date > current_date - 7)
                 + (select count(*) from public.supplement_logs sl
                    where sl.client_id = p.id and sl.taken and sl.log_date > current_date - 7) as n
        ) d
    ) as adherence_7d
from public.profiles p
where p.role = 'client' and p.coach_id is not null;

grant select on public.coach_client_overview to authenticated;
