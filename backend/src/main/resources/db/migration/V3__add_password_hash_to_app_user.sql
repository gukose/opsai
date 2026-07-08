alter table app_user
    add column password_hash text not null default '';

alter table app_user
    alter column password_hash drop default;
