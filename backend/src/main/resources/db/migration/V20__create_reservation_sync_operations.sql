create table reservation_sync_run (
    id uuid primary key,
    provider_id text not null,
    property_scope_hash text not null,
    property_scope_label text not null,
    requested_start_date date not null,
    requested_end_date date not null,
    trigger_type text not null,
    run_status text not null,
    started_at timestamptz not null,
    completed_at timestamptz null,
    fetched_count integer not null default 0,
    created_count integer not null default 0,
    updated_count integer not null default 0,
    unchanged_count integer not null default 0,
    stale_count integer not null default 0,
    conflict_count integer not null default 0,
    bounded_page_count integer not null default 0,
    failure_category text null,
    actor_user_id uuid null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint chk_reservation_sync_run_dates check (requested_end_date > requested_start_date),
    constraint chk_reservation_sync_run_trigger check (trigger_type in ('MANUAL', 'SCHEDULED', 'WEBHOOK', 'RECOVERY')),
    constraint chk_reservation_sync_run_status check (
        run_status in ('REQUESTED', 'RUNNING', 'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'REJECTED')
    ),
    constraint chk_reservation_sync_run_counts check (
        fetched_count >= 0 and created_count >= 0 and updated_count >= 0 and
        unchanged_count >= 0 and stale_count >= 0 and conflict_count >= 0 and bounded_page_count >= 0
    ),
    constraint chk_reservation_sync_run_version check (version >= 0)
);

create table reservation_sync_run_lock (
    id uuid primary key,
    provider_id text not null,
    property_scope_hash text not null,
    requested_start_date date not null,
    requested_end_date date not null,
    run_id uuid not null,
    locked_until timestamptz not null,
    created_at timestamptz not null,
    constraint fk_reservation_sync_run_lock_run foreign key (run_id)
        references reservation_sync_run (id) on delete cascade,
    constraint chk_reservation_sync_run_lock_dates check (requested_end_date > requested_start_date)
);

create table reservation_sync_schedule_state (
    schedule_id text primary key,
    paused boolean not null default false,
    paused_at timestamptz null,
    resumed_at timestamptz null,
    last_attempted_at timestamptz null,
    last_successful_at timestamptz null,
    last_failure_category text null,
    last_run_id uuid null,
    last_processed_count integer not null default 0,
    updated_at timestamptz not null,
    constraint fk_reservation_sync_schedule_state_run foreign key (last_run_id)
        references reservation_sync_run (id) on delete set null
);

create table reservation_webhook_inbox (
    id uuid primary key,
    provider_id text not null,
    provider_event_id text not null,
    event_type text not null,
    property_scope_hash text not null,
    property_scope_label text not null,
    external_entity_hash text null,
    provider_event_timestamp timestamptz null,
    received_at timestamptz not null,
    processing_status text not null,
    failure_category text null,
    attempt_count integer not null default 0,
    next_attempt_at timestamptz null,
    processing_started_at timestamptz null,
    completed_at timestamptz null,
    payload_fingerprint text not null,
    safe_metadata jsonb not null default '{}'::jsonb,
    sync_run_id uuid null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_reservation_webhook_inbox_event unique (provider_id, provider_event_id),
    constraint fk_reservation_webhook_inbox_sync_run foreign key (sync_run_id)
        references reservation_sync_run (id) on delete set null,
    constraint chk_reservation_webhook_inbox_status check (
        processing_status in ('RECEIVED', 'VERIFIED', 'REJECTED', 'DUPLICATE', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'IGNORED', 'DEAD_LETTER')
    ),
    constraint chk_reservation_webhook_inbox_attempts check (attempt_count >= 0),
    constraint chk_reservation_webhook_inbox_version check (version >= 0)
);

create index idx_reservation_sync_run_provider_status_started
    on reservation_sync_run (provider_id, run_status, started_at desc);

create index idx_reservation_sync_run_property_window
    on reservation_sync_run (property_scope_hash, requested_start_date, requested_end_date);

create index idx_reservation_sync_run_lock_overlap
    on reservation_sync_run_lock (provider_id, property_scope_hash, requested_start_date, requested_end_date, locked_until);

create index idx_reservation_webhook_inbox_processing
    on reservation_webhook_inbox (processing_status, next_attempt_at, received_at);

create index idx_reservation_webhook_inbox_provider_received
    on reservation_webhook_inbox (provider_id, received_at desc);

insert into permission (id, version, created_at, created_by, updated_at, updated_by, code, name, description)
values
    ('00000000-0000-0000-0000-000000000161', 0, now(), 'V20', now(), 'V20', 'RESERVATION_SYNC_OPERATIONS', 'Operate reservation synchronization', 'Allows running and inspecting sanitized reservation synchronization operations')
on conflict (code) do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from role r
join permission p on p.code = 'RESERVATION_SYNC_OPERATIONS'
where r.code = 'ADMIN'
on conflict do nothing;
