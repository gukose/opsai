# Backend Package Structure

## Goal

The Kotlin Spring Boot backend should use clear package boundaries so AI workflows, task operations, security, persistence, and UniMock integration remain testable and independently evolvable.

Recommended root package:

`com.hotelopai`

## Package Layout

```text
com.hotelopai
├── api
├── application
├── domain
├── infrastructure
├── integrations
├── security
├── shared
└── config
```

## API Layer

Package:

`com.hotelopai.api`

Responsibilities:

- REST controllers
- request/response DTOs
- validation annotations
- authentication principal mapping
- API error mapping

Should not contain:

- business rules
- persistence code
- OpenAI calls
- UniMock HTTP calls

Suggested packages:

```text
api.conversation
api.tasks
api.notifications
api.attachments
api.health
```

## Application Layer

Package:

`com.hotelopai.application`

Responsibilities:

- use cases
- transaction boundaries
- orchestration between domain services and ports
- state machine execution
- idempotency handling

Suggested packages:

```text
application.conversation
application.tasks
application.assignment
application.notifications
application.pms
```

Example services:

- `ConversationApplicationService`
- `CreateTaskFromDraftUseCase`
- `AssignTaskUseCase`
- `CompleteTaskUseCase`
- `SendNotificationUseCase`

## Domain Layer

Package:

`com.hotelopai.domain`

Responsibilities:

- entities
- value objects
- domain services
- domain events
- state transition rules
- task validation rules

Suggested packages:

```text
domain.conversation
domain.task
domain.assignment
domain.notification
domain.hotel
```

Domain code should not depend on Spring, HTTP clients, database frameworks, or OpenAI SDKs.

## Infrastructure Layer

Package:

`com.hotelopai.infrastructure`

Responsibilities:

- PostgreSQL persistence
- repository implementations
- migrations
- event outbox
- file storage adapters
- clock/id generation

Suggested packages:

```text
infrastructure.persistence
infrastructure.outbox
infrastructure.storage
infrastructure.time
```

## Integrations Layer

Package:

`com.hotelopai.integrations`

Responsibilities:

- external service adapters
- OpenAI adapter
- UniMock REST client
- retry and timeout configuration
- external DTO mapping

Suggested packages:

```text
integrations.openai
integrations.unimock
```

UniMock integration should be exposed to the application layer through ports such as:

- `RoomLookupPort`
- `AssetLookupPort`
- `PmsMutationPort`
- `GuestRequestPort`

## Security Layer

Package:

`com.hotelopai.security`

Responsibilities:

- JWT filter
- authentication configuration
- authorization policies
- current user provider
- password hashing if local auth exists

Security should expose application-friendly identity objects, not framework internals.

## Shared Layer

Package:

`com.hotelopai.shared`

Allowed content:

- common errors
- result types
- pagination
- correlation IDs
- idempotency utilities

Avoid turning `shared` into a dumping ground.

## PostgreSQL Boundary

Hotel OpAI PostgreSQL stores operational data only:

- tasks
- assignments
- notifications
- approvals
- conversation state
- AI logs
- audit logs
- PMS integration events

Do not store PMS master data as authoritative records:

- rooms
- room types
- occupancy
- assets
- minibar master data
- public areas
- PMS guest data

If display snapshots are needed, store them as denormalized immutable snapshots attached to operational records.

## Testing Strategy

Recommended test layers:

- domain unit tests for task and conversation transitions
- application tests with mocked ports
- API tests using Spring MockMvc or WebTestClient
- integration tests for repositories
- contract tests for UniMock client DTOs
- schema validation tests for AI responses

Critical tests:

- invalid AI output cannot create task
- ambiguous room triggers follow-up
- confirmed draft creates exactly one task
- PMS mutation failure does not corrupt task state
- task status transitions reject illegal moves
