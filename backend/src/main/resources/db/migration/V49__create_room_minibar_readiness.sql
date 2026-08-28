create table room_minibar_readiness (
    hotel_id uuid not null references hotel(id) on delete cascade,
    room_number text not null,
    status text not null check (status in ('PENDING','COMPLETED')),
    completed_at timestamptz null,
    updated_at timestamptz not null,
    primary key (hotel_id, room_number)
);
