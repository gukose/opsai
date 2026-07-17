create table operational_outbox (
    id uuid primary key,
    event_type text not null,
    aggregate_type text not null,
    aggregate_id uuid not null,
    hotel_id uuid not null,
    payload_json jsonb not null,
    status text not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz not null,
    locked_at timestamptz null,
    locked_by text null,
    processed_at timestamptz null,
    last_failure_code text null,
    last_failure_message text null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_operational_outbox_event_type check (event_type = 'TASK_CREATED'),
    constraint chk_operational_outbox_aggregate_type check (aggregate_type = 'TASK'),
    constraint chk_operational_outbox_status check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    constraint chk_operational_outbox_attempt_count check (attempt_count >= 0),
    constraint chk_operational_outbox_completed_processed_at check (status <> 'COMPLETED' or processed_at is not null)
);

create unique index uq_operational_outbox_event_aggregate
    on operational_outbox (event_type, aggregate_type, aggregate_id);

create index idx_operational_outbox_status_next_attempt
    on operational_outbox (status, next_attempt_at);

create index idx_operational_outbox_aggregate
    on operational_outbox (aggregate_type, aggregate_id);

create index idx_operational_outbox_hotel_created_at
    on operational_outbox (hotel_id, created_at desc);

create index idx_operational_outbox_status_locked_at
    on operational_outbox (status, locked_at);

alter table notifications
    add column source_event_id uuid null;

create unique index uq_notifications_source_event
    on notifications (source_event_id)
    where source_event_id is not null;
