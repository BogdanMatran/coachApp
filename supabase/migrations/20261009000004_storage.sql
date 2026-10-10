-- Private bucket for check-in progress photos: {client_id}/{checkin_id}/{pose}.jpg

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('checkin-photos', 'checkin-photos', false, 10485760, array['image/jpeg', 'image/png', 'image/webp', 'image/heic'])
on conflict (id) do nothing;

create policy checkin_photos_client_write on storage.objects for insert to authenticated
    with check (bucket_id = 'checkin-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy checkin_photos_client_update on storage.objects for update to authenticated
    using (bucket_id = 'checkin-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy checkin_photos_client_delete on storage.objects for delete to authenticated
    using (bucket_id = 'checkin-photos' and (storage.foldername(name))[1] = auth.uid()::text);

create policy checkin_photos_read on storage.objects for select to authenticated
    using (
        bucket_id = 'checkin-photos'
        and (
            (storage.foldername(name))[1] = auth.uid()::text
            or public.is_coach_of(((storage.foldername(name))[1])::uuid)
        )
    );
