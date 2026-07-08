# Sprint 2 - UniMock PMS Simulator

## Goal
Build UniMock as a separate runnable Spring Boot module and establish the PMS simulator boundary Hotel OpAI will use for development and testing.

## Business value
Provides a deterministic hotel world for PMS-like reads, writes, and verification without letting Hotel OpAI own PMS master data.

## Architecture impact
- Adds `unimock/` as a separate runnable module in the same repository.
- Keeps UniMock data in its own `unimock` PostgreSQL schema.
- Uses JSON seed loading as the source of simulation state for the simulator.
- Keeps Hotel OpAI operational data separate from PMS master data.
- Introduces the REST integration boundary Hotel OpAI must use for PMS access.

## Backend tasks
- Add a client boundary for UniMock access.
- Consume UniMock only through ports and REST adapters.
- Keep controllers and application services out of PMS master-data ownership.

## Mobile tasks
- Continue using backend APIs that depend on UniMock-backed data where needed.
- Do not add simulator-specific UI behavior.

## AI tasks
- Keep AI out of UniMock simulation execution.
- Preserve PMS context access for later assistant use.

## UniMock tasks
- Create a separate Spring Boot application under `unimock/`.
- Implement JSON seed loading and reset behavior.
- Expose PMS read APIs for rooms, assets, issue types, public areas, reservations, guests, occupancy, room status, and events.
- Expose PMS update APIs for room status, guest requests, minibar, maintenance, and events.
- Implement `pms_mock_verification_log`.
- Add admin endpoints for simulation load, reset, and current simulation inspection.
- Keep scenario files as reserved JSON assets only; do not implement a Scenario Engine yet.

## Database tasks
- Add Flyway migrations for the `unimock` schema.
- Add tables for simulation metadata, master data, operational data, and verification logging.
- Add the verification table used to trace PMS update requests and responses.

## Infrastructure tasks
- Extend local Docker Compose to run Hotel OpAI and UniMock together with PostgreSQL.
- Add integration-test coverage for the UniMock boundary and seed lifecycle.

## UI tasks
- Keep the UI unchanged unless it needs to display UniMock-backed operational data already exposed by backend APIs.

## Documentation tasks
- Document the UniMock module layout, seed structure, REST contract, verification log behavior, and local startup flow.

## Testing tasks
- Verify JSON seed load/reset idempotency.
- Verify PMS read and update APIs.
- Verify verification-log writes for update operations.
- Verify Hotel OpAI uses UniMock only through the integration boundary.

## Risks
- UniMock can accidentally become a second product if scenario execution is added too early.
- Contract drift between Hotel OpAI and UniMock would block later workflow and assistant work.

## Definition of Done
- UniMock runs as a separate Spring Boot module.
- The `unimock` schema is managed by Flyway.
- Seed data loads deterministically and can be reset.
- PMS read/update APIs and verification logging work.
- Hotel OpAI integrates through the UniMock boundary only.

## Dependencies
- Depends on Sprint 1 platform, PostgreSQL, Flyway, and repository conventions.
- Enables Sprint 3 workflow and task integration against a deterministic PMS simulator.
