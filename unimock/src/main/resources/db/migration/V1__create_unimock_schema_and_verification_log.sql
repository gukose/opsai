create schema if not exists unimock;

create table unimock.pms_mock_verification_log (
    id uuid primary key,
    simulation_id text not null,
    entity_type text not null,
    entity_id text null,
    operation text not null,
    request_payload_json jsonb null,
    response_payload_json jsonb null,
    status text not null,
    source_system text not null,
    destination_system text not null,
    http_status integer null,
    duration_ms bigint null,
    retry_count integer not null default 0,
    correlation_id text null,
    created_at timestamptz not null default now()
);

create index idx_pms_mock_verification_log_simulation_id
    on unimock.pms_mock_verification_log (simulation_id);

create index idx_pms_mock_verification_log_entity_type
    on unimock.pms_mock_verification_log (entity_type);

create index idx_pms_mock_verification_log_entity_id
    on unimock.pms_mock_verification_log (entity_id);

create index idx_pms_mock_verification_log_correlation_id
    on unimock.pms_mock_verification_log (correlation_id);
