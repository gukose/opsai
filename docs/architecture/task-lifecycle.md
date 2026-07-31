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

## Reservation Automation Tasks

Sprint 13A allows canonical reservation events to propose operational tasks.
Sprint 13B adds disabled-by-default scheduled processing and configurable rule
due-time policy.
The automation engine creates tasks only through the existing task lifecycle
service. It does not bypass task validation, history, logs, notifications, or
SLA checks.

Generated reservation tasks use existing public `TaskSource.IMPORT` to avoid a
public API enum change. Their automation origin, rule id, rule version,
deduplication key, and execution outcome are stored in private reservation task
automation execution history.

Repeated rule evaluation must preserve manually managed task fields. If a
generated task is edited, assigned, completed, cancelled, or otherwise managed
by staff, the automation engine does not overwrite title, description,
assignment, priority, due date, or lifecycle status. A replacement task is
created only when a new logical trigger or rule version produces a new durable
deduplication key.

Automation task titles and descriptions are generic operational instructions.
They must not include guest names, contact information, external reservation
references, raw PMS property identifiers, reservation notes, credentials, or raw
provider payloads.

Rule due-date policy is configured under
`ops.ai.reservation.task-automation.rules.<rule-id>`. Rules own applicability;
policy can only adjust enablement, priority, local due time, due-date offset,
minimum lead time, timezone, maximum trigger age, and past-due clamping.

## Reservation Task Recommendations

Sprint 13C adds advisory AI-assisted reservation task recommendations.
Recommendations do not create, modify, reopen, complete, or delete tasks.
Only the internal review workflow can apply an approved recommendation, and
application creates a task through the same `TaskLifecycleService` boundary as
manual and deterministic automation task creation.

Recommendation responses expose structured situation, rationale, supporting
signals, category, confidence, safe task title, priority, and due date. They do
not expose raw reservation references, property identifiers, guest data,
provider payloads, deduplication keys, or generated prompts.

Sprint 13D adds provider governance and scheduled recommendation generation.
Providers are resolved through `TaskRecommendationProviderRegistry` by stable
provider id and must declare capabilities before generation is allowed. The
current `internal-demo` provider remains local and deterministic.

Scheduled generation is disabled by default and can only create
`REVIEW_REQUIRED` recommendation records. It never approves, applies, modifies,
reopens, completes, or deletes tasks. Applying a recommendation remains an
explicit internal review action and still uses the normal task lifecycle
boundary.

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
- task attachment metadata linked
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

## Task Attachment Links

Sprint 7 task attachments are metadata/provenance links created only after successful explicit assistant confirmation.

- Links are created in `task_attachment_link`.
- Links point to backend-owned `REGISTERED` assistant attachments only.
- `LOCAL_METADATA_ONLY` message attachments do not create durable task links.
- Older unrelated registered conversation attachments are not linked.
- Valid source types are `ASSISTANT_MESSAGE` and `VISION_ANALYSIS`.
- Vision provenance stores analysis/import IDs where available, but it remains audit metadata and does not become authoritative task data.
- `REGISTERED` still means metadata identity only. It does not mean uploaded, stored, downloadable, provider-accessible, or analyzed.
- The task attachment read API returns metadata/provenance only and must not expose binary, base64, local URI, storage reference, download URL, raw provider payload, or provider secret.

Task deletion cascades task attachment link rows. Linked attachment, conversation, analysis, and analysis-import deletion is restricted by foreign keys while task links depend on them, so audit provenance is not silently destroyed.
