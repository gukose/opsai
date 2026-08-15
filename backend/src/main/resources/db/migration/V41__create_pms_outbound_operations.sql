create table pms_outbound_operation (
    id uuid primary key, hotel_id uuid not null references hotel(id), operation_type text not null,
    resource_reference text not null, provider_id text not null, idempotency_key text not null,
    status text not null, provider_reference text null, failure_category text null,
    attempt_count integer not null default 0, requested_at timestamptz not null, completed_at timestamptz null,
    unique(hotel_id,idempotency_key)
);
