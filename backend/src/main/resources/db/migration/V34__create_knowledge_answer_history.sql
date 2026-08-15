create table knowledge_answer_history (
    id uuid primary key,
    hotel_id uuid,
    provider_id varchar(80) not null,
    model_id varchar(160) not null,
    prompt_template_id varchar(160) not null,
    prompt_version varchar(160) not null,
    retrieval_mode varchar(40) not null,
    context_schema_version varchar(80) not null,
    status varchar(80) not null,
    confidence varchar(40),
    answer_text text,
    citation_refs jsonb not null default '[]'::jsonb,
    request_fingerprint varchar(64) not null,
    failure_category varchar(120),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_knowledge_answer_history_hotel_created on knowledge_answer_history(hotel_id, created_at desc);
create index idx_knowledge_answer_history_fingerprint on knowledge_answer_history(hotel_id, request_fingerprint, created_at desc);
create index idx_knowledge_answer_history_status on knowledge_answer_history(status);
