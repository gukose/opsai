alter table task
    alter column hotel_id type uuid using hotel_id::uuid;

alter table task
    drop column task_key;

alter table task
    add constraint fk_task_hotel foreign key (hotel_id) references hotel (id) on delete restrict;

alter table task_state_history
    alter column task_id type uuid using task_id::uuid;

alter table task_state_history
    alter column hotel_id type uuid using hotel_id::uuid;

alter table task_state_history
    add constraint fk_task_state_history_task foreign key (task_id) references task (id) on delete cascade;

alter table task_state_history
    add constraint fk_task_state_history_hotel foreign key (hotel_id) references hotel (id) on delete restrict;

alter table task_log
    alter column task_id type uuid using task_id::uuid;

alter table task_log
    alter column hotel_id type uuid using hotel_id::uuid;

alter table task_log
    add constraint fk_task_log_task foreign key (task_id) references task (id) on delete cascade;

alter table task_log
    add constraint fk_task_log_hotel foreign key (hotel_id) references hotel (id) on delete restrict;
