alter table employee add column if not exists primary_role_code text null;
alter table employee add column if not exists supervisor_employee_id uuid null references employee(id) on delete set null;
alter table employee add column if not exists home_area text null;
alter table employee add column if not exists languages text[] not null default '{}';
alter table employee add column if not exists operational_status text not null default 'OFFLINE';
alter table employee_skill add column if not exists skill_level integer not null default 1 check (skill_level between 1 and 5);

create table workforce_shift (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    employee_id uuid not null references employee(id) on delete cascade,
    planned_start timestamptz not null,
    planned_end timestamptz not null,
    actual_start timestamptz null,
    actual_end timestamptz null,
    status text not null,
    home_area text null,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (planned_end > planned_start)
);
create index idx_workforce_shift_hotel_employee on workforce_shift(hotel_id, employee_id, planned_start desc);

create table housekeeping_workflow (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    task_id uuid not null references task(id) on delete restrict,
    workflow_type text not null,
    room_number text not null,
    status text not null,
    inspection_required boolean not null,
    accepted_at timestamptz null,
    started_at timestamptz null,
    paused_at timestamptz null,
    resumed_at timestamptz null,
    cleaning_completed_at timestamptz null,
    inspection_started_at timestamptz null,
    inspection_completed_at timestamptz null,
    closed_at timestamptz null,
    working_seconds bigint not null default 0,
    paused_seconds bigint not null default 0,
    active_segment_started_at timestamptz null,
    pause_segment_started_at timestamptz null,
    source_reference text null,
    idempotency_key text not null,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique(hotel_id, task_id),
    unique(hotel_id, idempotency_key)
);
create index idx_housekeeping_hotel_status on housekeeping_workflow(hotel_id, status, created_at);
create index idx_housekeeping_room on housekeeping_workflow(hotel_id, room_number, created_at desc);

create table housekeeping_checklist_template (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete cascade,
    workflow_type text not null,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create table housekeeping_checklist_item (
    id uuid primary key,
    template_id uuid not null references housekeeping_checklist_template(id) on delete cascade,
    code text not null,
    label text not null,
    required boolean not null default true,
    display_order integer not null,
    unique(template_id, code)
);
create table housekeeping_inspection (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    workflow_id uuid not null references housekeeping_workflow(id) on delete cascade,
    inspector_user_id uuid not null references app_user(id) on delete restrict,
    attempt integer not null,
    result text not null,
    rejection_reason text null,
    quality_score integer null check (quality_score between 0 and 100),
    started_at timestamptz not null,
    completed_at timestamptz not null,
    created_at timestamptz not null,
    unique(workflow_id, attempt)
);
create table housekeeping_inspection_answer (
    inspection_id uuid not null references housekeeping_inspection(id) on delete cascade,
    checklist_item_id uuid not null references housekeeping_checklist_item(id) on delete restrict,
    passed boolean not null,
    note text null,
    primary key(inspection_id, checklist_item_id)
);
create table task_interruption (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    employee_id uuid not null references employee(id) on delete restrict,
    paused_task_id uuid not null references task(id) on delete restrict,
    interrupting_task_id uuid not null references task(id) on delete restrict,
    reason text not null,
    status text not null,
    idempotency_key text not null,
    paused_at timestamptz not null,
    resumed_at timestamptz null,
    created_at timestamptz not null,
    unique(hotel_id, idempotency_key),
    check(paused_task_id <> interrupting_task_id)
);
