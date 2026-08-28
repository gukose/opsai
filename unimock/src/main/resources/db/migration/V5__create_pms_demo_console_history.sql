create table unimock.pms_demo_console_event (
    id uuid primary key,
    event_id text not null unique,
    room_number text not null,
    destination_room_number text null,
    event_type text not null,
    occurred_at timestamptz not null,
    delivery_status text not null,
    message text null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index idx_pms_demo_console_event_recent on unimock.pms_demo_console_event(created_at desc);
