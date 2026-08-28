# Project Context

Use this file as the repository source of truth for future development tasks. Do not re-scan the whole repository; inspect only files directly relevant to the requested change. Preserve the conventions below unless a task explicitly changes them.

## Repository shape and architecture

- Gradle multi-module workspace: `backend` (main API) and `unimock` (internal/demo PMS simulator). Java 21, Kotlin 2.3, Spring Boot 4.1. Root Gradle also orchestrates TypeScript SDK checks.
- `mobile` is an Expo 54 / React Native 0.81 / React 19 / TypeScript 5.9 app targeting native and web. Node 22+ is required.
- `sdk/typescript` is a private generated client consumed by `mobile` through a local file dependency. `docs/api/openapi-v1.yaml` is its contract source; never hand-edit `sdk/typescript/src/generated/**`.
- Backend code is feature-first under `com.hotelopai.<feature>`, normally split into `api`, `application`, `domain`, and `infrastructure`. Controllers call application services, application code depends on ports/interfaces, and infrastructure implements adapters. Domain code is framework-free. ArchUnit enforces these boundaries and feature-cycle rules.
- Cross-cutting code lives under `shared`, `config`, `observability`, `scheduler`, and `outbox`. External systems are behind provider/port abstractions; deterministic internal providers are the safe defaults and external providers are generally disabled/profile-gated.
- UniMock hosts the business-facing PMS Demo Console at `/`. It retrieves canonical rooms through the Hotel OpAI demo integration endpoint, stores console history in its dedicated `pms_demo_console_event` table (independent of legacy simulations), and forwards events through a demo-key-protected Hotel OpAI endpoint; Hotel OpAI deduplicates provider event IDs before delegating actionable events to existing housekeeping, room-state, and task engines.

## Backend and API conventions

- Spring MVC REST endpoints use `/api/v1/**`; staff/admin operations use `/api/v1/internal/**`. Keep hotel/tenant scope derived from the authenticated context, never client-supplied.
- Controllers own HTTP DTO mapping and validation; application services own use cases and transactions; repositories own persistence mapping. Prefer constructor injection, Kotlin data classes, UTC `Instant`, UUID identifiers (UUIDv7 for new records), and explicit domain state transitions.
- Public errors use RFC 7807 `application/problem+json` via `ProblemDetailFactory`/feature exception handlers. All v1 responses include `X-API-Version: v1`; clients send `X-Correlation-Id`.
- Keep `docs/api/openapi-v1.yaml`, compatibility metadata/changelog, generated SDK, and mobile adapter aligned for public API changes. Preserve existing endpoints/response shapes unless compatibility work is explicit.
- Use bounded pagination/filter DTOs for collection searches. Mutation workflows that may be retried use an explicit idempotency key and a database uniqueness boundary. Durable async work uses the transactional outbox and distributed scheduler leases where applicable.

## Authentication and authorization

- Spring Security is stateless. Login and refresh are public; protected APIs use bearer JWTs signed with HS256. Access tokens last 15 minutes by default; opaque refresh tokens are stored server-side as hashes and last 30 days by default. Passwords use BCrypt cost 12.
- JWTs carry user/session/hotel identity plus role and permission codes; `CurrentUserContextResolver` is the canonical backend accessor. Authorization is permission-based through `@PreAuthorize(PermissionExpressions.*)` and `permissionGuard`, with additional hotel/role/employee visibility enforced in application/repository queries.
- The mobile app restores sessions through `AppBootstrapProvider`/`SessionService`, refreshes on eligible GET 401s, uses Expo SecureStore on native, and localStorage on web. Native storage separates tokens from server-derived user metadata. UI permission checks (`hasPermission`) control visibility only; backend authorization remains authoritative.
- `app_user` remains the single user identity model. `user_hotel_membership` supplies per-hotel department/status/dates; `user_hotel_role` and `user_hotel_skill` supply hotel-specific assignments. Login is by hotel code and resolves either the user's home hotel or an active membership. Target-hotel admin authorization must use `permissionGuard.hasHotelPermission(...)`.

## Database and migrations

- PostgreSQL 16 is the persistence target, configured through a Hikari datasource. Both Spring Data JPA and `NamedParameterJdbcTemplate` repositories are used: JPA for aggregate/entity persistence and JDBC for complex reads, coordination, and operational workflows.
- Hibernate uses `ddl-auto: validate` and open-in-view is disabled. Flyway is the only schema-change mechanism, using immutable, sequential `backend/src/main/resources/db/migration/V<number>__description.sql` migrations. Add a new migration; never edit an applied one.
- Tables are predominantly hotel-scoped and use explicit foreign keys, unique constraints, indexes, `timestamptz`, and audit/version columns where appropriate. Enforce tenant and idempotency invariants in both queries and database constraints.

## Frontend patterns

- The app is a functional-component React Native codebase with local hooks/services rather than Redux or a navigation framework. `App.tsx` gates loading/login/authenticated states; the authenticated shell switches compact Home/Assistant, Tasks, Knowledge, and Profile surfaces.
- Keep API-specific DTOs/adapters under `mobile/src/api`; use the generated SDK through `MobileHotelOpAiClient`, which centralizes bearer tokens, timeouts, safe GET retries, refresh, correlation IDs, API-version checks, and normalized `AppApiError` handling.
- State is organized into focused hooks/services (`use*State`, domain services), with offline cache/mutation queues and drafts scoped by hotel and user. Clear scoped offline data on logout.
- Reuse design tokens from `mobile/src/theme/tokens.ts`, `StyleSheet.create`, Lucide icons, and existing components. Layout is responsive at `<600` phone, `600–1024` tablet, and `>1024` desktop (max shell width 1360).
- `docs/ui-reference/**` images and behavior notes are the UI source of truth. Match them rather than redesigning. The visual language is premium, compact, card-based, with consistent tokens; keep only the conversation scrollable, pin the composer and bottom navigation, show task previews inline, and avoid unnecessary modals or duplicated UI logic.
- Permission-gated master-data administration is integrated into the existing Operations surface. A nine-step wizard submits one transactional onboarding request; employee membership, hotel roles/skills/shifts, grouped persisted permissions, filtered rooms, and preview-before-confirm CSV file/paste import are managed in this surface. Its hotel selector clears scoped UI state before driving explicit `/api/v1/internal/admin/hotels/{hotelId}/**` requests; these internal endpoints are intentionally excluded from the public OpenAPI/SDK contract.

## Testing and verification

- Backend/UniMock: JUnit 5 + Kotlin test + Spring Boot Test. Unit tests cover domain/application rules; integration tests use PostgreSQL 16 Testcontainers; MockMvc covers HTTP/security; ArchUnit protects architecture. There are also provider stubs, failure-injection/resilience, OpenAPI snapshot/compatibility, performance-index, Docker smoke, and opt-in external sandbox tests.
- Mobile/SDK: TypeScript compile checks plus Node's built-in test runner (`*.test.mjs`) with custom TS hooks. Tests focus on services, mappers, hooks/helpers, API transport behavior, offline/session logic, components, and responsive layout. Visual/voice acceptance scripts are separate targeted checks.
- Standard verification: `./gradlew :backend:test :unimock:test`; public API changes additionally run `:backend:verifyOpenApiContract`, `:backend:checkOpenApiCompatibility`, and SDK generate/verify/build/test checks; mobile changes run SDK build first, then `cd mobile && npx tsc --noEmit && npm test`. Use focused tests during iteration and the proportional relevant suite before handoff.

## Development guardrails

- Default local/test behavior must be deterministic and must not call external PMS, OpenAI, STT, messaging, or embedding services. New external behavior must remain adapter-based, explicitly enabled, profile/permission gated, privacy-safe, observable, and covered by local fakes.
- Preserve privacy boundaries: do not expose credentials, raw provider payloads, prompts, embeddings, guest/payment data, or raw PMS identifiers through APIs, logs, diagnostics, or UI.
- Keep new behavior tenant-scoped, permission-gated, transactional where state changes span records, observable with bounded/sanitized labels, and idempotent when retryable.
