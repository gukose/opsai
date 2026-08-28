alter table hotel add column if not exists timezone text not null default 'UTC';
alter table hotel add column if not exists address text null;
alter table department add constraint uk_department_id_hotel unique(id, hotel_id);
alter table role add constraint uk_role_id_hotel unique(id, hotel_id);
alter table skill add constraint uk_skill_id_hotel unique(id, hotel_id);
alter table role add column if not exists is_active boolean not null default true;

create table building (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    code text not null,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    unique(hotel_id, code),
    unique(id, hotel_id)
);
create index idx_building_hotel_active on building(hotel_id, active, code);

create table hotel_floor (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    building_id uuid not null,
    floor_number integer not null,
    name text null,
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    constraint fk_floor_building_hotel foreign key(building_id, hotel_id) references building(id, hotel_id) on delete restrict,
    unique(building_id, floor_number),
    unique(id, hotel_id, building_id)
);
create index idx_floor_hotel_building on hotel_floor(hotel_id, building_id, active, floor_number);

create table room_master (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    building_id uuid not null,
    floor_id uuid not null,
    room_number text not null,
    room_type text null,
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    constraint fk_room_building_hotel foreign key(building_id, hotel_id) references building(id, hotel_id) on delete restrict,
    constraint fk_room_floor_hotel_building foreign key(floor_id, hotel_id, building_id) references hotel_floor(id, hotel_id, building_id) on delete restrict,
    unique(hotel_id, room_number)
);
create index idx_room_master_search on room_master(hotel_id, active, building_id, floor_id, room_type, room_number);

create table user_hotel_membership (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    hotel_id uuid not null references hotel(id) on delete restrict,
    department_id uuid null,
    active boolean not null default true,
    start_date date null,
    end_date date null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    constraint fk_membership_department_hotel foreign key(department_id, hotel_id) references department(id, hotel_id) on delete restrict,
    constraint ck_membership_dates check(end_date is null or start_date is null or end_date >= start_date),
    unique(user_id, hotel_id)
);
create index idx_membership_hotel_active on user_hotel_membership(hotel_id, active, user_id);

create table user_hotel_role (
    id uuid primary key,
    membership_id uuid not null references user_hotel_membership(id) on delete cascade,
    role_id uuid not null,
    hotel_id uuid not null references hotel(id) on delete restrict,
    created_at timestamptz not null,
    created_by text null,
    constraint fk_user_hotel_role_scope foreign key(role_id, hotel_id) references role(id, hotel_id) on delete restrict,
    unique(membership_id, role_id)
);
create index idx_user_hotel_role_scope on user_hotel_role(hotel_id, membership_id);

create table user_hotel_skill (
    id uuid primary key,
    membership_id uuid not null references user_hotel_membership(id) on delete cascade,
    skill_id uuid not null,
    hotel_id uuid not null references hotel(id) on delete restrict,
    skill_level text null check(skill_level is null or skill_level in ('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')),
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    constraint fk_user_hotel_skill_scope foreign key(skill_id, hotel_id) references skill(id, hotel_id) on delete restrict,
    unique(membership_id, skill_id)
);
create index idx_user_hotel_skill_scope on user_hotel_skill(hotel_id, membership_id, active);

create table shift_definition (
    id uuid primary key,
    hotel_id uuid not null references hotel(id) on delete restrict,
    code text not null,
    name text not null,
    start_time time not null,
    end_time time not null,
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    unique(hotel_id, code),
    unique(id, hotel_id)
);
create index idx_shift_definition_hotel on shift_definition(hotel_id, active, code);

create table user_shift_assignment (
    id uuid primary key,
    membership_id uuid not null references user_hotel_membership(id) on delete cascade,
    hotel_id uuid not null references hotel(id) on delete restrict,
    shift_id uuid not null,
    shift_date date not null,
    active boolean not null default true,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    constraint fk_shift_assignment_definition foreign key(shift_id, hotel_id) references shift_definition(id, hotel_id) on delete restrict,
    unique(membership_id, shift_date)
);
create index idx_shift_assignment_filters on user_shift_assignment(hotel_id, shift_date, shift_id, active);

-- Existing hotel users retain access and their current hotel roles.
insert into user_hotel_membership(id,user_id,hotel_id,department_id,active,created_at,updated_at)
select gen_random_uuid(),u.id,u.hotel_id,e.department_id,true,now(),now()
from app_user u left join employee e on e.id=u.employee_id
on conflict(user_id,hotel_id) do nothing;

insert into user_hotel_role(id,membership_id,role_id,hotel_id,created_at)
select gen_random_uuid(),m.id,ur.role_id,m.hotel_id,now()
from user_hotel_membership m join user_role ur on ur.user_id=m.user_id
join role r on r.id=ur.role_id and r.hotel_id=m.hotel_id
on conflict(membership_id,role_id) do nothing;

insert into permission(id,version,created_at,updated_at,code,name,description)
select gen_random_uuid(),0,now(),now(),v.code,v.name,'Multi-hotel master data permission'
from (values
 ('PLATFORM_HOTEL_MANAGE','Manage hotels across the platform'),('HOTEL_VIEW','View hotels'),('HOTEL_MANAGE','Manage hotels'),
 ('BUILDING_VIEW','View buildings'),('BUILDING_MANAGE','Manage buildings'),
 ('FLOOR_VIEW','View floors'),('FLOOR_MANAGE','Manage floors'),
 ('ROOM_VIEW','View rooms'),('ROOM_CREATE','Create rooms'),('ROOM_UPDATE','Update rooms'),('ROOM_DELETE','Deactivate rooms'),
 ('DEPARTMENT_VIEW','View departments'),('DEPARTMENT_MANAGE','Manage departments'),
 ('USER_VIEW','View users'),('USER_CREATE','Create users'),('USER_UPDATE','Update users'),('USER_ASSIGN','Assign users'),
 ('ROLE_VIEW','View roles'),('ROLE_MANAGE','Manage roles'),
 ('SKILL_VIEW','View skills'),('SKILL_MANAGE','Manage skills'),
 ('SHIFT_VIEW','View shifts'),('SHIFT_MANAGE','Manage shifts')
) as v(code,name)
on conflict(code) do nothing;

-- Preserve current admin behavior by granting the new permissions to roles already carrying AUTH_MANAGE.
insert into role_permission(role_id,permission_id)
select distinct rp.role_id,p_new.id from role_permission rp
join permission p_old on p_old.id=rp.permission_id and p_old.code='AUTH_MANAGE'
cross join permission p_new
where p_new.code in ('PLATFORM_HOTEL_MANAGE','HOTEL_VIEW','HOTEL_MANAGE','BUILDING_VIEW','BUILDING_MANAGE','FLOOR_VIEW','FLOOR_MANAGE','ROOM_VIEW','ROOM_CREATE','ROOM_UPDATE','ROOM_DELETE','DEPARTMENT_VIEW','DEPARTMENT_MANAGE','USER_VIEW','USER_CREATE','USER_UPDATE','USER_ASSIGN','ROLE_VIEW','ROLE_MANAGE','SKILL_VIEW','SKILL_MANAGE','SHIFT_VIEW','SHIFT_MANAGE')
on conflict do nothing;
