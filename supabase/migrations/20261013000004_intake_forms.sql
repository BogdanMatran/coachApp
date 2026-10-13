-- Onboarding (intake) forms. The coach's questionnaire lives next to the weekly check-in
-- questions in checkin_templates, told apart by kind; a client answers it once after joining.

create type public.form_kind as enum ('checkin', 'intake');

alter table public.checkin_templates
    add column kind public.form_kind not null default 'checkin',
    drop constraint checkin_templates_coach_id_key,
    add constraint checkin_templates_coach_kind_key unique (coach_id, kind);

create table public.intake_responses (
    client_id    uuid primary key references public.profiles (id) on delete cascade,
    coach_id     uuid not null references public.profiles (id) on delete cascade,
    answers      jsonb not null default '{}'::jsonb,
    submitted_at timestamptz not null default now()
);
create index intake_responses_coach_idx on public.intake_responses (coach_id);

alter table public.intake_responses enable row level security;

create policy intake_responses_client on public.intake_responses for all to authenticated
    using (client_id = auth.uid())
    with check (client_id = auth.uid() and coach_id = public.my_coach_id());
create policy intake_responses_coach on public.intake_responses for select to authenticated
    using (public.is_coach_of(client_id));

-- True when the caller is a client whose coach has an onboarding form they haven't answered.
-- Answered for the current coach only: a client who switches coaches fills in the new one's.
create or replace function public.intake_pending()
returns boolean
language sql stable security definer set search_path = public
as $$
    select exists (
        select 1
        from public.profiles p
        join public.checkin_templates t on t.coach_id = p.coach_id and t.kind = 'intake'
        where p.id = auth.uid()
          and p.role = 'client'
          and jsonb_array_length(t.questions) > 0
          and not exists (
              select 1 from public.intake_responses r
              where r.client_id = p.id and r.coach_id = p.coach_id)
    )
$$;

grant execute on function public.intake_pending() to authenticated;

-- The export gains the onboarding answers.
-- Kept callable: it runs as the caller, so RLS still limits it to their own rows.
alter function public.export_my_data() rename to export_base_data;

create or replace function public.export_my_data()
returns jsonb
language sql stable security invoker set search_path = public
as $$
    select public.export_base_data() || jsonb_build_object(
        'intake_response', (select to_jsonb(r) from intake_responses r where r.client_id = auth.uid()))
$$;

grant execute on function public.export_my_data() to authenticated;
