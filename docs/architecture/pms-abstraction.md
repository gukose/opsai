# PMS Abstraction Layer

Sprint 11A introduces a vendor-agnostic PMS boundary for backend application code.
The abstraction exists because HotelOpAI needs to support providers such as Mews,
Apaleo, Opera Cloud, Protel, Cloudbeds, and future internal systems without
coupling task, assistant, reporting, or notification workflows to one vendor.

## Provider Lifecycle

The active provider is resolved through `PmsProviderRegistry`.

- `ops.ai.pms.active-provider` selects the provider.
- The default is `internal-demo`.
- Providers are regular Spring beans implementing `PmsProvider`.
- Duplicate provider ids fail during registry construction.
- Missing or disabled active providers fail during startup with an actionable
  error.
- Registered providers must have a matching configuration entry.

Sprint 11A registered one provider:

- `InternalDemoPmsProvider`, which wraps the current UniMock/internal demo PMS
  behavior.

Sprint 11C adds one opt-in external sandbox provider:

- `ApaleoPmsProvider`, a read-only Apaleo sandbox adapter for properties, units,
  bookings/reservations, guests, and stays.

Internal demo remains the default. Sprint 11D adds provider-bound health,
bounded retry, rate-limit handling, and an in-memory circuit breaker for Apaleo.
Sprint 11E adds internal operations endpoints, rollout controls, credential
refresh lifecycle, and bounded Apaleo OAuth token caching.

No production rollout, sync job, webhook, repository-wide cache, or
repository-wide rate limiter is implemented through Sprint 11E.

Sprint 12B adds an explicit reservation synchronization foundation on top of
the PMS abstraction. It is an application service, not a provider feature: the
active provider supplies provider-neutral PMS reservations and guests, and the
reservation module maps those models into canonical reservation snapshots. No
provider DTOs, raw payloads, credentials, scheduler, or webhook logic are added
to business services.

## Provider Configuration

Provider configuration is bound from `ops.ai.pms`.

```yaml
ops:
  ai:
    pms:
      active-provider: internal-demo
      providers:
        internal-demo:
          enabled: true
          display-name: Internal Demo PMS
          hotel-property-identifier: ${PMS_PROPERTY_ID:}
          endpoint: ${PMS_BASE_URL:}
          authentication:
            mode: NONE
          request-timeout: PT5S
          settings:
            vendor-region: ${PMS_VENDOR_REGION:}
```

Credential values are not stored in PMS domain models or diagnostics. Provider
configuration stores credential references only, such as
`env:APALEO_CLIENT_SECRET`, so environment-specific secret injection happens at
the adapter boundary.

Supported authentication configuration modes are:

- `NONE`
- `API_KEY`
- `BEARER_TOKEN`
- `BASIC`
- `OAUTH2_CLIENT_CREDENTIALS`

OAuth2 client credentials were modeled in Sprint 11B. Sprint 11C implements
client-credentials token acquisition only inside the Apaleo adapter. Sprint 11E
caches Apaleo access tokens in memory until shortly before expiry and clears the
cache after credential refresh or one API authentication rejection. Tokens are
not persisted by the SDK, mobile app, PMS domain, diagnostics, or API responses.

## Apaleo Sandbox Adapter

Apaleo was selected because its official developer documentation exposes a free
developer signup path, Swagger/OpenAPI reference at `https://api.apaleo.com/swagger/`,
OAuth2/OpenID Connect authentication, and documented inventory and booking APIs.

Official documentation used:

- API overview: `https://apaleo.dev/guides/api/overview.html`
- OAuth2 authentication: `https://apaleo.dev/guides/oauth-connection/authentication.html`
- Client credentials flow: `https://apaleo.dev/guides/oauth-connection/simple-client`
- Inventory Swagger: `https://api.apaleo.com/swagger/inventory-v1/swagger.json`
- Booking Swagger: `https://api.apaleo.com/swagger/booking-v1/swagger.json`
- Pagination: `https://apaleo.dev/guides/api/pagination.html`
- Rate limiting: `https://apaleo.dev/guides/api/rate-limiting.html`
- Scopes: `https://apaleo.dev/guides/api/scopes.html`

Implemented read-only calls:

- `GET /inventory/v1/properties/{id}` for hotel/property lookup.
- `GET /inventory/v1/units` for room/unit listing.
- `GET /booking/v1/bookings` for bookings, reservations, guests, and stay
  derivation.

The adapter requests OAuth2 client-credentials tokens from the configured token
URL and sends Apaleo API requests with bearer authentication. It uses bounded
`pageNumber`/`pageSize` pagination and defaults to at most two pages with
`page-size` capped at 200. It does not cache, synchronize, or subscribe to
webhooks.

Opt-in local sandbox profile:

```bash
SPRING_PROFILES_ACTIVE=apaleo-sandbox \
APALEO_PROPERTY_ID=<sandbox-property-id> \
APALEO_CLIENT_ID=<sandbox-client-id> \
APALEO_CLIENT_SECRET=<sandbox-client-secret> \
./gradlew :backend:bootRun
```

The profile uses placeholders only. CI and normal local development do not
depend on live Apaleo credentials.

Apaleo resilience configuration is held under provider `settings`:

```yaml
settings:
  health-check-timeout: PT2S
  retry-max-attempts: "2"
  retry-initial-backoff: PT0.1S
  retry-max-backoff: PT1S
  max-rate-limit-delay: PT2S
  circuit-failure-threshold: "3"
  circuit-open-duration: PT30S
  circuit-half-open-max-attempts: "1"
  token-expiry-safety-window: PT60S
```

The live sandbox smoke test is opt-in and is not part of CI:

```bash
APALEO_PROPERTY_ID=<sandbox-property-id> \
APALEO_CLIENT_ID=<sandbox-client-id> \
APALEO_CLIENT_SECRET=<sandbox-client-secret> \
./gradlew :backend:apaleoSandboxSmokeTest -Photelopai.apaleo.sandbox.smoke.enabled=true
```

The task performs read-only health and unit-list checks. It skips unless the
enable flag is set.

## Capability Model

Each provider declares `PmsCapabilities`. Capabilities describe support only;
they do not perform operations.

Current capability flags include:

- hotel lookup
- room listing
- room status lookup and update
- stay or occupancy lookup
- reservation lookup
- guest lookup
- asset lookup
- issue type lookup
- housekeeping status updates
- maintenance updates
- event retrieval and creation
- webhooks
- incremental sync

Application services use `PmsProviderRegistry.activeProviderRequiring(...)` for
optional PMS operations. Unsupported capabilities fail with a clear application
exception instead of provider-name checks.

Startup validation also checks basic capability consistency. For example,
maintenance updates require room listing and issue type lookup.

Reservation synchronization requires `RESERVATION_LOOKUP` and `GUEST_LOOKUP`.
Providers without those capabilities, including the default internal-demo
provider today, fail through the capability guard when synchronization is
explicitly invoked. Internal-demo remains the default PMS provider for normal
runtime behavior.

## Reservation Webhooks

Sprint 12E adds provider-neutral reservation webhook ingestion. Webhooks are an
integration notification boundary, not a source-of-truth boundary. Provider
adapters verify and normalize webhook notifications, then the reservation
application layer stores safe inbox metadata and triggers bounded PMS reads
through `ReservationSyncOperationsService`.

Provider-specific payloads remain inside the provider integration package. The
Apaleo adapter maps official Apaleo webhook fields (`id`, `topic`, `type`,
property id, timestamp, and optional entity id) into provider-neutral event
categories. Apaleo documentation recommends HTTPS, IP allowlisting,
deduplication by event id, and a unique token in the webhook URL; no
cryptographic signature header is documented for this flow. HotelOpAI therefore
uses a credential-reference URL token with constant-time comparison and
database uniqueness for replay/deduplication.

Webhook ingestion and processing are disabled by default and require explicit
configuration. Internal-demo remains the default provider and does not declare
webhook capability.

Sprint 12F adds scheduled webhook inbox processing behind the same provider
abstraction. Startup/runtime activation validates that ingestion and processing
are enabled, the active PMS provider declares `WEBHOOKS`, a matching
`ReservationWebhookAdapter` is registered, and the adapter can resolve its safe
authentication reference. Scheduled processing uses the shared distributed
scheduler lock and never calls provider HTTP clients directly; it invokes the
provider-neutral webhook processing service, which then invokes reservation
sync operations.

## Sanitized Diagnostics

`PmsProviderDiagnostic` is an internal diagnostic representation. It may include:

- provider id
- display name
- enabled/configured state
- authentication mode
- credential reference names
- safe endpoint host
- configured settings keys
- capabilities

It must not include:

- API keys
- bearer tokens
- passwords
- client secrets
- full authorization headers
- provider-specific secret values

No public diagnostic endpoint is added in Sprint 11B.

Sprint 11D extends diagnostics with health state, circuit state, retry policy
summary, and last safe failure category. Sprint 11E exposes those details only
through authenticated internal PMS operations endpoints.

## Internal Operations And Rollout Controls

Sprint 11E adds an internal provider-neutral operations surface under
`/api/v1/internal/pms`. These endpoints are excluded from the public v1 OpenAPI
group and are not mobile-facing.

Required permission:

- `PMS_OPERATIONS_ACCESS`

Endpoints:

- `GET /api/v1/internal/pms/providers`
- `GET /api/v1/internal/pms/providers/{providerId}`
- `POST /api/v1/internal/pms/providers/{providerId}/health-check`
- `POST /api/v1/internal/pms/providers/{providerId}/credentials/refresh`
- `GET /api/v1/internal/pms/rollout-readiness`

The DTOs are provider-neutral and omit credential references, raw provider
settings, secret values, request bodies, raw provider responses, guest data,
reservation data, and sensitive URLs.

Rollout readiness states are:

- `NOT_CONFIGURED`
- `DISABLED`
- `BLOCKED`
- `DEGRADED`
- `READY_FOR_SANDBOX`
- `READY_FOR_PRODUCTION_REVIEW`

Readiness is assessed from safe signals: enabled state, configuration readiness,
credential reference resolution readiness, capabilities, latest health, circuit
state, required property id, active profiles, and explicit production approval.
The check never activates or promotes a provider.

External PMS providers cannot be active in `prod` or `production` profiles
without `production-approved: true`. Optional `allowed-profiles` can further
limit where a provider may be active. Internal demo is exempt. Temporary
external unavailability does not block startup when configuration and approval
are valid.

Credential refresh is an internal operation. It re-resolves configured
credential references and invalidates provider authentication state. It never
accepts raw secret values, writes secrets, or returns secrets. Apaleo clears its
cached OAuth token after refresh.

## Health and Resilience

PMS provider health uses provider-neutral states:

- `READY`
- `DEGRADED`
- `UNAVAILABLE`
- `MISCONFIGURED`
- `DISABLED`

Health details include provider id, enabled/configured flags, checked time,
last successful and failed check timestamps, safe failure category, supported
capabilities, circuit state, and retry policy summary. Health output does not
include credentials, raw response bodies, guest/property/reservation data, or
sensitive URLs.

Internal demo health is local and network-free. Apaleo health is an on-demand
minimal read-only check using configured property lookup when a property id is
present, otherwise unit listing. Temporary external unavailability does not
cause startup failure. Startup fails only when an explicitly active provider is
disabled, missing, invalidly configured, or has unresolved credential references.

Apaleo retries are bounded and provider-local. Retryable failures are:

- timeouts
- transport failures
- selected 5xx provider unavailable responses
- 429 responses only when `Retry-After` is present and within
  `max-rate-limit-delay`

Non-retryable failures are:

- OAuth token/authentication request failures
- 400/422 validation failures
- 401 authentication failures
- 403 permission failures
- 404 not-found failures
- malformed responses
- configuration failures

The retry policy uses exponential backoff up to `retry-max-backoff`. Tests inject
a fake sleeper; production uses bounded thread sleep. No retry path is infinite.

The Apaleo circuit breaker is scoped to the provider bean and has three states:

- `CLOSED`: normal calls flow through.
- `OPEN`: calls are rejected immediately after repeated transient failures.
- `HALF_OPEN`: limited probes are allowed after `circuit-open-duration`.

Successful calls close the circuit and reset transient failure state. Half-open
failures reopen it. Authentication and configuration failures are recorded as
safe failure categories but do not count as transient availability failures.

## Domain Boundary

Provider-independent models live under `com.hotelopai.pms.domain`.

Core models include:

- `PmsHotel`
- `PmsReservation`
- `PmsGuest`
- `PmsRoom`
- `PmsRoomStatus`
- `PmsStay`
- `PmsHousekeepingTask`
- `PmsAsset`
- `PmsIssueType`
- `PmsEvent`
- `PmsUpdateResult`

These models intentionally do not include vendor-specific fields. Vendor DTOs
remain inside provider or integration packages.

Sprint 12A adds `com.hotelopai.reservation` as the canonical business
reservation domain. PMS reservation, guest, and stay models remain
integration-facing source models. Business workflows should consume the
reservation application/domain boundary instead of depending directly on
`PmsReservation`, `PmsGuest`, or provider DTOs.

## Provider Interface

`PmsProvider` covers the current backend needs:

- hotel lookup
- reservation and guest listing as read-only provider extension points
- room listing and room lookup
- room status lookup and update
- room stay/occupancy lookup
- room assets and issue types
- housekeeping task lookup and status update
- maintenance updates used by task completion
- PMS event creation for the existing dev PMS surface
- hotel lookup as an extension point

The interface does not model broad future capabilities until business code
needs them.

## Mapping Boundaries

Mapping is explicit:

Vendor DTO or vendor client model

↓

Provider-independent PMS domain model

↓

Application service or API adapter

For the internal demo provider, `InternalDemoPmsMapper` owns the mapping between
the existing UniMock client models and the neutral PMS domain. The local/test
dev PMS controller still returns its existing response DTOs, but it now reaches
the data through the provider registry.

For Apaleo, `ApaleoPmsMapper` owns mappings from Apaleo property, unit, booking,
reservation, and person DTOs to neutral PMS models. Apaleo identifiers and enum
names do not leak into application services.

## Provider Errors and Observability

Provider HTTP failures are translated to provider-neutral exceptions:

- 400/422 invalid request
- 401 authentication failure
- 403 permission failure
- 404 missing resource
- 429 rate limit, with safe `Retry-After` metadata
- 5xx provider unavailable
- timeout
- malformed provider response
- unresolved credential reference

Exception messages may include operation name, HTTP status, and Apaleo tracking
id. They do not include raw response bodies, credentials, bearer tokens, guest
names, reservation details, or full URLs.

The Apaleo client records safe metrics:

- `pms_provider_requests_total`
- `pms_provider_request_duration`
- `pms_provider_health_checks_total`
- `pms_provider_retries_total`
- `pms_provider_last_attempt_count`
- `pms_provider_rate_limit_total`
- `pms_provider_circuit_transitions_total`
- `pms_provider_circuit_rejections_total`
- `pms_provider_token_refresh_total`
- `pms_operations_audit_events_total`

Tags are limited to provider, operation, outcome, and status. No request bodies,
guest data, reservation data, credentials, or identifying URLs are recorded.
Internal PMS operations audit events record acting user id, provider id, action,
outcome, timestamp, and safe failure category only.

## Extension Strategy

Adding a provider should require:

1. A new `PmsProvider` implementation.
2. Provider-specific DTOs/client code inside that provider's infrastructure
   package.
3. Dedicated mapper code from provider DTOs to PMS domain models.
4. Spring registration under a unique `PmsProviderId`.
5. Configuration under `ops.ai.pms.providers`.
6. Capability declaration matching implemented operations.
7. Configuration of `ops.ai.pms.active-provider` when that provider should be
   selected.

Business services should not require modification for provider selection or
vendor DTO mapping.

## Current Behavior

Existing runtime behavior is preserved:

- Public API paths and DTOs are unchanged.
- Dev PMS paths remain local/test only.
- Task completion still performs the existing internal demo PMS maintenance
  verification and returns the same verification metadata.
- Mobile behavior is unchanged.
- Default backend startup still activates `internal-demo`.

## Sprint 11D Candidates

Likely next work:

- PMS sync/webhook architecture
- operational policy tuning from real sandbox evidence
- broader Apaleo operation coverage or a second sandbox provider
