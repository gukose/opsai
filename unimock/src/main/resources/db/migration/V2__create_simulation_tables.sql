create table unimock.simulation (
    id uuid primary key,
    code text not null,
    name text not null,
    seed_path text not null,
    loaded_at timestamptz not null,
    active boolean not null default true,
    version integer not null default 0,
    created_at timestamptz not null default now(),
    created_by text null,
    updated_at timestamptz not null default now(),
    updated_by text null
);

create unique index uk_unimock_simulation_code
    on unimock.simulation (code);

create index idx_unimock_simulation_active
    on unimock.simulation (active);

create table unimock.simulation_document (
    id uuid primary key,
    simulation_id uuid not null references unimock.simulation (id) on delete cascade,
    document_path text not null,
    document_type text not null,
    payload_json jsonb not null,
    version integer not null default 0,
    created_at timestamptz not null default now(),
    created_by text null,
    updated_at timestamptz not null default now(),
    updated_by text null,
    constraint uk_unimock_simulation_document_path unique (simulation_id, document_path)
);

create index idx_unimock_simulation_document_simulation_id
    on unimock.simulation_document (simulation_id);

create index idx_unimock_simulation_document_type
    on unimock.simulation_document (document_type);
