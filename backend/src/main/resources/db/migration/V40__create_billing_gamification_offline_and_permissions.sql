create table hotel_billing_counter (
    id uuid primary key, hotel_id uuid not null references hotel(id), business_date date not null,
    occupied_room_count integer not null check(occupied_room_count >= 0), source_provider text not null,
    source_event_hash text not null, idempotency_key text not null, recorded_at timestamptz not null,
    corrected_from_id uuid null references hotel_billing_counter(id), correction_reason text null, corrected_by uuid null references app_user(id),
    unique(hotel_id, business_date), unique(hotel_id, idempotency_key)
);
create table gamification_ledger (
    id uuid primary key, hotel_id uuid not null references hotel(id), employee_id uuid not null references employee(id),
    event_type text not null, source_reference text not null, xp integer not null, quality_score integer null,
    occurred_at timestamptz not null, rule_version text not null, unique(hotel_id, source_reference, event_type)
);
create table employee_badge (
    hotel_id uuid not null references hotel(id), employee_id uuid not null references employee(id), badge_code text not null,
    earned_at timestamptz not null, source_reference text not null, primary key(hotel_id, employee_id, badge_code)
);
create table offline_operation (
    id uuid primary key, hotel_id uuid not null references hotel(id), user_id uuid not null references app_user(id),
    client_operation_id text not null, operation_type text not null, resource_id text null, payload_hash text not null,
    status text not null, result_reference text null, submitted_at timestamptz not null, processed_at timestamptz null,
    unique(hotel_id, user_id, client_operation_id)
);

insert into permission(id, version, created_at, created_by, updated_at, updated_by, code, name, description)
select gen_random_uuid(), 0, now(), 'V40', now(), 'V40', code, name, description
from (values
 ('HOUSEKEEPING_OPERATIONS','Housekeeping operations','Execute hotel-scoped housekeeping workflows'),
 ('HOUSEKEEPING_INSPECTION','Housekeeping inspection','Approve or reject hotel-scoped inspections'),
 ('INVENTORY_OPERATIONS','Inventory operations','Manage hotel-scoped inventory ledger'),
 ('MINIBAR_OPERATIONS','Minibar operations','Capture and review minibar operations'),
 ('DAMAGE_REVIEW','Damage review','Review hotel-scoped damage and charges'),
 ('EMPLOYEE_OPERATIONS','Employee operations','Manage safe employee operational profiles'),
 ('SHIFT_OPERATIONS','Shift operations','Manage shifts and handovers'),
 ('SERVICE_RECOVERY_OPERATIONS','Service recovery','Manage guest risk and service recovery'),
 ('MANAGER_REPORTING','Manager reporting','Use safe manager reporting'),
 ('BILLING_REPORTS','Billing reports','View and correct occupied-room counters'),
 ('GAMIFICATION_VIEW','Gamification view','View privacy-safe gamification results'),
 ('GUEST_MESSAGING_OPERATIONS','Guest messaging','Operate guest session messaging')
) p(code,name,description)
on conflict(code) do nothing;

insert into role_permission(role_id, permission_id)
select r.id, p.id from role r cross join permission p
where r.code = 'ADMIN' and p.code in ('HOUSEKEEPING_OPERATIONS','HOUSEKEEPING_INSPECTION','INVENTORY_OPERATIONS','MINIBAR_OPERATIONS','DAMAGE_REVIEW','EMPLOYEE_OPERATIONS','SHIFT_OPERATIONS','SERVICE_RECOVERY_OPERATIONS','MANAGER_REPORTING','BILLING_REPORTS','GAMIFICATION_VIEW','GUEST_MESSAGING_OPERATIONS')
on conflict do nothing;

with matrix(role_code, permission_code) as (values
 ('MANAGER','HOUSEKEEPING_OPERATIONS'),('MANAGER','HOUSEKEEPING_INSPECTION'),('MANAGER','INVENTORY_OPERATIONS'),('MANAGER','MINIBAR_OPERATIONS'),('MANAGER','DAMAGE_REVIEW'),('MANAGER','EMPLOYEE_OPERATIONS'),('MANAGER','SHIFT_OPERATIONS'),('MANAGER','SERVICE_RECOVERY_OPERATIONS'),('MANAGER','MANAGER_REPORTING'),('MANAGER','BILLING_REPORTS'),('MANAGER','GAMIFICATION_VIEW'),('MANAGER','GUEST_MESSAGING_OPERATIONS'),
 ('HOUSEKEEPING','HOUSEKEEPING_OPERATIONS'),('HOUSEKEEPING','MINIBAR_OPERATIONS'),('HOUSEKEEPING','SHIFT_OPERATIONS'),('HOUSEKEEPING','GAMIFICATION_VIEW'),
 ('FRONT_DESK','MINIBAR_OPERATIONS'),('FRONT_DESK','DAMAGE_REVIEW'),('FRONT_DESK','SERVICE_RECOVERY_OPERATIONS'),('FRONT_DESK','GUEST_MESSAGING_OPERATIONS'),('FRONT_DESK','SHIFT_OPERATIONS'),
 ('MAINTENANCE','SHIFT_OPERATIONS'),('MAINTENANCE','GAMIFICATION_VIEW'),('STAFF','SHIFT_OPERATIONS'),('STAFF','GAMIFICATION_VIEW')
)
insert into role_permission(role_id, permission_id)
select r.id,p.id from matrix m join role r on r.code=m.role_code join permission p on p.code=m.permission_code
on conflict do nothing;
