# Security Permission Matrix

Sprint 8C replaces broad authenticated-only operational access with explicit
capability checks at controller boundaries.

Tenant isolation remains separate from capability authorization:

- Missing or invalid authentication returns `401`.
- Authenticated users without the required capability return `403`.
- Users with the capability but a foreign-hotel resource receive the existing
  non-leaking `404` behavior.

Task visibility and task writes remain hotel-scoped in Sprint 8C. Assignment,
department, employee, team, or category-specific row filtering is not
implemented in this sprint.

## Permission Codes

Auth:

- `AUTH_LOGIN`
- `AUTH_MANAGE`
- `AUTH_VIEW`

Tasks:

- `TASK_READ`
- `TASK_CREATE`
- `TASK_ASSIGN`
- `TASK_START`
- `TASK_PAUSE`
- `TASK_RESUME`
- `TASK_COMPLETE`
- `TASK_CANCEL`
- `TASK_MARK_OVERDUE`
- `TASK_ATTACHMENT_READ`

Assistant:

- `ASSISTANT_USE`
- `ASSISTANT_CONFIRM_TASK`
- `ASSISTANT_ATTACHMENT_REGISTER`
- `ASSISTANT_VISION_IMPORT`

Notifications:

- `NOTIFICATION_READ`
- `NOTIFICATION_MARK_READ`

Dashboard and reporting:

- `DASHBOARD_READ`
- `REPORT_READ`

Local/test Dev PMS:

- `DEV_PMS_ACCESS`

Internal PMS operations:

- `PMS_OPERATIONS_ACCESS`

Internal reservation sync operations:

- `RESERVATION_SYNC_OPERATIONS`

## Role Matrix

`ADMIN` receives all existing and new permissions, including `DEV_PMS_ACCESS`,
`PMS_OPERATIONS_ACCESS`, and `RESERVATION_SYNC_OPERATIONS`.

`MANAGER` receives operational task management, assistant, notification,
dashboard, and reporting permissions. It does not receive `DEV_PMS_ACCESS`.

`FRONT_DESK` receives task read/create/assign, task attachment read, assistant
use/confirm/attachment registration/vision import, notification read/mark-read,
and dashboard read. It does not receive lifecycle execution, cancel,
mark-overdue, report, or Dev PMS permissions.

`MAINTENANCE` receives task read/create/start/pause/resume/complete, task
attachment read, assistant use/confirm/attachment registration/vision import,
and notification read/mark-read. It does not receive assign, cancel,
mark-overdue, dashboard, report, or Dev PMS permissions.

`HOUSEKEEPING` uses the same Sprint 8C capability baseline as `MAINTENANCE`.
This does not imply housekeeping-only row filtering.

`STAFF` receives task read/start/pause/resume/complete, task attachment read,
assistant use, assistant attachment registration, and notification
read/mark-read. It does not receive task create, task assign, assistant confirm,
vision import, dashboard, report, cancel, mark-overdue, or Dev PMS permissions.

## Endpoint Mapping

- `GET /api/v1/auth/me`: `AUTH_VIEW`
- `POST /api/v1/auth/logout`: authenticated-only self-session logout
- `GET /api/v1/tasks`: `TASK_READ`
- `GET /api/v1/tasks/{taskId}`: `TASK_READ`
- `POST /api/v1/tasks`: `TASK_CREATE`
- `GET /api/v1/tasks/{taskId}/attachments`: `TASK_ATTACHMENT_READ`
- `POST /api/v1/tasks/{taskId}/assign`: `TASK_ASSIGN`
- `POST /api/v1/tasks/{taskId}/start`: `TASK_START`
- `POST /api/v1/tasks/{taskId}/pause`: `TASK_PAUSE`
- `POST /api/v1/tasks/{taskId}/resume`: `TASK_RESUME`
- `POST /api/v1/tasks/{taskId}/complete`: `TASK_COMPLETE`
- `POST /api/v1/tasks/{taskId}/cancel`: `TASK_CANCEL`
- `POST /api/v1/tasks/{taskId}/overdue`: `TASK_MARK_OVERDUE`
- assistant start/message/reset: `ASSISTANT_USE`
- assistant confirm: `ASSISTANT_CONFIRM_TASK`
- assistant attachment registration: `ASSISTANT_ATTACHMENT_REGISTER`
- assistant vision import: `ASSISTANT_VISION_IMPORT`
- `GET /api/v1/notifications`: `NOTIFICATION_READ`
- notification mark-read: `NOTIFICATION_MARK_READ`
- `GET /api/v1/dashboard/summary`: `DASHBOARD_READ`
- `GET /api/v1/dashboard/reports/tasks`: `REPORT_READ`
- all `/api/v1/dev/pms/**` endpoints: `DEV_PMS_ACCESS`
- all `/api/v1/internal/pms/**` endpoints: `PMS_OPERATIONS_ACCESS`
- all `/api/v1/internal/reservations/**` sync, scheduled sync, webhook inbox,
  webhook processing scheduler, dead-letter retry, and cleanup endpoints:
  `RESERVATION_SYNC_OPERATIONS`

Login, refresh, CORS preflight, actuator health, and actuator info keep their
existing public behavior.

## Tokens

Access tokens carry role and permission claims at issue time. Permission mapping
changes apply to newly issued access tokens through login or refresh. Existing
access tokens keep their embedded claims until expiry.
