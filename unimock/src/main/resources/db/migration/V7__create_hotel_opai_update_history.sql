create table if not exists unimock.hotel_opai_room_update (
    id uuid primary key,
    room_number text not null,
    status text not null,
    source text not null,
    received_at timestamptz not null
);
create index if not exists idx_hotel_opai_room_update_recent on unimock.hotel_opai_room_update(received_at desc);
