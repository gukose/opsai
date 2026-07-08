# Sprint 12 - Simulation Engine Foundation

## Goal
Introduce the Simulation Engine using the Master Simulation Dataset.

## Business value
Enables controlled hotel-operations simulations over time for demos, training, regression testing, and future scenario validation.

## Architecture impact
- Adds Simulation Engine as a separate capability from UniMock.
- UniMock remains the PMS simulator; the Simulation Engine drives operational scenarios over time using the Master Simulation Dataset.
- Sprint 12 introduces the engine foundation only, not a full scenario authoring suite.

## Backend tasks
- Implement Simulation Engine domain/application foundation for simulation runs, clocks, steps, emitted operations events, and run state.
- Add adapters that can drive Hotel OpAI workflows and UniMock PMS changes through existing public boundaries.
- Keep engine controls permissioned and separate from production workflows.

## Mobile tasks
- Add minimal internal/admin simulation run visibility if needed for demos.
- Do not expose simulation controls to ordinary hotel staff.

## AI tasks
- Add placeholders for AI-assisted simulation evaluation but keep execution deterministic.
- Do not use AI to decide simulation events in this sprint.

## UniMock tasks
- Allow Simulation Engine to call UniMock update APIs as an external driver.
- Keep UniMock responsible only for PMS state and verification logs.

## Database tasks
- Add Flyway migrations for simulation datasets, simulation runs, simulation steps, emitted events, run logs, and dataset versions.
- Store references to Master Simulation Dataset versions.

## Infrastructure tasks
- Add local simulation configuration, time-control settings, and integration-test fixtures.
- Ensure simulation runs are isolated from production profiles.

## UI tasks
- Add minimal admin-only status views if required.
- Avoid building a full scenario editor.

## Documentation tasks
- Document Simulation Engine vs UniMock boundaries, Master Simulation Dataset format, run lifecycle, and safety controls.

## Testing tasks
- Verify simulation run creation, stepping, pause/resume if included, event emission, and isolation.
- Verify Simulation Engine drives UniMock and Hotel OpAI only through public integration boundaries.

## Risks
- Confusing Simulation Engine with UniMock can blur ownership and create duplicate PMS logic.
- Time simulation can create hard-to-debug data unless runs are isolated and auditable.

## Definition of Done
- Simulation Engine foundation can run a basic deterministic operations simulation over time.
- UniMock remains a separate PMS simulator.
- Simulation runs are auditable, permissioned, and isolated from production behavior.

## Dependencies on previous sprints
- Depends on Sprint 2 UniMock, Sprint 3 Workflow/Task Engine, Sprint 8 notifications, and Sprint 9 reporting foundation.
