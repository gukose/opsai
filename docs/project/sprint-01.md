# Sprint 1 - Platform Foundation

## Goal
Establish the platform foundation for Hotel OpAI: repository structure, PostgreSQL, Flyway, Docker, profiles, Testcontainers, feature-first backend packaging, core identity persistence, JWT authentication, refresh sessions, RBAC authorization, RFC 7807 errors, and current-user access.

## Business value
Creates the durable SaaS baseline required before simulator, workflow, assistant, and mobile integration work can scale safely.

## Architecture impact
- Confirms `backend/`, `mobile/`, `unimock/`, `docs/`, `.handbook/`, `.skills/`, `docker/`, and `scripts/` as the platform structure.
- Standardizes PostgreSQL, Flyway, UUID v7, audit columns, and tenant scoping conventions.
- Establishes the single-hotel identity MVP: one `User` belongs to one `Hotel`, one `Employee` belongs to one `Hotel`.
- Introduces `RefreshSession` as the refresh-token persistence model with rotation and revocation.
- Introduces JWT authentication and permission-based authorization using the existing role and permission model.
- Makes RFC 7807 Problem Details the standard API error shape.
- Adds hotelId discipline and observability foundations that later modules must reuse.

## Backend tasks
- Keep the backend in the `backend/` module with feature-first packages.
- Persist `Hotel`, `User`, `Employee`, `Department`, `Role`, `Permission`, `Skill`, and `RefreshSession`.
- Implement JWT login, refresh, logout, and current-user endpoints under `/api/v1/auth`.
- Implement RBAC authorization helpers for permission checks.
- Keep controllers thin and push use-case logic into application services.

## Mobile tasks
- Keep mobile code in the `mobile/` module.
- Prepare for backend auth integration without redesigning the UI.
- Use backend mode switches and Authorization headers when auth flows are enabled.

## AI tasks
- No OpenAI integration yet.
- Keep assistant and AI contracts stable for later sprints.

## UniMock tasks
- Keep UniMock out of Sprint 1 runtime paths.
- Preserve the contract boundary so Sprint 2 can add the PMS simulator without changing Hotel OpAI ownership rules.

## Database tasks
- Create PostgreSQL schemas and Flyway baseline migrations.
- Add foundation tables, constraints, indexes, and audit columns for core identity and refresh sessions.
- Use UUID v7 identifiers for all business entities.
- Keep hotel scoping explicit in the persistence model.

## Infrastructure tasks
- Add Docker Compose for local PostgreSQL.
- Configure local, test, and prod profiles.
- Add Testcontainers for repository and auth integration tests.
- Add CORS, structured logging, correlation IDs, and RFC 7807 support.

## UI tasks
- Keep the UI aligned with `docs/ui-reference/`.
- Avoid redesigning screens while the platform foundation is changing underneath.

## Documentation tasks
- Document repository structure, package boundaries, ID strategy, audit strategy, auth model, authorization rules, and tenant isolation.

## Testing tasks
- Verify Flyway, PostgreSQL startup, and schema creation.
- Verify repository persistence and uniqueness constraints.
- Verify login, refresh, logout, `/me`, and RBAC authorization.
- Verify RFC 7807 error responses and current-user tenant context.

## Risks
- Identity and tenant conventions are expensive to change after production launch.
- Permission checks based on JWT claims are acceptable for this sprint but become stale until refresh if server-side permissions change.
- Package reorganization can create accidental breakage if feature ownership is not followed strictly.

## Definition of Done
- Backend starts locally against PostgreSQL.
- Flyway migrations run from an empty database.
- Core identity and refresh-session persistence work.
- JWT auth, refresh rotation, logout, `/me`, and RBAC authorization work.
- RFC 7807 responses, CORS, and observability conventions are in place.
- Backend tests pass.

## Dependencies
- Depends on Sprint 0 architecture, packaging, and platform decisions.
- Provides the baseline for Sprint 2 UniMock and Sprint 3 task/workflow work.

## Current status
- Done or nearly done.
- Any remaining work should be treated as minor completion work, not architecture redesign.
