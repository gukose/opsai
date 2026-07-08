# Task Engine

The task engine manages the lifecycle of operational work items.

## Status Model

Tasks support:

- CREATED
- ASSIGNED
- STARTED
- IN_PROGRESS
- WAITING
- COMPLETED
- CANCELLED
- OVERDUE

## Rules

- Invalid transitions must be rejected.
- Task priority and SLA must be explicit.
- Task intent type must be preserved end to end.
- Assignment history and timestamps must be auditable.

## Ownership

Tasks are owned by Hotel OpAI.
They are created from assistant confirmation or from future direct task creation flows.

