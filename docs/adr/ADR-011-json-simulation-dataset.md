# ADR-011: JSON Simulation Dataset

## Context
Development and tests need deterministic hotel-world data that can drive realistic assistant and PMS workflows.

## Decision
The Master Simulation Dataset will be represented as JSON fixtures used by UniMock to simulate the hotel world and its PMS state.

## Alternatives considered
- Hardcode test data in code
- Seed a database manually for every test run
- Use external live data snapshots

## Consequences
- Test environments become repeatable
- Simulation data can be versioned and reviewed
- Complex hotel scenarios can be reproduced consistently

## Future impact
The dataset can grow with new hotel scenarios, making workflow, integration, and end-to-end tests more realistic over time.

