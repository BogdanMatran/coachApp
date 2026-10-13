-- Public bucket mirroring the upstream exercise thumbnails and animations:
--   img/{image_path}   180x180 JPEG still
--   gif/{gif_path}     animated GIF
--
-- Public on purpose. The media is the same for every user, so signing each URL would buy no
-- privacy and would force a per-exercise round trip into six view models. Populate it with
-- scripts/sync-exercise-media.sh; the assets are not in this repository (see NOTICE.md).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('exercise-media', 'exercise-media', true, 10485760, array['image/jpeg', 'image/gif', 'image/webp'])
on conflict (id) do nothing;

-- Reads are open to signed-in and anonymous clients alike; writes stay with the service role,
-- which bypasses RLS, so no insert/update/delete policy is granted here.
create policy exercise_media_read on storage.objects for select to authenticated, anon
    using (bucket_id = 'exercise-media');
