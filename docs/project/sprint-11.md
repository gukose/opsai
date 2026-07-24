# Sprint 11 - Reporting AI + Service Recovery

## Sprint 11A - PMS Abstraction Layer

Sprint 11A establishes the PMS abstraction that later reporting and service
recovery work will consume.

Implemented architecture:

- Added a provider-independent PMS domain package under `com.hotelopai.pms`.
- Added `PmsProvider` as the central backend PMS capability interface.
- Added `PmsProviderRegistry` with configurable active-provider resolution.
- Registered `InternalDemoPmsProvider` as the first provider, wrapping the
  current UniMock/internal demo behavior.
- Moved internal demo mapping into `InternalDemoPmsMapper`.
- Updated task completion to depend on the active PMS provider rather than a
  UniMock-specific maintenance port.
- Kept dev PMS API response/request contracts unchanged.

Sprint 11A deliberately does not add real external PMS integrations, sync jobs,
webhooks, OAuth, retries, caching, rate limiting, or background workers.

Architecture details: `docs/architecture/pms-abstraction.md`.

## Sprint 11B - PMS Provider Configuration & Capability Model

Sprint 11B adds secure configuration and capability discovery to the PMS
abstraction.

Implemented architecture:

- Expanded `ops.ai.pms` configuration with active provider, enabled state,
  property identifier, endpoint, request timeout, auth mode, credential
  references, and provider-specific settings.
- Added type-safe authentication config for `NONE`, `API_KEY`, `BEARER_TOKEN`,
  `BASIC`, and future-shaped `OAUTH2_CLIENT_CREDENTIALS`.
- Added `PmsCapabilities` and central capability checks in
  `PmsProviderRegistry`.
- Added startup validation for duplicate providers, missing providers, disabled
  active providers, missing configuration, incomplete auth config, and
  inconsistent capability declarations.
- Added sanitized internal diagnostics that expose safe status and reference
  metadata without secret values.
- Declared the explicit InternalDemo capability set while keeping it the
  default credential-free provider.

Sprint 11B deliberately does not add real external PMS integrations, sync jobs,
webhooks, OAuth token acquisition, retries, caching, or public diagnostics.

## Sprint 11C - Real PMS Sandbox Adapter

Sprint 11C proves the PMS abstraction with one real external sandbox adapter
while preserving internal-demo as the default provider.

Provider selected:

- Apaleo, based on official developer documentation for free developer account
  setup, OAuth2 client credentials, Swagger-described inventory and booking
  APIs, documented pagination, documented rate limits, and explicit API scopes.

Implemented architecture:

- Added `ApaleoPmsProvider` under `com.hotelopai.integration.apaleo`.
- Added Apaleo-specific DTOs and `ApaleoPmsMapper` inside the Apaleo integration
  package only.
- Added a dedicated Apaleo HTTP client for OAuth2 token exchange, bearer
  requests, JSON parsing, bounded pagination, safe error mapping, and metrics.
- Added `PmsCredentialResolver` with environment-based credential references.
- Extended `PmsProvider` with provider-neutral read-only reservation and guest
  listing methods.
- Added disabled-by-default Apaleo configuration and an explicit
  `apaleo-sandbox` Spring profile with placeholder credential references.

Implemented Apaleo capabilities:

- hotel/property lookup
- room/unit listing
- reservation lookup through booking reservations
- guest lookup through booking/reservation person data
- stay lookup derived from room-assigned reservations

Unsupported in Sprint 11C:

- room status lookup/update
- housekeeping status updates
- maintenance updates
- PMS events
- webhooks
- incremental sync
- retries, caching, and production rollout

Automated tests use the local mock HTTP server and do not call Apaleo. They cover
authentication headers, mapping, bounded pagination, error translation, timeout,
malformed responses, unresolved credential references, metrics, capability
declarations, registry activation, and internal-demo remaining the default.

Architecture details: `docs/architecture/pms-abstraction.md`.

## Sprint 11D - PMS Health, Resilience & Rate-Limit Handling

Sprint 11D adds provider-level health checks and bounded resilience for external
PMS calls while keeping resilience inside the provider integration boundary.

Implemented architecture:

- Added provider-neutral health states: `READY`, `DEGRADED`, `UNAVAILABLE`,
  `MISCONFIGURED`, and `DISABLED`.
- Added safe PMS health details with provider id, configuration readiness, last
  check timestamps, safe failure category, capabilities, circuit state, and retry
  policy summary.
- Added network-free InternalDemo health through the default provider health
  path.
- Added Apaleo on-demand health checks using a minimal read-only property or
  unit-list request.
- Added bounded Apaleo retries for timeout, transport failure, selected 5xx, and
  bounded 429 `Retry-After` responses.
- Added a provider-scoped in-memory Apaleo circuit breaker with `CLOSED`, `OPEN`,
  and `HALF_OPEN` states.
- Added safe metrics for request attempts, retries, rate limits, circuit
  transitions, circuit rejections, and attempt counts.
- Extended sanitized diagnostics with health state, circuit state, retry policy,
  and last safe failure category.

Non-retryable failures remain single-shot:

- token/authentication requests
- 400/422 validation failures
- 401 authentication failures
- 403 permission failures
- 404 missing resources
- malformed responses
- configuration failures

Sprint 11D deliberately does not add background sync, webhooks, caching,
distributed circuit state, public diagnostics, production rollout, a second PMS
provider, or public API changes.

Automated tests use deterministic mock HTTP responses and fake sleepers. They do
not call Apaleo or require sandbox credentials.

Architecture details: `docs/architecture/pms-abstraction.md`.

## Sprint 11E - PMS Operations & Rollout Controls

Sprint 11E adds an internal operational surface and controlled rollout workflow
without changing public product APIs, SDK behavior, mobile behavior, or backend
business semantics.

Implemented architecture:

- Added `PmsOperationsService` as the provider-neutral operations boundary.
- Added authenticated internal endpoints under `/api/v1/internal/pms`, excluded
  from the public v1 OpenAPI group.
- Added `PMS_OPERATIONS_ACCESS` for sanitized PMS operational access.
- Added rollout readiness states: `NOT_CONFIGURED`, `DISABLED`, `BLOCKED`,
  `DEGRADED`, `READY_FOR_SANDBOX`, and `READY_FOR_PRODUCTION_REVIEW`.
- Added production activation guard for external PMS providers in `prod` or
  `production` profiles; `internal-demo` remains exempt.
- Added credential refresh lifecycle and safe audit events for health checks,
  credential refresh, rollout inspection, and production activation rejection.
- Added provider-scoped Apaleo OAuth token caching with expiry safety window,
  single-flight acquisition, refresh invalidation, and one reacquire after an API
  401.
- Added opt-in `:backend:apaleoSandboxSmokeTest`; it skips by default and is not
  part of CI.

Sprint 11E deliberately does not add background synchronization, webhooks,
provider data caching, a second PMS adapter, production rollout automation, or
public/mobile-facing PMS operations.

Architecture details: `docs/architecture/pms-abstraction.md`.

## Goal
Implement Bad Review Risk, Service Recovery, and Manager AI Reporting.

## Business value
Helps managers identify guest-experience risks early and coordinate recovery before issues become negative reviews.

## Architecture impact
- Adds AI-assisted reporting on top of validated operational, PMS, and guest-channel data.
- Keeps recommendations advisory; workflow changes still go through Task Engine controls.

## Backend tasks
- Implement bad-review-risk scoring inputs, service-recovery recommendation APIs, and manager report generation endpoints.
- Add audit records for generated reports and recommended actions.
- Allow managers to convert approved recommendations into tasks.

## Mobile tasks
- Add manager AI report views, risk indicators, recommended recovery actions, and task creation from approved recommendations.
- Show confidence and source context clearly.

## AI tasks
- Use `AiInterpreter` or a reporting-specific abstraction behind the same validation principles.
- Generate structured manager summaries, risk reasons, and service-recovery suggestions.
- Include confidence score, source references, and fallback behavior.

## UniMock tasks
- Provide PMS guest, reservation, event, and guest-request context for reporting AI test scenarios.

## Database tasks
- Add Flyway migrations for risk scores, report runs, report artifacts, recommendation records, source references, and manager approvals.

## Infrastructure tasks
- Add cost controls and scheduled/report-triggered execution limits.
- Add secure storage strategy for report artifacts and audit logs.

## UI tasks
- Add manager-facing reporting AI screens that are concise, inspectable, and action-oriented.
- Distinguish AI recommendations from confirmed operational facts.

## Documentation tasks
- Document risk-score inputs, report schemas, confidence handling, recommendation approval flow, and limitations.

## Testing tasks
- Verify report generation with mocked AI outputs.
- Verify source references and confidence are stored.
- Verify recommendations cannot alter workflow without manager approval.
- Verify fallback when AI output is invalid or unavailable.

## Risks
- AI-generated risk labels can be misleading if source data is incomplete.
- Managers need clear distinction between facts, inference, and recommendation.

## Definition of Done
- Managers can view AI-assisted risk and service-recovery reports.
- Recommendations are structured, auditable, and manager-approved before task creation.

## Dependencies on previous sprints
- Depends on Sprint 9 dashboard/reporting foundation, Sprint 10 guest channels, Sprint 5 AI abstraction, and Sprint 3 Task Engine.
