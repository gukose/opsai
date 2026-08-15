create table knowledge_chunk_embedding (
    chunk_id uuid not null references knowledge_chunk(id) on delete cascade,
    provider_id varchar(80) not null,
    model_id varchar(160) not null,
    embedding_dimension integer not null,
    embedding_vector double precision[],
    content_fingerprint varchar(64) not null,
    generated_at timestamptz,
    status varchar(40) not null,
    failure_category varchar(80),
    attempt_count integer not null default 0,
    next_attempt_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint knowledge_chunk_embedding_pk primary key (chunk_id, provider_id, model_id),
    constraint knowledge_chunk_embedding_dimension_positive check (embedding_dimension > 0),
    constraint knowledge_chunk_embedding_status_check check (status in ('READY', 'FAILED', 'STALE', 'SKIPPED'))
);

create index idx_knowledge_chunk_embedding_status on knowledge_chunk_embedding(status);
create index idx_knowledge_chunk_embedding_provider_model on knowledge_chunk_embedding(provider_id, model_id);
create index idx_knowledge_chunk_embedding_fingerprint on knowledge_chunk_embedding(content_fingerprint);
