create table demo_bootstrap_marker (
    hotel_id uuid not null references hotel(id) on delete cascade,
    dataset_version text not null,
    applied_at timestamptz not null,
    primary key (hotel_id, dataset_version)
);
