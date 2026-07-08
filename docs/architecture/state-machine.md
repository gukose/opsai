# Multi-Turn Conversation State Machine

## Purpose

The conversation state machine controls assistant workflows from first user message to confirmed task creation. It prevents the AI from creating tasks prematurely and ensures missing or ambiguous information is collected through focused follow-up questions.

## State List

Recommended states:

- `IDLE`
- `RECEIVING_INPUT`
- `ANALYZING`
- `NEEDS_FOLLOW_UP`
- `WAITING_FOR_USER_ANSWER`
- `READY_FOR_PREVIEW`
- `WAITING_FOR_CONFIRMATION`
- `CREATING_TASK`
- `TASK_CREATED`
- `FAILED`
- `CANCELLED`

## State Definitions

`IDLE`

- No active task draft.
- Assistant can accept a new request.

`RECEIVING_INPUT`

- Backend has received user text, voice, image, or mixed input.
- Attachments may still be processing.

`ANALYZING`

- Backend is building context and calling AI adapter.
- User should not see a blocking modal; mobile may show inline assistant activity.

`NEEDS_FOLLOW_UP`

- Required fields are missing.
- Backend has generated a specific question.

`WAITING_FOR_USER_ANSWER`

- Assistant has asked a question and is waiting for user input or selection.

`READY_FOR_PREVIEW`

- Required fields are present and validated.
- Backend can generate a task preview.

`WAITING_FOR_CONFIRMATION`

- Mobile displays inline Task Preview.
- User can confirm, edit, cancel, or answer further questions.

`CREATING_TASK`

- Backend is creating the task transactionally.
- Idempotency key is required.

`TASK_CREATED`

- Task exists, assignment and notifications are triggered.
- Conversation can reset or continue.

`FAILED`

- AI, validation, task creation, or integration failed.
- User can retry or cancel.

`CANCELLED`

- User intentionally stopped the flow.

## Transition Rules

```text
IDLE -> RECEIVING_INPUT
RECEIVING_INPUT -> ANALYZING
ANALYZING -> NEEDS_FOLLOW_UP
ANALYZING -> READY_FOR_PREVIEW
ANALYZING -> FAILED
NEEDS_FOLLOW_UP -> WAITING_FOR_USER_ANSWER
WAITING_FOR_USER_ANSWER -> RECEIVING_INPUT
READY_FOR_PREVIEW -> WAITING_FOR_CONFIRMATION
WAITING_FOR_CONFIRMATION -> CREATING_TASK
WAITING_FOR_CONFIRMATION -> WAITING_FOR_USER_ANSWER
WAITING_FOR_CONFIRMATION -> CANCELLED
CREATING_TASK -> TASK_CREATED
CREATING_TASK -> FAILED
FAILED -> RECEIVING_INPUT
FAILED -> CANCELLED
TASK_CREATED -> IDLE
```

## Required Data Checks

Every intent should define required fields.

Examples:

Guest Request:

- room or guest context
- description
- target department or inferred category

Maintenance:

- room, public area, or asset
- issue description
- priority

Tray Removal:

- room
- tray count or confirmation if unknown

Lost and Found:

- item description
- location
- timestamp or shift context

Laundry:

- room or source area
- item type
- quantity if required

## Ambiguity Handling

If multiple PMS records match:

- do not guess silently
- return a `question` render item
- include options when possible
- persist ambiguity context

Example:

User says: `AC not working in one zero one`

If room `101` exists:

- ask confirmation if confidence is below threshold

If multiple rooms match:

- ask user to choose

If no room matches:

- ask for a valid room

## Task Preview Rules

Task Preview is shown only when:

- required fields are present
- PMS references are validated through UniMock
- assignment target can be resolved or deferred by policy
- task type is supported

Task Preview should include:

- type
- room or area
- description
- assigned team
- priority
- SLA

## Persistence

Persist:

- conversation session
- current state
- draft version
- extracted fields
- missing fields
- ambiguity context
- messages
- AI interaction metadata
- task draft snapshots

The state machine should be resumable after app restart.

## Idempotency

Confirmation must be idempotent.

Use:

- `conversationId`
- `draftVersion`
- `confirmActionId`

This prevents duplicate task creation from double taps, retries, or mobile reconnects.

## UniMock Boundary

The state machine may request PMS validation through ports:

- `findRoom`
- `findAsset`
- `findPublicArea`
- `getIssueTypes`
- `getMinibarItems`

It must not directly call HTTP clients. Application services call ports; integration adapters implement those ports.

## Failure Handling

Common failures:

- AI response invalid
- UniMock unavailable
- room not found
- task validation failed
- task creation conflict
- notification failed

Failure strategy:

- preserve conversation state
- show clear assistant message
- allow retry where safe
- never create partial duplicate tasks
