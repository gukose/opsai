# Backend

The backend is Kotlin with Spring Boot and PostgreSQL as the intended production persistence layer.

## Backend Responsibilities

- Versioned REST APIs for mobile and web clients under `/api/v1`
- assistant conversation orchestration
- task lifecycle management
- PMS integration through UniMock in development
- authentication and tenant-aware request handling
- request correlation and structured logging
- RFC 7807 problem-details error responses

## Package Structure

Use feature-first Clean Architecture boundaries. The canonical layout is defined in
[Project Structure](project-structure.md) and the backend code must follow it:

- `assistant/api`, `assistant/application`, `assistant/domain`, `assistant/infrastructure`
- `task/api`, `task/application`, `task/domain`, `task/infrastructure`
- `workflow/domain` and `workflow/task` for workflow state definitions
- `auth/api`, `auth/application`, `auth/domain`, `auth/infrastructure`
- `hotel/application`, `hotel/domain`, `hotel/infrastructure`
- `employee/application`, `employee/domain`, `employee/infrastructure`
- `integration/openai` and `integration/unimock`
- `shared` for cross-cutting business abstractions and shared kernel concepts
- `common` for utilities, helpers, constants, and generic reusable code
- `config` for bootstrapping and environment wiring

## Rules

- Controllers must not contain business logic.
- Application services must depend on interfaces, not concrete adapters.
- Domain objects must enforce invariants.
- Infrastructure must not decide business behavior.
- Every incoming request must receive a correlation identifier and request identifier.
- Authentication should use JWT access tokens and refresh sessions with rotation.
- Logout must revoke the active refresh session rather than deleting historical session data.
- Sprint 1 uses a simplified identity model: `User` and `Employee` each belong to one `Hotel`.
- Multi-hotel membership will be introduced later through a dedicated membership model.

## Persistence

Operational data belongs to Hotel OpAI.
Master data belongs to UniMock or the real PMS later.

Sprint 1 persistence should treat refresh tokens as session records, not as a generic token blob table.
Future persistence entities should live under `<feature>/infrastructure/persistence/` so domain objects remain persistence-agnostic.

### ID Strategy

Use one ID strategy across all business entities:

- UUID v7
- PostgreSQL `uuid` column type
- application-generated IDs
- no auto-increment primary keys for business entities

Why UUID v7:

- it is globally unique without coordination
- it preserves index locality better than random UUIDs
- it works cleanly across future distributed services, imports, and offline object creation
- it avoids later primary-key migrations when the platform splits into more modules or services

Where IDs are generated:

- in the application layer or domain factory before persistence
- never by database auto-increment

Future compatibility:

- this supports future multi-service architectures, event publication, and cross-system references without rewriting identifiers later
- the generator is an implementation detail in `shared/kernel`; it must preserve UUID v7 semantics but does not become part of the business domain

### Base Audit Model

Every persistent business entity should follow the same audit convention:

- `id`
- `version`
- `createdAt`
- `createdBy`
- `updatedAt`
- `updatedBy`

Optimistic locking:

- use the `version` field for optimistic locking
- reject stale updates instead of silently overwriting newer data

Audit strategy:

- populate audit fields in the application or persistence boundary
- keep the model consistent across all business entities
- do not introduce soft delete in Sprint 1
- `createdBy` and `updatedBy` remain actor references, represented as strings for now or a future lightweight `ActorId` abstraction if the auth model later needs it

Soft delete is intentionally postponed to a future sprint so the initial model stays simple and query behavior remains explicit.

### Permission and Reference Model

- Permissions are global capability metadata.
- Permissions are not tenant-scoped.
- Roles belong to one `Hotel`.
- Roles should reference permissions by stable permission references, not by embedding Permission aggregates.
- In Sprint 1 the reference form may remain UUID-based, but the domain should treat them as references rather than as owned child objects.

### Hotel-Configurable Reference Data

- Departments are hotel-configurable.
- Skills are hotel-configurable.
- Future hotel templates may seed departments, skills, and other reference data from predefined template definitions.

### Naming Conventions

Use a single naming convention across the platform:

- Tables: `snake_case`, singular logical ownership preferred where it improves clarity
- Columns: `snake_case`
- Foreign keys: `fk_<table>_<referenced_table>`
- Indexes: `idx_<table>_<column>`
- Unique constraints: `uk_<table>_<column>`
- Primary keys: `pk_<table>`
- Flyway migrations: `V<version>__<description>.sql`

Examples:

- `pk_task`
- `fk_task_employee`
- `idx_task_status`
- `uk_user_email`

## Error Handling

- Return RFC 7807 Problem Details responses.
- Reject invalid state transitions in the domain.
- Prefer explicit validation over silent correction.
