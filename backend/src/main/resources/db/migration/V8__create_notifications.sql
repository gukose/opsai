create table notifications (
    id uuid primary key,
    hotel_id uuid not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    recipient_user_id uuid null,
    recipient_role_code text null,
    type text not null,
    status text not null,
    title text not null,
    body text not null,
    source_task_id uuid null,
    read_at timestamptz null,
    constraint fk_notifications_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint fk_notifications_task foreign key (source_task_id) references task (id) on delete cascade,
    constraint chk_notifications_single_recipient check (
        (recipient_user_id is not null and recipient_role_code is null)
        or (recipient_user_id is null and recipient_role_code is not null)
    )
);

create index idx_notifications_hotel_created_at on notifications (hotel_id, created_at desc);
create index idx_notifications_recipient_user on notifications (hotel_id, recipient_user_id, created_at desc);
create index idx_notifications_recipient_role on notifications (hotel_id, recipient_role_code, created_at desc);
create index idx_notifications_status on notifications (hotel_id, status, created_at desc);
create index idx_notifications_source_task on notifications (source_task_id);

create unique index uq_notifications_task_created_once
    on notifications (source_task_id, type)
    where source_task_id is not null and type = 'TASK_CREATED';
