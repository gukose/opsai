# ADR-013: Notification Engine

## Context
Hotel operations require notifications for task creation, assignment, approval, and state changes.

## Decision
Notifications will be handled as a dedicated operational capability in Hotel OpAI, separate from conversation state and task lifecycle logic.

## Alternatives considered
- Emit notifications directly from controllers
- Bake notifications into task entities
- Ignore notifications until later

## Consequences
- Notification behavior can evolve independently
- Operational events can be notified consistently
- The task engine stays focused on lifecycle rules

## Future impact
This supports future channels such as push, in-app, email, and team routing without rewriting task or workflow logic.

