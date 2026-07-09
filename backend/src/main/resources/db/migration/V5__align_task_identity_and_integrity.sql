alter table task
    add column hotel_id_uuid uuid;

update task t
set hotel_id_uuid =
    case
        when t.hotel_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-7][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then t.hotel_id::uuid
        else coalesce(
            (select h.id from hotel h where h.code = t.hotel_id limit 1),
            (select h.id from hotel h where h.code = 'hotel-opai-demo' limit 1),
            (select h.id from hotel h order by h.created_at asc limit 1)
        )
    end;

alter table task
    alter column hotel_id_uuid set not null;

alter table task
    drop column hotel_id;

alter table task
    rename column hotel_id_uuid to hotel_id;

alter table task
    add constraint fk_task_hotel foreign key (hotel_id) references hotel (id) on delete restrict;

create index idx_task_hotel_id on task (hotel_id);

alter table task_state_history
    add column task_id_uuid uuid;

update task_state_history tsh
set task_id_uuid =
    case
        when tsh.task_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-7][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then tsh.task_id::uuid
        else (
            select t.id
            from task t
            where t.task_key = tsh.task_id
            limit 1
        )
    end;

alter table task_state_history
    alter column task_id_uuid set not null;

alter table task_state_history
    drop column task_id;

alter table task_state_history
    rename column task_id_uuid to task_id;

alter table task_state_history
    add column hotel_id_uuid uuid;

update task_state_history tsh
set hotel_id_uuid =
    case
        when tsh.hotel_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-7][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then tsh.hotel_id::uuid
        else coalesce(
            (select h.id from hotel h where h.code = tsh.hotel_id limit 1),
            (select h.id from hotel h where h.code = 'hotel-opai-demo' limit 1),
            (select h.id from hotel h order by h.created_at asc limit 1)
        )
    end;

alter table task_state_history
    alter column hotel_id_uuid set not null;

alter table task_state_history
    drop column hotel_id;

alter table task_state_history
    rename column hotel_id_uuid to hotel_id;

alter table task_state_history
    add constraint fk_task_state_history_task foreign key (task_id) references task (id) on delete cascade;

alter table task_state_history
    add constraint fk_task_state_history_hotel foreign key (hotel_id) references hotel (id) on delete restrict;

create index idx_task_state_history_task_id on task_state_history (task_id);
create index idx_task_state_history_hotel_id on task_state_history (hotel_id);

alter table task_log
    add column task_id_uuid uuid;

update task_log tl
set task_id_uuid =
    case
        when tl.task_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-7][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then tl.task_id::uuid
        else (
            select t.id
            from task t
            where t.task_key = tl.task_id
            limit 1
        )
    end;

alter table task_log
    alter column task_id_uuid set not null;

alter table task_log
    drop column task_id;

alter table task_log
    rename column task_id_uuid to task_id;

alter table task_log
    add column hotel_id_uuid uuid;

update task_log tl
set hotel_id_uuid =
    case
        when tl.hotel_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-7][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then tl.hotel_id::uuid
        else coalesce(
            (select h.id from hotel h where h.code = tl.hotel_id limit 1),
            (select h.id from hotel h where h.code = 'hotel-opai-demo' limit 1),
            (select h.id from hotel h order by h.created_at asc limit 1)
        )
    end;

alter table task_log
    alter column hotel_id_uuid set not null;

alter table task_log
    drop column hotel_id;

alter table task_log
    rename column hotel_id_uuid to hotel_id;

alter table task_log
    add constraint fk_task_log_task foreign key (task_id) references task (id) on delete cascade;

alter table task_log
    add constraint fk_task_log_hotel foreign key (hotel_id) references hotel (id) on delete restrict;

create index idx_task_log_task_id on task_log (task_id);
create index idx_task_log_hotel_id on task_log (hotel_id);

alter table task
    drop column task_key;
