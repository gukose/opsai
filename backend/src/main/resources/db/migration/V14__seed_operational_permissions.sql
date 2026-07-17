insert into permission (id, version, created_at, created_by, updated_at, updated_by, code, name, description)
values
    ('00000000-0000-0000-0000-000000000101', 0, now(), 'V14', now(), 'V14', 'TASK_READ', 'Read hotel tasks', 'Allows reading hotel-scoped task lists and task details'),
    ('00000000-0000-0000-0000-000000000102', 0, now(), 'V14', now(), 'V14', 'TASK_CREATE', 'Create hotel tasks', 'Allows creating tasks for the authenticated hotel'),
    ('00000000-0000-0000-0000-000000000103', 0, now(), 'V14', now(), 'V14', 'TASK_ASSIGN', 'Assign hotel tasks', 'Allows assigning hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000104', 0, now(), 'V14', now(), 'V14', 'TASK_START', 'Start hotel tasks', 'Allows starting hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000105', 0, now(), 'V14', now(), 'V14', 'TASK_PAUSE', 'Pause hotel tasks', 'Allows pausing hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000106', 0, now(), 'V14', now(), 'V14', 'TASK_RESUME', 'Resume hotel tasks', 'Allows resuming hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000107', 0, now(), 'V14', now(), 'V14', 'TASK_COMPLETE', 'Complete hotel tasks', 'Allows completing hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000108', 0, now(), 'V14', now(), 'V14', 'TASK_CANCEL', 'Cancel hotel tasks', 'Allows cancelling hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000109', 0, now(), 'V14', now(), 'V14', 'TASK_MARK_OVERDUE', 'Mark hotel tasks overdue', 'Allows marking hotel-scoped tasks overdue'),
    ('00000000-0000-0000-0000-00000000010a', 0, now(), 'V14', now(), 'V14', 'TASK_ATTACHMENT_READ', 'Read task attachments', 'Allows reading task attachment metadata for hotel-scoped tasks'),
    ('00000000-0000-0000-0000-000000000111', 0, now(), 'V14', now(), 'V14', 'ASSISTANT_USE', 'Use assistant', 'Allows starting, messaging, and resetting assistant conversations'),
    ('00000000-0000-0000-0000-000000000112', 0, now(), 'V14', now(), 'V14', 'ASSISTANT_CONFIRM_TASK', 'Confirm assistant task', 'Allows confirming assistant task previews'),
    ('00000000-0000-0000-0000-000000000113', 0, now(), 'V14', now(), 'V14', 'ASSISTANT_ATTACHMENT_REGISTER', 'Register assistant attachment metadata', 'Allows registering metadata-only assistant attachments'),
    ('00000000-0000-0000-0000-000000000114', 0, now(), 'V14', now(), 'V14', 'ASSISTANT_VISION_IMPORT', 'Import assistant vision analysis', 'Allows importing completed vision analysis into a conversation'),
    ('00000000-0000-0000-0000-000000000121', 0, now(), 'V14', now(), 'V14', 'NOTIFICATION_READ', 'Read notifications', 'Allows reading accessible notifications'),
    ('00000000-0000-0000-0000-000000000122', 0, now(), 'V14', now(), 'V14', 'NOTIFICATION_MARK_READ', 'Mark notifications read', 'Allows marking accessible notifications as read'),
    ('00000000-0000-0000-0000-000000000131', 0, now(), 'V14', now(), 'V14', 'DASHBOARD_READ', 'Read dashboard summary', 'Allows reading hotel-scoped dashboard summary'),
    ('00000000-0000-0000-0000-000000000132', 0, now(), 'V14', now(), 'V14', 'REPORT_READ', 'Read task reports', 'Allows reading hotel-scoped task reports'),
    ('00000000-0000-0000-0000-000000000141', 0, now(), 'V14', now(), 'V14', 'DEV_PMS_ACCESS', 'Access local Dev PMS proxy', 'Allows accessing local/test Dev PMS endpoints')
on conflict (code) do nothing;

with admin_permissions as (
    select id
    from permission
)
insert into role_permission (role_id, permission_id)
select r.id, p.id
from role r
cross join admin_permissions p
where r.code = 'ADMIN'
on conflict do nothing;

with matrix(role_code, permission_code) as (
    values
        ('MANAGER', 'AUTH_VIEW'),
        ('MANAGER', 'TASK_READ'),
        ('MANAGER', 'TASK_CREATE'),
        ('MANAGER', 'TASK_ASSIGN'),
        ('MANAGER', 'TASK_START'),
        ('MANAGER', 'TASK_PAUSE'),
        ('MANAGER', 'TASK_RESUME'),
        ('MANAGER', 'TASK_COMPLETE'),
        ('MANAGER', 'TASK_CANCEL'),
        ('MANAGER', 'TASK_MARK_OVERDUE'),
        ('MANAGER', 'TASK_ATTACHMENT_READ'),
        ('MANAGER', 'ASSISTANT_USE'),
        ('MANAGER', 'ASSISTANT_CONFIRM_TASK'),
        ('MANAGER', 'ASSISTANT_ATTACHMENT_REGISTER'),
        ('MANAGER', 'ASSISTANT_VISION_IMPORT'),
        ('MANAGER', 'NOTIFICATION_READ'),
        ('MANAGER', 'NOTIFICATION_MARK_READ'),
        ('MANAGER', 'DASHBOARD_READ'),
        ('MANAGER', 'REPORT_READ'),
        ('FRONT_DESK', 'AUTH_VIEW'),
        ('FRONT_DESK', 'TASK_READ'),
        ('FRONT_DESK', 'TASK_CREATE'),
        ('FRONT_DESK', 'TASK_ASSIGN'),
        ('FRONT_DESK', 'TASK_ATTACHMENT_READ'),
        ('FRONT_DESK', 'ASSISTANT_USE'),
        ('FRONT_DESK', 'ASSISTANT_CONFIRM_TASK'),
        ('FRONT_DESK', 'ASSISTANT_ATTACHMENT_REGISTER'),
        ('FRONT_DESK', 'ASSISTANT_VISION_IMPORT'),
        ('FRONT_DESK', 'NOTIFICATION_READ'),
        ('FRONT_DESK', 'NOTIFICATION_MARK_READ'),
        ('FRONT_DESK', 'DASHBOARD_READ'),
        ('MAINTENANCE', 'AUTH_VIEW'),
        ('MAINTENANCE', 'TASK_READ'),
        ('MAINTENANCE', 'TASK_CREATE'),
        ('MAINTENANCE', 'TASK_START'),
        ('MAINTENANCE', 'TASK_PAUSE'),
        ('MAINTENANCE', 'TASK_RESUME'),
        ('MAINTENANCE', 'TASK_COMPLETE'),
        ('MAINTENANCE', 'TASK_ATTACHMENT_READ'),
        ('MAINTENANCE', 'ASSISTANT_USE'),
        ('MAINTENANCE', 'ASSISTANT_CONFIRM_TASK'),
        ('MAINTENANCE', 'ASSISTANT_ATTACHMENT_REGISTER'),
        ('MAINTENANCE', 'ASSISTANT_VISION_IMPORT'),
        ('MAINTENANCE', 'NOTIFICATION_READ'),
        ('MAINTENANCE', 'NOTIFICATION_MARK_READ'),
        ('HOUSEKEEPING', 'AUTH_VIEW'),
        ('HOUSEKEEPING', 'TASK_READ'),
        ('HOUSEKEEPING', 'TASK_CREATE'),
        ('HOUSEKEEPING', 'TASK_START'),
        ('HOUSEKEEPING', 'TASK_PAUSE'),
        ('HOUSEKEEPING', 'TASK_RESUME'),
        ('HOUSEKEEPING', 'TASK_COMPLETE'),
        ('HOUSEKEEPING', 'TASK_ATTACHMENT_READ'),
        ('HOUSEKEEPING', 'ASSISTANT_USE'),
        ('HOUSEKEEPING', 'ASSISTANT_CONFIRM_TASK'),
        ('HOUSEKEEPING', 'ASSISTANT_ATTACHMENT_REGISTER'),
        ('HOUSEKEEPING', 'ASSISTANT_VISION_IMPORT'),
        ('HOUSEKEEPING', 'NOTIFICATION_READ'),
        ('HOUSEKEEPING', 'NOTIFICATION_MARK_READ'),
        ('STAFF', 'AUTH_VIEW'),
        ('STAFF', 'TASK_READ'),
        ('STAFF', 'TASK_START'),
        ('STAFF', 'TASK_PAUSE'),
        ('STAFF', 'TASK_RESUME'),
        ('STAFF', 'TASK_COMPLETE'),
        ('STAFF', 'TASK_ATTACHMENT_READ'),
        ('STAFF', 'ASSISTANT_USE'),
        ('STAFF', 'ASSISTANT_ATTACHMENT_REGISTER'),
        ('STAFF', 'NOTIFICATION_READ'),
        ('STAFF', 'NOTIFICATION_MARK_READ')
)
insert into role_permission (role_id, permission_id)
select r.id, p.id
from matrix m
join role r on r.code = m.role_code
join permission p on p.code = m.permission_code
on conflict do nothing;
