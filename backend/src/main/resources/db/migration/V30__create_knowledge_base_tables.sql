create table knowledge_document (
    id uuid primary key,
    hotel_id uuid,
    title varchar(240) not null,
    category varchar(80) not null,
    source varchar(80) not null,
    language varchar(16) not null,
    original_content text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create table knowledge_chunk (
    id uuid primary key,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    chunk_order integer not null,
    heading varchar(240),
    text text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint knowledge_chunk_document_order_unique unique (document_id, chunk_order),
    constraint knowledge_chunk_order_non_negative check (chunk_order >= 0)
);

create table knowledge_metadata (
    document_id uuid primary key references knowledge_document(id) on delete cascade,
    tags text[] not null default '{}',
    attributes jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_knowledge_document_hotel on knowledge_document(hotel_id);
create index idx_knowledge_document_category on knowledge_document(category);
create index idx_knowledge_document_source on knowledge_document(source);
create index idx_knowledge_document_title_lower on knowledge_document(lower(title));
create index idx_knowledge_chunk_document_order on knowledge_chunk(document_id, chunk_order);
create index idx_knowledge_chunk_text_lower on knowledge_chunk(lower(text));
create index idx_knowledge_metadata_tags on knowledge_metadata using gin(tags);

insert into permission (id, version, created_at, created_by, updated_at, updated_by, code, name, description)
values
    ('00000000-0000-0000-0000-000000000154', 0, now(), 'V30', now(), 'V30', 'KNOWLEDGE_OPERATIONS', 'Operate internal knowledge base', 'Allows importing, searching, re-chunking, and deleting internal knowledge documents')
on conflict (code) do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from role r
join permission p on p.code = 'KNOWLEDGE_OPERATIONS'
where r.code = 'ADMIN'
on conflict do nothing;
