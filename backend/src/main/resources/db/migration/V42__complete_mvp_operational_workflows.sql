create table room_operational_state (
    hotel_id uuid not null references hotel(id), room_number text not null, status text not null,
    source_type text not null, source_reference text not null, updated_at timestamptz not null,
    primary key(hotel_id, room_number)
);

alter table housekeeping_checklist_template add column if not exists department_id uuid null references department(id);
alter table housekeeping_checklist_template add column if not exists version_number integer not null default 1;
alter table housekeeping_checklist_template add column if not exists enabled boolean not null default true;
alter table housekeeping_workflow add column if not exists template_id uuid null references housekeeping_checklist_template(id);
alter table housekeeping_workflow add column if not exists template_version integer null;

create table task_assignment_audit (
    id uuid primary key, hotel_id uuid not null references hotel(id), task_id uuid not null references task(id),
    previous_assignee_id text null, new_assignee_id text null, reason_code text not null,
    actor_user_id uuid not null references app_user(id), action text not null, created_at timestamptz not null
);
create index idx_task_assignment_audit_task on task_assignment_audit(hotel_id, task_id, created_at);

create table inventory_unit (
    id uuid primary key, hotel_id uuid not null references hotel(id), code text not null, name text not null,
    decimal_scale integer not null default 0, active boolean not null default true, unique(hotel_id, code)
);
alter table inventory_item add column if not exists minimum_stock numeric(16,3) null check(minimum_stock is null or minimum_stock >= 0);

create table damage_approval_history (
    id uuid primary key, hotel_id uuid not null references hotel(id), damage_report_id uuid not null references damage_report(id),
    action text not null, reason_code text null, amount numeric(14,2) null, actor_user_id uuid not null references app_user(id),
    created_at timestamptz not null
);

alter table guest_message add column if not exists linked_message_id uuid null references guest_message(id);
alter table guest_message add column if not exists delivered_at timestamptz null;

alter table service_recovery add column if not exists compensation_amount numeric(14,2) null check(compensation_amount is null or compensation_amount >= 0);
alter table service_recovery add column if not exists compensation_currency char(3) null;
alter table service_recovery add column if not exists compensation_reviewed_at timestamptz null;
alter table service_recovery add column if not exists closed_at timestamptz null;

create table billing_counter_correction (
    id uuid primary key, hotel_id uuid not null references hotel(id), billing_counter_id uuid not null references hotel_billing_counter(id),
    previous_count integer not null, corrected_count integer not null check(corrected_count >= 0), reason_code text not null,
    actor_user_id uuid not null references app_user(id), created_at timestamptz not null,
    unique(hotel_id, billing_counter_id, created_at)
);
