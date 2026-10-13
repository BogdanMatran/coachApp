-- Columns for the imported ExerciseDB library (see NOTICE.md).
--
-- ext_id is the upstream exercise id and the natural key for the import, so re-running the seed
-- is a no-op. image_path/gif_path are bare filenames inside the exercise-media bucket, not URLs:
-- the client builds the URL, so moving the bucket never means rewriting 1,324 rows.

alter table public.exercises
    add column if not exists ext_id            text,
    add column if not exists body_part         text,
    add column if not exists target            text,
    add column if not exists secondary_muscles text[],
    add column if not exists image_path        text,
    add column if not exists gif_path          text;

create unique index if not exists exercises_ext_id_key on public.exercises (ext_id);

-- The library screen filters by body part and equipment and searches by name.
create index if not exists exercises_body_part_idx on public.exercises (body_part);
create index if not exists exercises_equipment_idx on public.exercises (equipment);
create index if not exists exercises_name_idx on public.exercises (lower(name));

-- The distinct values behind the library's filter chips.
--
-- Not a plain `select distinct` through PostgREST: that caps at db-max-rows (1,000 by default)
-- and the library is larger, so a facet appearing only in the tail would silently go missing.
-- security invoker keeps RLS in force, so a coach sees the global library plus their own.
create or replace function public.exercise_facets()
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
    select jsonb_build_object(
        'body_parts', coalesce(
            (select jsonb_agg(distinct body_part order by body_part)
             from public.exercises where body_part is not null), '[]'::jsonb),
        'equipment', coalesce(
            (select jsonb_agg(distinct equipment order by equipment)
             from public.exercises where equipment is not null), '[]'::jsonb)
    );
$$;

grant execute on function public.exercise_facets() to authenticated;
