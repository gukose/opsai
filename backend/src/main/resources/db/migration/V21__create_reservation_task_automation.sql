create table reservation_task_automation_execution (
    id uuid primary key,
    outbox_event_id uuid not null,
    reservation_id uuid not null,
    rule_id text not null,
    rule_version integer not null,
    trigger_event_type text not null,
    deduplication_key text not null,
    outcome text not null,
    created_task_id uuid null,
    failure_category text null,
    skip_reason text null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz null,
    version bigint not null default 0,
    constraint uq_reservation_task_automation_dedupe unique (deduplication_key),
    constraint fk_reservation_task_automation_task foreign key (created_task_id)
        references task (id) on delete set null,
    constraint chk_reservation_task_automation_outcome check (
        outcome in ('CREATED', 'ALREADY_EXISTS', 'SKIPPED', 'NOT_APPLICABLE', 'FAILED', 'DEAD_LETTER')
    ),
    constraint chk_reservation_task_automation_attempts check (attempt_count >= 0),
    constraint chk_reservation_task_automation_version check (version >= 0)
);

create index idx_reservation_task_automation_outbox
    on reservation_task_automation_execution (outbox_event_id);

create index idx_reservation_task_automation_reservation
    on reservation_task_automation_execution (reservation_id, created_at desc);

create index idx_reservation_task_automation_outcome
    on reservation_task_automation_execution (outcome, next_attempt_at, created_at);
