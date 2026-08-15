create table knowledge_embedding_provider_diagnostic (
    id uuid primary key,
    provider_id varchar(80) not null,
    model_id varchar(160) not null,
    diagnostic_type varchar(80) not null,
    outcome varchar(40) not null,
    readiness varchar(40) not null,
    failure_category varchar(80),
    latency_band varchar(40) not null,
    batch_size integer not null,
    generated_at timestamptz not null,
    created_at timestamptz not null,
    constraint knowledge_embedding_diag_outcome_check check (outcome in ('SUCCEEDED', 'FAILED', 'SKIPPED'))
);

create index idx_knowledge_embedding_diag_provider_created
    on knowledge_embedding_provider_diagnostic(provider_id, created_at desc);

create table knowledge_embedding_schedule_state (
    schedule_id text primary key,
    paused boolean not null default false,
    paused_at timestamptz,
    resumed_at timestamptz,
    last_attempted_at timestamptz,
    last_successful_at timestamptz,
    last_embedded_count integer not null default 0,
    last_failure_category varchar(80),
    updated_at timestamptz not null
);
