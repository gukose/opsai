create table knowledge_answer_inflight_scope (
    hotel_id uuid not null,
    actor_user_id uuid not null,
    updated_at timestamptz not null,
    primary key (hotel_id, actor_user_id)
);

create table knowledge_answer_request_lifecycle (
    request_id uuid primary key,
    answer_id uuid references knowledge_answer_history(id) on delete set null,
    original_request_id uuid,
    hotel_id uuid,
    actor_user_id uuid,
    provider_id varchar(80) not null,
    model_id varchar(160) not null,
    retrieval_mode varchar(40) not null,
    request_fingerprint varchar(64) not null,
    status varchar(80) not null,
    requested_at timestamptz not null,
    completed_at timestamptz,
    updated_at timestamptz not null,
    failure_category varchar(120),
    citation_count_band varchar(40) not null default 'unknown',
    latency_band varchar(40) not null default 'unknown'
);

create index idx_knowledge_answer_request_scope_status
    on knowledge_answer_request_lifecycle(hotel_id, actor_user_id, status, updated_at desc);

create index idx_knowledge_answer_request_hotel_requested
    on knowledge_answer_request_lifecycle(hotel_id, requested_at desc);

create index idx_knowledge_answer_request_answer
    on knowledge_answer_request_lifecycle(answer_id);

create index idx_knowledge_answer_request_original
    on knowledge_answer_request_lifecycle(original_request_id);
