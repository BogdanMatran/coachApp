-- A coach's own demo video for any exercise, including the shared library ones. It overrides the
-- library's image/GIF for that coach's clients only. Either an upload in the private
-- exercise-videos bucket ({coach_id}/{exercise_id}.{ext}) or a link (YouTube, Vimeo, a file URL).

create table public.exercise_videos (
    coach_id     uuid not null references public.profiles (id) on delete cascade,
    exercise_id  uuid not null references public.exercises (id) on delete cascade,
    storage_path text,
    external_url text,
    updated_at   timestamptz not null default now(),
    primary key (coach_id, exercise_id),
    check (num_nonnulls(storage_path, external_url) = 1)
);
create index exercise_videos_exercise_idx on public.exercise_videos (exercise_id);

alter table public.exercise_videos enable row level security;

create policy exercise_videos_coach on public.exercise_videos for all to authenticated
    using (coach_id = auth.uid())
    with check (coach_id = auth.uid() and public.auth_role() = 'coach');
create policy exercise_videos_client on public.exercise_videos for select to authenticated
    using (coach_id = public.my_coach_id());

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('exercise-videos', 'exercise-videos', false, 52428800, array['video/mp4', 'video/quicktime', 'video/webm'])
on conflict (id) do nothing;

create policy exercise_videos_write on storage.objects for insert to authenticated
    with check (bucket_id = 'exercise-videos' and (storage.foldername(name))[1] = auth.uid()::text
                and public.auth_role() = 'coach');
create policy exercise_videos_update on storage.objects for update to authenticated
    using (bucket_id = 'exercise-videos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy exercise_videos_delete on storage.objects for delete to authenticated
    using (bucket_id = 'exercise-videos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy exercise_videos_read on storage.objects for select to authenticated
    using (bucket_id = 'exercise-videos'
           and (storage.foldername(name))[1] in (auth.uid()::text, public.my_coach_id()::text));
