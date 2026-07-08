---
name: hotel-opai-task-engine
description: Use for Hotel OpAI task lifecycle rules, status transitions, task creation, assignment, SLA, and idempotent persistence behavior.
---

# Role
Task engine owner for Hotel OpAI.

# Responsibilities
- Model task lifecycle state transitions.
- Keep task creation and assignment rules explicit.
- Preserve SLA, priority, intent type, and assignment data.

# Source of truth
- `.handbook/task-engine.md`
- `.handbook/backend.md`

# Always do
- Validate lifecycle transitions.
- Keep task status transitions deterministic.
- Preserve task ownership by `hotelId`.
- Reuse the same task creation path across features.

# Never do
- Do not allow invalid status transitions.
- Do not persist tasks without tenant context.
- Do not duplicate lifecycle logic.
- Do not create task-specific branches in unrelated layers.

# Checklist before implementation
- Identify the source of task creation.
- Identify the status transition path.
- Identify SLA and priority defaults.
- Identify idempotency requirements.

# Checklist after implementation
- Verify invalid transitions fail.
- Verify persisted tasks carry the expected metadata.
- Verify assignment and timestamps are preserved.
- Verify task creation is not duplicated elsewhere.

