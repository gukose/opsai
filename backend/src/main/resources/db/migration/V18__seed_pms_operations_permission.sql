insert into permission (id, version, created_at, created_by, updated_at, updated_by, code, name, description)
values
    ('00000000-0000-0000-0000-000000000151', 0, now(), 'V18', now(), 'V18', 'PMS_OPERATIONS_ACCESS', 'Access PMS operations', 'Allows inspecting sanitized PMS provider diagnostics and rollout readiness')
on conflict (code) do nothing;

insert into role_permission (role_id, permission_id)
select r.id, p.id
from role r
join permission p on p.code = 'PMS_OPERATIONS_ACCESS'
where r.code = 'ADMIN'
on conflict do nothing;
