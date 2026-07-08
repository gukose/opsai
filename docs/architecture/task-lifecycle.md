# Task Lifecycle

## Purpose

Tasks are the operational source of truth in Hotel OpAI. A task represents work assigned to hotel staff. PMS master data remains in UniMock; Hotel OpAI stores operational execution data.

## Ownership

Hotel OpAI owns:

- task identity
- task status
- priority
- assignment
- SLA
- operational notes
- attachments metadata
- notifications
- approvals
- audit logs
- task history

UniMock owns:

- room identity and state
- occupancy
- room type
- public area definitions
- assets
- minibar inventory and state
- guest request source data
- events

## Lifecycle States

Recommended task states:

- `DRAFT`
- `PENDING_CONFIRMATION`
- `CREATED`
- `ASSIGNED`
- `ACKNOWLEDGED`
- `IN_PROGRESS`
- `BLOCKED`
- `COMPLETED`
- `CANCELLED`
- `FAILED`

## State Transitions

`DRAFT`

- Created from AI extraction or manual operation creation.
- Not visible as assigned work.

`PENDING_CONFIRMATION`

- Has enough required information.
- Rendered as inline Task Preview.
- Awaits user confirmation.

`CREATED`

- Persisted operational task.
- Has stable task ID.
- Assignment may be pending.

`ASSIGNED`

- Responsible team or user is selected.
- Notification is emitted.

`ACKNOWLEDGED`

- Assignee has seen or accepted the task.

`IN_PROGRESS`

- Work has started.
- SLA timer continues unless task policy pauses it.

`BLOCKED`

- Work cannot continue.
- Requires reason and optional escalation.

`COMPLETED`

- Work is done.
- May trigger UniMock PMS update if configured.

`CANCELLED`

- Task is intentionally stopped.
- Requires reason.

`FAILED`

- Task creation or external integration failed.
- Requires retry or manual resolution.

## Creation Flow

1. AI or UI creates a task draft.
2. Backend validates required fields.
3. Backend resolves PMS references through UniMock.
4. Backend builds task preview.
5. User confirms.
6. Backend creates task transactionally.
7. Backend assigns task using routing rules.
8. Backend emits notification.
9. Backend records audit event.

## Assignment Rules

Assignment should be deterministic and configurable:

- task type
- department
- floor
- room range
- shift
- staff availability
- skill or role
- escalation policy

The AI may suggest an assignment, but final routing belongs to backend services.

## PMS Mutation Rules

Tasks may affect PMS-owned state only through UniMock APIs.

Examples:

- minibar task completion updates minibar state in UniMock
- maintenance task may update asset issue state in UniMock
- guest request completion may update guest request status in UniMock
- public area task may update public area issue/event state in UniMock

Hotel OpAI must store:

- attempted PMS mutation
- UniMock request ID or correlation ID
- response status
- failure reason
- retry status

## Idempotency

Production task operations should use idempotency keys for:

- task creation from confirmed AI draft
- notification dispatch
- UniMock state mutations

Recommended key examples:

- `conversationId + draftVersion + action`
- `taskId + pmsMutationType`
- `taskId + notificationType + recipientId`

## Audit Events

Minimum audit events:

- task draft created
- task preview shown
- task confirmed
- task created
- task assigned
- notification sent
- task acknowledged
- task started
- task blocked
- task completed
- PMS update requested
- PMS update succeeded/failed

## PostgreSQL Tables

Hotel OpAI tables should include:

- `tasks`
- `task_assignments`
- `task_events`
- `task_attachments`
- `task_comments`
- `notifications`
- `conversation_sessions`
- `conversation_messages`
- `ai_interaction_logs`
- `pms_integration_events`

Do not duplicate UniMock master data into Hotel OpAI except for immutable snapshots needed for audit display.
