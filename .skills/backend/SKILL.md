---
name: hotel-opai-backend
description: Use for Hotel OpAI Kotlin Spring Boot backend design, REST APIs, application services, configuration, and persistence boundaries.
---

# Role
Backend engineer for Hotel OpAI.

# Responsibilities
- Implement REST APIs with thin controllers.
- Keep business logic in application services and domain objects.
- Maintain task, assistant, and PMS integration boundaries.
- Support multi-tenant SaaS behavior.

# Source of truth
- `.handbook/backend.md`
- `.handbook/architecture.md`
- `.handbook/project-structure.md`
- `.handbook/task-engine.md`
- `.handbook/unimock.md`

# Always do
- Follow `.handbook/project-structure.md` before implementation.
- Place backend classes in feature-first packages.
- Determine the owning feature and layer before creating a class.
- Prefer clean, testable application services.
- Keep domain validation in the domain model.
- Use ports for external systems.
- Scope operational data to `hotelId`.
- Use explicit mapping between API DTOs, domain models, persistence entities, and external DTOs.

# Never do
- Do not put orchestration in controllers.
- Do not duplicate task creation logic.
- Do not let infrastructure decide business rules.
- Do not write code that makes Hotel OpAI a second PMS.
- Do not create top-level `controller`, `service`, `repository`, `model`, or `dto` packages.
- Do not call repositories directly from controllers.
- Do not leak JPA entities into controllers, API DTOs, mobile DTOs, or domain APIs.
- Do not use generic service names like `TaskService`, `UserService`, or `CommonService` without a clear documented reason.

# Checklist before implementation
- Read `.handbook/project-structure.md`.
- Identify the owning feature and layer.
- Identify the use case and owning service.
- Identify required domain invariants.
- Identify integration points and ports.
- Identify tenant scope and idempotency needs.
- Identify DTO/entity/mapper boundaries.
- Confirm PMS, UniMock, and Hotel OpAI ownership boundaries.

# Checklist after implementation
- Verify controller remains thin.
- Verify business rules live outside the controller.
- Verify classes live in the correct feature-first package.
- Verify repository interfaces are depended on from domain/application and implemented in infrastructure.
- Verify mapping is explicit and entities/DTOs do not leak across boundaries.
- Verify tests cover happy path and failure path.
- Verify no cross-boundary ownership drift.
