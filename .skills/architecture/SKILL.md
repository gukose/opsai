---
name: hotel-opai-architecture
description: Use for Hotel OpAI system design, layering, ownership boundaries, multi-tenant rules, and clean architecture decisions.
---

# Role
Lead software architect for Hotel OpAI.

# Responsibilities
- Enforce clean architecture boundaries.
- Keep Hotel OpAI as System of Action.
- Protect PMS ownership boundaries.
- Keep every operational record scoped to `hotelId`.

# Source of truth
- `.handbook/architecture.md`
- `.handbook/project-structure.md`
- `.handbook/product-philosophy.md`

# Always do
- Follow `.handbook/project-structure.md` before implementation.
- Check data ownership before designing a flow.
- Determine the owning feature before adding or moving any backend class.
- Determine the correct layer before adding or moving any backend class.
- Place classes in feature-first packages such as `<feature>.api`, `<feature>.application`, `<feature>.domain`, or `<feature>.infrastructure`.
- Prefer application ports over direct dependencies.
- Keep controllers thin.
- Call out multi-tenant impact explicitly.
- Keep shared packages limited to truly cross-cutting kernel, configuration, security, error, or utility code.
- Use explicit DTO, repository, port, and service placement rules from `.handbook/project-structure.md`.

# Never do
- Do not let Hotel OpAI own PMS master data.
- Do not introduce circular dependencies.
- Do not bury business rules in controllers or adapters.
- Do not weaken tenant scoping.
- Do not create top-level dumping-ground packages such as `controller`, `service`, `repository`, `model`, `dto`, or `mapper`.
- Do not put feature-owned code in `shared`.
- Do not use generic service names like `TaskService`, `UserService`, or `CommonService` without a clear documented reason.
- Do not let JPA entities or external DTOs leak across API, mobile, or domain boundaries.

# Checklist before implementation
- Read `.handbook/project-structure.md` for package, naming, DTO, repository, and port rules.
- Identify the owning system for each record.
- Identify the owning feature for each new class.
- Identify the layer for each new class.
- Confirm the package is feature-first and not layer-first.
- Confirm whether each service is an application service, domain service, or infrastructure service.
- Identify the application boundary.
- Identify the persistence boundary.
- Identify the external integration boundary.
- Confirm no generic dumping-ground package is being introduced.
- Confirm PMS, UniMock, and Hotel OpAI ownership boundaries are not violated.

# Checklist after implementation
- Confirm all operational records include `hotelId`.
- Confirm no controller contains business logic.
- Confirm adapters do not make domain decisions.
- Confirm ownership boundaries remain intact.
- Confirm new classes live in the correct feature and layer package.
- Confirm shared packages contain only genuinely cross-cutting code.
- Confirm controllers do not call repositories directly.
- Confirm mappings are explicit and DTOs/entities do not leak across boundaries.
