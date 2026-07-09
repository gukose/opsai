alter table task
    add column if not exists room_number text null;

create index if not exists idx_task_room_number on task (room_number);
