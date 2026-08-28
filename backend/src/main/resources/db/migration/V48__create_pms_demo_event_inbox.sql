create table pms_demo_event_inbox (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    provider_event_id text not null,
    event_type text not null,
    room_number text not null,
    destination_room_number text null,
    status text not null check(status in ('PROCESSING','PROCESSED','FAILED')),
    result_type text null,
    result_id uuid null,
    occurred_at timestamptz not null,
    processed_at timestamptz null,
    created_at timestamptz not null,
    unique(hotel_id, provider_event_id)
);
create index idx_pms_demo_event_history on pms_demo_event_inbox(hotel_id, created_at desc);
