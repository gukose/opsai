create table scheduler_lock (
    job_name text primary key,
    locked_until timestamptz not null,
    locked_by text not null,
    acquired_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_scheduler_lock_locked_until
    on scheduler_lock (locked_until);
