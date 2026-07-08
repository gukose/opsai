# Architecture

Hotel OpAI uses feature-first Clean Architecture with clear ownership boundaries.

The required backend packaging rules are defined in [Project Structure](project-structure.md). Follow that document before adding Sprint 1 classes, modules, repositories, migrations, authentication components, or tenant foundations.

## Layers

1. API layer
2. Application layer
3. Domain layer
4. Infrastructure layer

Layers live inside feature packages. The backend should be organized around product capabilities such as `assistant`, `task`, `workflow`, `auth`, `hotel`, `employee`, and `notification`, not around top-level technical folders.

See [Project Structure](project-structure.md) for the required package structure, class placement rules, DTO rules, repository rules, and service naming rules.

## Boundary Rules

- Controllers stay thin.
- Application services orchestrate use cases.
- Domain models contain business rules and validation.
- Infrastructure implements repositories, HTTP clients, and external adapters.
- New backend classes must be placed in the owning feature and correct layer.
- Shared packages are allowed only for genuinely cross-cutting kernel, configuration, security, error, or utility code.
- Common packages are allowed only for pure utilities, helpers, constants, extensions, and other reusable technical code.
- Do not create generic top-level packages such as `controller`, `service`, `repository`, `model`, `dto`, or `mapper`.
- Controllers must never call repositories directly.
- JPA entities must not leak into controllers, API DTOs, mobile DTOs, or domain APIs.
- Domain models must remain persistence-agnostic.
- Future persistence entities belong under `<feature>/infrastructure/persistence/`.
- Domain code must never depend on JPA or database annotations.

## Package Structure

Use feature-first packages:

```text
com.hotelopai
├── assistant
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── task
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── workflow
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── shared
│   ├── config
│   ├── error
│   ├── kernel
│   ├── security
│   └── util
└── common
    ├── constants
    ├── extensions
    └── util
```

Layer-first top-level packages are discouraged because they hide feature ownership and become dumping grounds as the SaaS platform grows.

Shared kernel abstractions such as `DomainEvent`, `AuditedRecord`, tenant context, correlation context, and UUID generation helpers may live under `shared/kernel` when they need to be reused across features.

## Identity Model

Sprint 1 uses a simplified MVP identity model:

- `User` belongs to one `Hotel`
- `Employee` belongs to one `Hotel`

This is an intentional temporary simplification. Future multi-hotel membership will be introduced later through a dedicated membership model such as `user_hotel_membership` without changing the tenant-scoping rule that every operational record belongs to one hotel.

## Tenant-Configurable Reference Data

- Departments are hotel-configurable.
- Skills are hotel-configurable.
- Future hotel templates may seed standard departments, skills, and related reference data into a hotel from predefined template definitions.

## Data Ownership

### Hotel OpAI owns

- tasks
- assignments
- notifications
- approvals
- logs
- conversation state

### Hotel OpAI does not own

- rooms
- room types
- occupancy
- assets
- issue types
- minibar
- public areas
- guest requests
- events

### UniMock owns

- PMS-like master data and simulated hotel state

## Multi-Tenancy

All operational entities must include `hotelId`.
Application services must always resolve work in the context of a hotel.

## API Versioning

All external REST APIs must be versioned from Sprint 1.

- Public endpoints use `/api/v1/...`
- Breaking contract changes require a new version path
- Versioning must be reflected in controller mappings, API docs, and mobile client contracts

## Observability

Sprint 1 establishes the observability baseline for the platform.

- Every incoming request receives a correlation identifier.
- Request ID and correlation ID must be propagated through logs and downstream calls.
- Logs must be structured so production events are searchable by hotel, request, and correlation identifiers.
- Distributed tracing hooks should be prepared even if full tracing infrastructure is added later.

## Error Model

Use RFC 7807 Problem Details as the standard API error shape.

- validation errors
- authentication errors
- authorization errors
- not found
- conflict
- internal server error

Controllers and exception handlers should translate domain and application failures into stable problem-details responses, not ad hoc JSON error envelopes.

## Workflow State Machine

The Workflow Engine will become the single source of truth for operational state transitions across the platform.

- State transitions must be explicit.
- Illegal transitions must be rejected.
- Feature modules must not invent independent lifecycle rules that conflict with the Workflow Engine.
- Sprint 3 is the planned implementation point for this foundation.

## Business Event Model

The platform will publish business events as the long-term integration contract for downstream modules.

Examples:

- `TaskCreated`
- `TaskAssigned`
- `TaskCompleted`
- `GuestCheckedOut`
- `GuestCheckedIn`
- `MaintenanceCompleted`
- `RoomStatusChanged`

Downstream consumers:

- Notifications
- Dashboards
- Reporting
- Analytics
- AI

These modules should consume business events instead of querying business tables directly whenever the event model is available. Implementation is planned for a future sprint.

## Integration Strategy

- Hotel OpAI calls UniMock for PMS state lookup and synchronization.
- Hotel OpAI never writes directly to PMS master data.
- Task changes that affect PMS state must be synchronized through explicit integration boundaries.
