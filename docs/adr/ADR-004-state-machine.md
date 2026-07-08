# ADR-004: State Machine

## Context
Assistant conversations need deterministic transitions across multi-turn flows, confirmation, task creation, and reset behavior.

## Decision
Conversation handling will be modeled as an explicit state machine. State transitions must be validated and persisted through the application layer.

## Alternatives considered
- Implicit state in UI components
- Loose event handling without defined transitions
- A database-driven workflow table only

## Consequences
- Invalid transitions are rejected early
- Conversation behavior is predictable
- Tests can assert exact state progression

## Future impact
The state machine can evolve to support more conversation modes, richer task creation, and future interruption or escalation paths without losing determinism.

