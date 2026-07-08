# Sprint 0 - Architecture & Product Foundation

## Goal
Establish the product, architecture, UI, and delivery foundations before feature implementation.

## Business value
Creates a stable decision base so later sprint work builds toward the same hotel operations product.

## Architecture impact
- Confirms multi-tenant SaaS, PMS ownership, Hotel OpAI ownership, AI boundaries, UniMock responsibilities, and Simulation Engine responsibilities.
- Defines Clean Architecture package direction and keeps `docs/ui-reference/` as the UI source of truth.
- Establishes that infrastructure work is delivered inside feature sprints.

## Backend tasks
- Document backend package boundaries for API, application, domain, infrastructure, integrations, security, shared, and config.
- Define repository port conventions before PostgreSQL implementation.
- Define authentication, authorization, and tenant-context boundaries.

## Mobile tasks
- Document navigation, auth shell, task surfaces, assistant surfaces, and reusable component conventions.
- Identify mobile screens needed for Sprints 1-4.

## AI tasks
- Define Assistant, OpenAI, voice, and vision integration boundaries.
- Document that AI suggestions must enter workflow validation and confirmation before task creation.

## UniMock tasks
- Define UniMock as a separate runnable PMS simulator in the same repository.
- Document that UniMock owns PMS simulation data and is distinct from the Simulation Engine.

## Database tasks
- Document Hotel OpAI-owned data vs PMS-owned data.
- Select Flyway as the project migration tool.
- Define tenant scoping rules and schema ownership principles.

## Infrastructure tasks
- Define local development expectations for Docker Compose.
- Document environment configuration conventions for backend, mobile, UniMock, PostgreSQL, and later AI providers.

## UI tasks
- Align planned mobile UI with `docs/ui-reference/`.
- Define reusable UI primitives without implementing application code.

## Documentation tasks
- Maintain handbook, ADRs, roadmap, sprint plans, and backlog alignment.
- Capture architecture decisions for PostgreSQL, UniMock, AI abstraction, and infrastructure strategy.

## Testing tasks
- Establish baseline expectations for unit, integration, contract, mobile, and documentation consistency checks.
- Confirm existing tests remain the starting point for later sprint validation.

## Risks
- Architecture drift if sprint plans are not kept aligned with ADRs.
- Underestimating early persistence work may slow Sprint 1.

## Definition of Done
- Roadmap and sprint documents reflect the current architecture decisions.
- System boundaries are documented and consistent.
- No application code is implemented in this sprint planning revision.

## Dependencies on previous sprints
- None.
