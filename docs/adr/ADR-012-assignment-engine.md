# ADR-012: Assignment Engine

## Context
Tasks must be assignable to teams or people, and the assignment model must remain reusable across hotel operation types.

## Decision
Assignments will be modeled as a dedicated engine and domain concept, separate from task creation and separate from the conversation engine.

## Alternatives considered
- Store assignment as a free-form string
- Infer assignment inside the UI
- Couple assignment logic directly to each task type

## Consequences
- Assignment behavior can evolve independently
- Task creation stays simpler
- Future routing or auto-assignment logic can be added cleanly

## Future impact
The assignment engine can later support skill-based routing, escalation rules, and team-aware automation.

