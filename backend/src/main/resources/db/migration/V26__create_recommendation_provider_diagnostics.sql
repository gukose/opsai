create table recommendation_provider_diagnostic (
    id uuid primary key,
    provider_id varchar(120) not null,
    diagnostic_type varchar(80) not null,
    trigger_type varchar(80) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    outcome varchar(80) not null,
    failure_category varchar(120),
    latency_band varchar(40) not null,
    retry_count integer not null default 0,
    response_validation_outcome varchar(80) not null,
    prompt_version varchar(160) not null,
    model_identifier varchar(160),
    environment_class varchar(80) not null,
    endpoint_classification varchar(80) not null,
    created_at timestamptz not null
);

create index idx_recommendation_provider_diagnostic_provider_started
    on recommendation_provider_diagnostic (provider_id, started_at desc);

create index idx_recommendation_provider_diagnostic_outcome_started
    on recommendation_provider_diagnostic (outcome, started_at desc);
