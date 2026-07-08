# Sprint 3 - Hotel Operations Engine

## Goal
Build the Task Engine, Workflow Engine, state machine, assignment foundation, SLA support, task history, task logs, notification foundation, and domain event model.

## Business value
Turns Hotel OpAI into a durable system of action that can own operational work instead of only capturing intent.

## Architecture impact
- Establishes the workflow/state-machine layer as the source of truth for operational transitions.
- Makes task lifecycle transitions explicit and rejectable when invalid.
- Introduces assignment and SLA foundations that other surfaces can consume.
- Creates the domain event model that later notifications, dashboards, reporting, and AI features will subscribe to.

## Backend tasks
- Implement the workflow engine and task lifecycle foundation.
- Add assignment foundation and task history/log persistence.
- Add SLA deadline tracking and overdue handling.
- Add notification-event publication hooks without implementing the full notification system yet.

## Mobile tasks
- Add task list, task detail, assignment display, and SLA indicators when backend APIs exist.

## AI tasks
- Keep AI out of task creation.
- Ensure assistant flows later use this engine instead of bypassing it.

## UniMock tasks
- Use UniMock only for PMS lookups needed by task context.
- Do not add scenario execution.

## Database tasks
- Add Flyway migrations for workflows, tasks, assignments, history, logs, SLA metadata, and event tables as needed.

## Infrastructure tasks
- Extend integration testing for workflow persistence and lifecycle transitions.
- Add logging for transition and correlation context.

## UI tasks
- Implement operational task surfaces using the existing UI references.
- Keep the experience compact and work-focused.

## Documentation tasks
- Document task lifecycle rules, state transitions, assignment rules, SLA semantics, and business event ownership.

## Testing tasks
- Verify valid and invalid state transitions.
- Verify task persistence, task history, task logs, SLA calculations, and assignment rules.
- Verify event emission hooks are wired for future consumers.

## Risks
- A weak state machine will make later automation unreliable.
- SLA rules must be explicit about timing, pause, and overdue semantics.

## Definition of Done
- Tasks are durable, tenant-scoped, assignable, and governed by explicit transitions.
- Assignment and SLA foundations exist.
- Task history and task logs are queryable.

## Dependencies
- Depends on Sprint 1 persistence/auth foundation and Sprint 2 UniMock integration.
- Provides the operational core for Sprint 4 mobile/backend integration and Sprint 5 assistant flows.
