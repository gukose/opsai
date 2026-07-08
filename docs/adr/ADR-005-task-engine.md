# ADR-005: Task Engine

## Context
Hotel OpAI owns operational work items, their lifecycle, assignments, SLAs, and status transitions.

## Decision
Tasks will be modeled as a dedicated domain with explicit lifecycle transitions, priority, SLA deadline, assignment metadata, source, and intent type.

## Alternatives considered
- Reuse conversation state as the task lifecycle
- Allow free-form task status updates
- Create tasks only as integration side effects

## Consequences
- Task behavior becomes auditable and enforceable
- Invalid transitions are rejected by the domain
- Tasks remain reusable across all hotel operation types

## Future impact
This creates a stable foundation for assignment automation, escalation, SLA monitoring, and future task workflows.

