create table hotel (
    id uuid primary key,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    code text not null,
    name text not null,
    status text not null,
    constraint uk_hotel_code unique (code)
);

create index idx_hotel_code on hotel (code);

create table permission (
    id uuid primary key,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    code text not null,
    name text not null,
    description text null,
    constraint uk_permission_code unique (code)
);

create index idx_permission_code on permission (code);

create table department (
    id uuid primary key,
    hotel_id uuid not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    code text not null,
    name text not null,
    is_active boolean not null,
    constraint fk_department_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint uk_department_hotel_code unique (hotel_id, code)
);

create index idx_department_hotel_id on department (hotel_id);

create table skill (
    id uuid primary key,
    hotel_id uuid not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    code text not null,
    name text not null,
    description text null,
    is_active boolean not null,
    constraint fk_skill_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint uk_skill_hotel_code unique (hotel_id, code)
);

create index idx_skill_hotel_id on skill (hotel_id);

create table role (
    id uuid primary key,
    hotel_id uuid not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    code text not null,
    name text not null,
    description text null,
    constraint fk_role_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint uk_role_hotel_code unique (hotel_id, code)
);

create index idx_role_hotel_id on role (hotel_id);

create table role_permission (
    role_id uuid not null,
    permission_id uuid not null,
    constraint pk_role_permission primary key (role_id, permission_id),
    constraint fk_role_permission_role foreign key (role_id) references role (id) on delete cascade,
    constraint fk_role_permission_permission foreign key (permission_id) references permission (id) on delete restrict
);

create index idx_role_permission_permission_id on role_permission (permission_id);

create table app_user (
    id uuid primary key,
    hotel_id uuid not null,
    employee_id uuid null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    email text not null,
    display_name text not null,
    status text not null,
    constraint fk_app_user_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint uk_app_user_hotel_email unique (hotel_id, email),
    constraint uk_app_user_employee_id unique (employee_id)
);

create index idx_app_user_hotel_id on app_user (hotel_id);
create index idx_app_user_employee_id on app_user (employee_id);
create index idx_app_user_email on app_user (email);

create table user_role (
    user_id uuid not null,
    role_id uuid not null,
    constraint pk_user_role primary key (user_id, role_id),
    constraint fk_user_role_user foreign key (user_id) references app_user (id) on delete cascade,
    constraint fk_user_role_role foreign key (role_id) references role (id) on delete restrict
);

create index idx_user_role_role_id on user_role (role_id);

create table employee (
    id uuid primary key,
    hotel_id uuid not null,
    user_id uuid null,
    department_id uuid null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    employee_number text not null,
    display_name text not null,
    status text not null,
    constraint fk_employee_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint fk_employee_user foreign key (user_id) references app_user (id) on delete restrict deferrable initially deferred,
    constraint fk_employee_department foreign key (department_id) references department (id) on delete set null,
    constraint uk_employee_hotel_employee_number unique (hotel_id, employee_number),
    constraint uk_employee_user_id unique (user_id)
);

create index idx_employee_hotel_id on employee (hotel_id);
create index idx_employee_user_id on employee (user_id);
create index idx_employee_department_id on employee (department_id);
create index idx_employee_employee_number on employee (employee_number);

alter table app_user
    add constraint fk_app_user_employee foreign key (employee_id) references employee (id) on delete set null deferrable initially deferred;

create table employee_role (
    employee_id uuid not null,
    role_id uuid not null,
    constraint pk_employee_role primary key (employee_id, role_id),
    constraint fk_employee_role_employee foreign key (employee_id) references employee (id) on delete cascade,
    constraint fk_employee_role_role foreign key (role_id) references role (id) on delete restrict
);

create index idx_employee_role_role_id on employee_role (role_id);

create table employee_skill (
    employee_id uuid not null,
    skill_id uuid not null,
    constraint pk_employee_skill primary key (employee_id, skill_id),
    constraint fk_employee_skill_employee foreign key (employee_id) references employee (id) on delete cascade,
    constraint fk_employee_skill_skill foreign key (skill_id) references skill (id) on delete restrict
);

create index idx_employee_skill_skill_id on employee_skill (skill_id);

create table refresh_session (
    id uuid primary key,
    user_id uuid not null,
    hotel_id uuid not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    refresh_token_hash text not null,
    device_id text not null,
    device_name text null,
    ip_address text null,
    user_agent text null,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    last_used_at timestamptz null,
    constraint fk_refresh_session_user foreign key (user_id) references app_user (id) on delete cascade,
    constraint fk_refresh_session_hotel foreign key (hotel_id) references hotel (id) on delete restrict,
    constraint uk_refresh_session_refresh_token_hash unique (refresh_token_hash)
);

create index idx_refresh_session_user_id on refresh_session (user_id);
create index idx_refresh_session_hotel_id on refresh_session (hotel_id);
create index idx_refresh_session_expires_at on refresh_session (expires_at);
