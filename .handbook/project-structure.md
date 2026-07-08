# Project Structure

Hotel OpAI uses feature-first backend packaging.

Feature-first means each product capability owns its API, application, domain, and infrastructure code together. The top-level package should describe the business capability, not the technical layer.

Do not move existing files just to satisfy this document. Apply this structure deliberately as Sprint 1 introduces PostgreSQL, authentication, authorization, tenant foundations, and repository implementations.

Sprint 1 migration target is `backend/src/main/kotlin/com/hotelopai/`. Current code under `org.ops.ai.opsai` should be moved feature by feature into that structure rather than by a blind mechanical rename.

## Recommended Backend Structure

Use this structure for new backend code:

```text
backend/
└── src/main/kotlin/com/hotelopai/
    ├── assistant/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── task/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── workflow/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── auth/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── hotel/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── employee/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── notification/
    │   ├── api/
    │   ├── application/
    │   ├── domain/
    │   └── infrastructure/
    ├── integration/
    │   └── unimock/
    ├── shared/
    └── common/
```

Additional product capabilities should follow the same pattern when they become real features, for example `voice`, `vision`, `reporting`, `concierge`, or `simulation`.

## Shared vs Common

Use `shared/` and `common/` for different purposes.

### `shared/`

Use `shared/` only for cross-cutting business abstractions and shared kernel concepts that are genuinely reused across features.

Examples:

- tenant and correlation primitives
- problem-details error primitives
- security contracts
- domain-independent event contracts
- value objects that are not owned by a single feature

### `common/`

Use `common/` for reusable technical code that is not part of the business domain.

Examples:

- helper classes
- constants
- extensions
- date/time formatters
- JSON utilities
- mapping helpers
- generic assertion utilities

### Strict rules

- Feature-owned code must not move into `shared/` just because it is reused twice.
- `shared/` must not become a dumping ground for feature services, repositories, DTOs, or domain models.
- `common/` must not contain feature logic, domain rules, or persistence rules.
- If code has a feature owner, it belongs in that feature even if it is reused by one other feature.

## Layer Responsibilities

Each feature may use `api`, `application`, `domain`, and `infrastructure`. Create only the layers that the feature actually needs.

### `api/`

Responsibilities:

- REST controllers
- request DTOs
- response DTOs
- API mappers
- request validation and transport-level error mapping
- no business logic

Controllers should translate HTTP into application calls. They must not contain orchestration, persistence logic, task state transitions, authorization shortcuts, or PMS synchronization decisions.

### `application/`

Responsibilities:

- use cases
- orchestration
- application services
- ports/interfaces
- transaction boundaries
- command/query objects when useful
- coordination between domain models, repositories, and external ports

Application code may depend on domain types and port interfaces. It should not depend directly on infrastructure implementations.

### `domain/`

Responsibilities:

- domain models
- value objects
- domain services
- business rules
- state machines
- policies
- domain events
- domain repository interfaces when the repository is part of the domain vocabulary

Domain code must not depend on Spring, HTTP clients, database frameworks, OpenAI SDKs, UniMock clients, JPA entities, or mobile/API DTOs.

### `infrastructure/`

Responsibilities:

- persistence entities
- repository implementations
- external REST clients
- security adapters
- configuration specific to the feature
- database mappers
- scheduled jobs or listeners that adapt technical events into application calls

Infrastructure implements ports. It must not own business rules.

### `shared/`

Responsibilities:

- only true cross-cutting utilities
- shared kernel types
- common error primitives
- common configuration primitives
- security primitives used by multiple features

`shared/` must not contain feature-specific logic, feature DTOs, feature repositories, feature services, or domain models that belong to a product capability.

## Where Classes Belong

| Class type | Package |
| --- | --- |
| Controller | `<feature>/api/` |
| Request DTO | `<feature>/api/` |
| Response DTO | `<feature>/api/` |
| Mapper | API mappers in `<feature>/api/`; persistence mappers in `<feature>/infrastructure/`; integration mappers beside the integration adapter |
| Application Service | `<feature>/application/` |
| Domain Service | `<feature>/domain/` |
| Repository Interface | `<feature>/domain/` when domain-owned, or `<feature>/application/` when it is a use-case port |
| Repository Implementation | `<feature>/infrastructure/` |
| JPA Entity | `<feature>/infrastructure/` |
| REST Client | `<feature>/infrastructure/` when feature-specific, or `integration/<system>/` when shared across features |
| Configuration | feature-specific config in `<feature>/infrastructure/`; cross-cutting config in `shared/` |
| Exceptions | feature-specific exceptions in the owning feature; cross-cutting error primitives in `shared/` |
| Validators | API request validators in `<feature>/api/`; domain validators/rules in `<feature>/domain/`; use-case validation in `<feature>/application/` |
| Ports | inbound and outbound application ports in `<feature>/application/`; domain repository ports may live in `<feature>/domain/` |
| Event | domain events in `<feature>/domain/`; application event handlers in `<feature>/application/`; infrastructure listeners in `<feature>/infrastructure/` |
| Policy | domain policies in `<feature>/domain/` |
| Specification | domain specifications in `<feature>/domain/` |
| Factory | domain factories in `<feature>/domain/`; application factories in `<feature>/application/` when assembling use-case commands |
| Converter | technical converters in `<feature>/infrastructure/` or `common/` only when they are pure utilities |
| Listener | technical listeners in `<feature>/infrastructure/` |
| DomainEvent | `shared/kernel/` for cross-cutting event abstractions; `<feature>/domain/` for feature-specific event payloads |

## API Versioning

All REST APIs must be versioned from Sprint 1.

- Use `/api/v1/...` for every public REST endpoint.
- Do not ship unversioned production REST routes.
- Keep API versioning explicit in controller mappings, client contracts, and documentation.
- When a breaking contract change is required later, introduce `/api/v2/...` rather than mutating `v1` in place.

## Service Naming Rules

Use names that describe the layer and responsibility.

### Application Service

Application services orchestrate use cases and transaction boundaries.

Examples:

- `CreateTaskApplicationService`
- `ConfirmAssistantTaskApplicationService`
- `AuthenticateUserApplicationService`
- `RegisterEmployeeApplicationService`

### Domain Service

Domain services contain domain rules that do not naturally belong to a single entity or value object.

Examples:

- `SlaCalculator`
- `AssignmentPolicy`
- `TaskStateMachine`
- `PermissionPolicy`

### Infrastructure Service

Infrastructure services talk to external systems or technical resources.

Examples:

- `RestUniMockClient`
- `OpenAiClient`
- `PostgresTaskRepository`
- `JwtTokenAdapter`

Avoid generic names unless there is a clear, documented reason:

- `TaskService`
- `UserService`
- `CommonService`
- `HelperService`
- `Manager`

Generic service names blur ownership and make it unclear whether a class contains use-case orchestration, domain rules, or adapter behavior.

## Repository Rules

- Domain and application layers depend on repository interfaces, not implementations.
- Infrastructure implements repository interfaces.
- Controllers never call repositories directly.
- JPA entities must not leak into controllers, API DTOs, mobile DTOs, or domain APIs.
- Repository interfaces should describe domain/application needs, not raw database tables.
- Repository implementations should handle persistence mapping explicitly.

## DTO Rules

- API DTOs live in the feature `api/` layer.
- External integration DTOs live in the feature `infrastructure/` layer or in `integration/<system>/`.
- Domain models are not DTOs.
- JPA entities are not DTOs.
- Mapping between API DTOs, application commands, domain models, persistence entities, and external DTOs must be explicit.
- Do not use one object as API payload, domain model, and database entity.

## Integration Packages

Use `integration/<system>/` for external systems shared by multiple features.

Example:

```text
backend/
└── src/main/kotlin/com/hotelopai/
    └── integration/
        └── unimock/
            ├── RestUniMockClient.kt
            ├── UniMockRoomDto.kt
            ├── UniMockGuestRequestDto.kt
            └── UniMockMapper.kt
```

Feature application services should depend on feature-owned ports. They should not call integration clients directly unless the client is itself the port implementation wired at the boundary.

## Module Dependency Rules

Feature modules may depend only on the modules listed below.

| Module | Allowed dependencies |
| --- | --- |
| `assistant` | `task`, `workflow`, `auth`, `shared`, `common` |
| `task` | `workflow`, `auth`, `shared`, `common` |
| `workflow` | `shared`, `common` |
| `auth` | `hotel`, `shared`, `common` |
| `hotel` | `shared`, `common` |
| `employee` | `hotel`, `auth`, `shared`, `common` |
| `notification` | `auth`, `shared`, `common` |
| `integration/unimock` | `shared`, `common` |

Rules:

- Feature modules must not depend on sibling feature infrastructure packages directly.
- `shared/` may depend only on `common/` and other cross-cutting primitives, never on feature packages.
- `common/` must not depend on feature packages.
- The UniMock module must never depend on Hotel OpAI business modules.
- If a dependency is not listed, it is not allowed without an explicit architecture update.

## Discouraged Top-Level Packages

Do not create top-level layer-first packages such as:

```text
backend/src/main/kotlin/com/hotelopai/controller/
backend/src/main/kotlin/com/hotelopai/service/
backend/src/main/kotlin/com/hotelopai/repository/
backend/src/main/kotlin/com/hotelopai/model/
backend/src/main/kotlin/com/hotelopai/dto/
```

Layer-first structures look tidy at the beginning, but they age poorly in a long-lived SaaS product:

- feature changes scatter across unrelated folders
- ownership becomes unclear
- generic packages become dumping grounds
- domain boundaries are harder to see
- tenant and authorization rules become easier to bypass
- PMS, UniMock, and Hotel OpAI ownership boundaries become easier to blur
- deleting, extracting, or scaling a feature becomes unnecessarily risky

Feature-first packaging keeps related code close, makes product ownership visible, and lets each capability evolve without turning the backend into one large shared layer.

## Class Placement Checklist

Before creating any class, answer:

- Which feature owns this class?
- Which layer does it belong to?
- Is it domain, application, api, or infrastructure?
- Is it reusable or feature-specific?
- Does it introduce duplicated logic?
- Does it violate PMS, UniMock, or Hotel OpAI ownership boundaries?
- Does it need a port/interface, or is it an implementation of one?
- Does it expose a JPA entity or external DTO beyond its boundary?
- Does the name describe the responsibility clearly?

If the feature, layer, or ownership boundary is unclear, do not create the class yet. Resolve the design first.
