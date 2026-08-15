alter table task add column if not exists unassigned_reason_code text null;

create table task_assignment_orchestration (
    task_id uuid primary key references task(id) on delete cascade,
    hotel_id uuid not null references hotel(id) on delete restrict,
    outcome text not null,
    reason_code text not null,
    selected_employee_id uuid null references employee(id) on delete set null,
    selected_user_id uuid null references app_user(id) on delete set null,
    candidate_count integer not null default 0 check(candidate_count >= 0),
    assignment_source text not null,
    rule_version text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_task_assignment_orchestration_hotel_outcome
    on task_assignment_orchestration(hotel_id, outcome, created_at desc);
