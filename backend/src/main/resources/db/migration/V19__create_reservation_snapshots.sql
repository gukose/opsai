create table reservation_snapshot (
    id uuid primary key,
    provider_id text not null,
    external_reservation_reference text not null,
    property_reference text not null,
    reservation_status text not null,
    stay_status text not null,
    arrival_date date not null,
    departure_date date not null,
    occupancy_adults integer not null,
    occupancy_children integer not null default 0,
    source text not null,
    operational_notes text null,
    pms_source_updated_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_reservation_snapshot_provider_external_property unique (
        provider_id,
        external_reservation_reference,
        property_reference
    ),
    constraint chk_reservation_snapshot_status check (
        reservation_status in ('PENDING', 'CONFIRMED', 'CANCELLED', 'NO_SHOW')
    ),
    constraint chk_reservation_snapshot_stay_status check (
        stay_status in ('NOT_ARRIVED', 'IN_HOUSE', 'CHECKED_OUT')
    ),
    constraint chk_reservation_snapshot_dates check (departure_date > arrival_date),
    constraint chk_reservation_snapshot_occupancy check (
        occupancy_adults >= 0 and occupancy_children >= 0 and occupancy_adults + occupancy_children > 0
    ),
    constraint chk_reservation_snapshot_version check (version >= 0)
);

create table reservation_guest_snapshot (
    reservation_id uuid not null,
    guest_id text not null,
    guest_role text not null,
    guest_order integer not null,
    created_at timestamptz not null,
    constraint pk_reservation_guest_snapshot primary key (reservation_id, guest_id),
    constraint fk_reservation_guest_snapshot_reservation foreign key (reservation_id)
        references reservation_snapshot (id) on delete cascade,
    constraint chk_reservation_guest_snapshot_role check (guest_role in ('PRIMARY', 'ACCOMPANYING')),
    constraint chk_reservation_guest_snapshot_order check (guest_order >= 0)
);

create table reservation_room_assignment_snapshot (
    reservation_id uuid primary key,
    room_id text not null,
    assignment_arrival_date date not null,
    assignment_departure_date date not null,
    created_at timestamptz not null,
    constraint fk_reservation_room_assignment_snapshot_reservation foreign key (reservation_id)
        references reservation_snapshot (id) on delete cascade,
    constraint chk_reservation_room_assignment_dates check (assignment_departure_date > assignment_arrival_date)
);

create table reservation_sync_state (
    provider_id text not null,
    property_reference text not null,
    sync_status text not null,
    sync_cursor text null,
    last_attempted_at timestamptz null,
    last_successful_at timestamptz null,
    last_failure_category text null,
    source_data_timestamp timestamptz null,
    window_start date null,
    window_end date null,
    fetched_count integer not null default 0,
    created_count integer not null default 0,
    updated_count integer not null default 0,
    unchanged_count integer not null default 0,
    stale_count integer not null default 0,
    conflict_count integer not null default 0,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint pk_reservation_sync_state primary key (provider_id, property_reference),
    constraint chk_reservation_sync_state_status check (sync_status in ('NEVER_SYNCED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    constraint chk_reservation_sync_state_counts check (
        fetched_count >= 0 and created_count >= 0 and updated_count >= 0 and
        unchanged_count >= 0 and stale_count >= 0 and conflict_count >= 0
    )
);

alter table operational_outbox
    drop constraint chk_operational_outbox_event_type,
    add constraint chk_operational_outbox_event_type check (
        event_type in (
            'TASK_CREATED',
            'RESERVATION_IMPORTED',
            'RESERVATION_UPDATED',
            'RESERVATION_STATUS_CHANGED',
            'GUEST_CHECKED_IN',
            'GUEST_CHECKED_OUT',
            'RESERVATION_CANCELLED',
            'RESERVATION_MARKED_NO_SHOW',
            'ROOM_ASSIGNMENT_CHANGED'
        )
    );

alter table operational_outbox
    drop constraint chk_operational_outbox_aggregate_type,
    add constraint chk_operational_outbox_aggregate_type check (aggregate_type in ('TASK', 'RESERVATION'));

create index idx_reservation_snapshot_property_dates
    on reservation_snapshot (property_reference, arrival_date, departure_date);

create index idx_reservation_snapshot_provider_updated
    on reservation_snapshot (provider_id, pms_source_updated_at desc);
