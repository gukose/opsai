create table knowledge_retrieval_evaluation_run (
    id uuid primary key,
    name varchar(120) not null,
    status varchar(40) not null,
    case_count integer not null,
    k_value integer not null,
    modes text[] not null default '{}',
    started_at timestamptz not null,
    completed_at timestamptz not null,
    failure_category varchar(80),
    created_at timestamptz not null,
    constraint knowledge_retrieval_eval_status_check check (status in ('SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED')),
    constraint knowledge_retrieval_eval_case_count_positive check (case_count > 0),
    constraint knowledge_retrieval_eval_k_positive check (k_value > 0)
);

create table knowledge_retrieval_evaluation_metric (
    run_id uuid not null references knowledge_retrieval_evaluation_run(id) on delete cascade,
    mode varchar(40) not null,
    precision_at_k double precision not null,
    recall_at_k double precision not null,
    mean_reciprocal_rank double precision not null,
    ndcg double precision not null,
    hit_rate double precision not null,
    average_latency_millis bigint not null,
    average_retrieved_chunks double precision not null,
    score_band varchar(40) not null,
    primary key (run_id, mode)
);

create index idx_knowledge_retrieval_eval_created
    on knowledge_retrieval_evaluation_run(created_at desc);
