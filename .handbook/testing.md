# Testing

Testing must be deterministic, repeatable, and aligned to production boundaries.

## Test Layers

- domain tests for invariants and transitions
- application tests for orchestration and use cases
- controller tests for API contracts
- integration tests for external client boundaries
- mobile type checks and UI behavior tests

## Expectations

- cover happy path and failure path
- verify idempotency where required
- verify invalid state transitions are rejected
- verify task and conversation ownership boundaries
- verify assistant behavior without live AI dependency when possible

## Simulation

Prefer the deterministic interpreter and UniMock simulator for stable tests.

