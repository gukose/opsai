# AI-Assisted Task Recommendations

Sprint 13C introduced advisory reservation task recommendations. Sprint 13D
adds provider governance and disabled-by-default scheduled generation. The
deterministic reservation task automation engine remains the only automatic
task creation path.

## Provider Governance

Recommendation providers are registered through `TaskRecommendationProviderRegistry`
using stable provider ids. Duplicate ids fail startup. The registry resolves
the configured active provider and exposes only safe metadata: provider id,
display name, status, capability names, whether model metadata exists, and the
template version.

The current provider is `internal-demo`. It requires no credentials, performs no
network calls, and declares deterministic output. Future external LLM adapters
must register through the same provider interface and must keep prompts,
credentials, raw PMS references, reservation ids, and guest data outside
operational responses, metrics, and audit events.

Provider capabilities are explicit:

- `BATCH_GENERATION`
- `STRUCTURED_EXPLANATIONS`
- `CONFIDENCE_SCORING`
- `RETRYABLE_EXECUTION`
- `MODEL_METADATA`
- `DETERMINISTIC_OUTPUT`

Generation validates required capabilities before invoking a provider.

## Sanitized Context

Recommendation context is provider-neutral and versioned with
`reservation-task-recommendation-context-v1`. It may include lifecycle status,
stay timing, occupancy counts, room-assignment completeness, deterministic
automation outcomes, safe backlog bands, active recommendation bands, unresolved
automation failure indicators, stay proximity bands, lifecycle recency bands,
and property capability flags.

It must not include guest names, contact information, notes, raw reservation
references, PMS DTOs, raw property identifiers, webhook payloads, payment data,
prompt contents, or credentials.

The context schema version is persisted with recommendations and included in
deduplication input so future incompatible context changes do not silently reuse
old recommendation identities.

## Scheduled Generation

Scheduled generation is disabled by default under
`ops.ai.reservation.task-recommendations.schedule.enabled=false`. When enabled,
it invokes `ReservationTaskRecommendationService`, uses the shared
`scheduler_lock` table with job name
`reservation_task_recommendation_scheduler`, and processes a bounded candidate
batch. It never approves or applies recommendations.

Pause and resume state is durable in
`reservation_task_recommendation_schedule_state`. Configuration remains the
upper-level enable switch. Operator run-now executions are recorded as
`OPERATOR`, not `SCHEDULED`.

## Candidate Selection

Candidates are selected from successful deterministic automation executions
that do not already have an active recommendation for the same internal
reservation. Ordering is deterministic by automation execution creation time and
outbox event id. Batches are bounded by configuration, so older eligible records
do not starve and permanently ineligible records are skipped rather than
reprocessed.

## Run History And Retention

Generation runs are persisted in
`reservation_task_recommendation_generation_run` with trigger, provider id,
status, timestamps, selected and processed candidate counts, generated,
duplicate, skipped, and failed counts, and a safe failure category. Run history
does not store reservation ids, recommendation content, task ids, prompts, or
provider payloads.

Retention cleanup is explicit and may be scheduled behind a separate disabled
flag. It deletes only terminal runs and terminal recommendation records older
than configured retention thresholds. Applied recommendations use a longer
retention window.

## Operations

Internal operations under
`/api/v1/internal/reservations/task-recommendations` require
`RESERVATION_SYNC_OPERATIONS` and are excluded from the public OpenAPI/SDK
contract. They support provider status, scheduler status, run-now, pause,
resume, generation-run history, bounded expiration, and retention cleanup.

## Limitations

There is no external LLM adapter, no prompt registry, no scheduler-enabled
default behavior, no automatic approval/application, no message broker, and no
public/mobile recommendation surface.

## Sprint 13E External LLM Foundation

Sprint 13E introduces the first external-provider foundation while keeping
`internal-demo` as the default active provider. External providers are
registered through the same `TaskRecommendationProviderRegistry` and are
classified as `INTERNAL` or `EXTERNAL`. Provider lifecycle values are
`REGISTERED`, `AVAILABLE`, `DISABLED`, `MISCONFIGURED`, and `UNAVAILABLE`.

The OpenAI provider is registered as `openai` but disabled by default:

```yaml
ops:
  ai:
    reservation:
      task-recommendations:
        active-provider: internal-demo
        providers:
          openai:
            enabled: false
            endpoint: https://api.openai.com/v1/chat/completions
            model: gpt-placeholder
            credential-reference:
              source: ENVIRONMENT
              name: OPENAI_API_KEY
            prompt-template-id: reservation-task-recommendation-v1
            prompt-version: reservation-task-recommendation-openai-v1
```

Disabled external configuration must never fail startup. If an external
provider is enabled and selected, startup validates required local
configuration only; it does not perform a network health check.

## Prompt Model

Prompts are represented by immutable `RecommendationPrompt` objects:

- system instructions
- recommendation template id
- template version
- structured sanitized context
- expected output schema

Services do not concatenate prompt strings. Persistence stores only prompt
version/template metadata through recommendation fields; generated prompt text
is not persisted, logged, returned by diagnostics, or audited.

## Privacy Gateway

`RecommendationPrivacyGateway` is the explicit outbound boundary for external
LLM traffic. It converts the internal sanitized recommendation context into a
smaller outbound context with only coarse operational signals: lifecycle state,
stay timing band, nights band, occupancy band, room-assignment completeness,
automation outcomes, safe task/recommendation count bands, unresolved
automation failure flag, and property capability flags.

Automated assertions reject outbound context containing restricted terms for
guest data, contact data, notes, payments, reservation ids, property ids,
webhooks, PMS DTOs, or external references.

## Structured Response Contract

External providers must return structured recommendation data. Required fields
are category, priority, confidence, explanation, supporting signals, task title,
and task summary. Malformed output, unsupported enum values, oversized
explanations, blank titles, or too many recommendations are rejected as terminal
provider failures. There is no free-form parsing.

## Credentials And HTTP

Credential resolution is isolated behind `RecommendationCredentialResolver`.
Current support is environment-variable resolution; secret reference and vault
sources are reserved for future integrations and fail safely until implemented.
Credential values are not stored, logged, returned by diagnostics, or persisted.

`RecommendationHttpClient` is the provider-neutral HTTP boundary. The OpenAI
adapter uses it for timeout-bound JSON POST requests, safe status mapping, and
provider metrics. Business services do not know about OpenAI DTOs, HTTP
headers, endpoints, retries, or response parsing.

## Failure Handling

Provider failures use safe categories such as `NETWORK_ERROR`, `TIMEOUT`,
`RATE_LIMIT`, `AUTHENTICATION`, `AUTHORIZATION`, `INVALID_RESPONSE`,
`INVALID_CONFIGURATION`, `PROVIDER_UNAVAILABLE`, and
`INTERNAL_PROVIDER_ERROR`. Retry is allowed only for transient network,
timeout, rate-limit, and provider-unavailable categories.

Internal provider diagnostics include provider id, type, lifecycle,
capabilities, active model, prompt version, last safe failure category, response
time band, and retry statistics. They never expose prompts, request bodies,
responses, credentials, reservation ids, task ids, guest data, or PMS
identifiers.

## Sprint 13F Operational Readiness

Sprint 13F keeps `internal-demo` as the default active provider and keeps
OpenAI disabled by default. It adds a readiness model for external providers
without performing network checks during readiness assessment. Readiness is
separate from lifecycle and may be `NOT_CONFIGURED`, `DISABLED`,
`BLOCKED_BY_ENVIRONMENT`, `MISCONFIGURED`, `READY_FOR_LOCAL_SMOKE`,
`READY_FOR_NON_PRODUCTION`, `TEMPORARILY_UNAVAILABLE`, or
`PRODUCTION_BLOCKED`.

External activation is governed by
`ops.ai.reservation.task-recommendations.external-providers.*` and
provider-specific settings under
`ops.ai.reservation.task-recommendations.providers.openai.*`.
Production profiles are blocked for Sprint 13F. Disabled providers do not
affect startup. Enabled external providers must pass profile, credential
reference, model, endpoint, and capability validation with sanitized error
messages. Local stub activation requires explicit smoke-test configuration.
There is no silent fallback to `internal-demo`; fallback is allowed only when
explicitly configured and is visible in diagnostics.

Smoke tests are operator-triggered only. The smoke path uses a synthetic,
versioned recommendation context, the `RecommendationPrivacyGateway`, the
canonical prompt model, and the real OpenAI adapter boundary. Fixture modes are
available only in explicit local/test smoke mode and support success, empty
success, malformed response, timeout, rate limit, authentication failure, and
provider unavailable outcomes. Smoke tests do not persist recommendations,
create tasks, approve/apply recommendations, or consume generation retry
budgets.

Provider smoke diagnostics are stored in
`recommendation_provider_diagnostic`. Records contain only provider id,
diagnostic type, trigger type, timestamps, outcome, safe failure category,
latency band, retry count, response validation outcome, prompt version, model
identifier, environment class, and endpoint classification. They do not persist
prompts, responses, request bodies, credentials, provider request ids,
reservation context, recommendation content, reservation ids, property ids, or
guest data.

## Sprint 14A Non-Production Pilot

Sprint 14A adds an internal pilot workflow for bounded external recommendation
generation in non-production only. The pilot is disabled by default and does
not change the normal recommendation scheduler, public APIs, SDK, or mobile
behavior.

Pilot readiness requires:

- enabled pilot configuration in an allowed non-production profile
- external provider id in the allowlist
- configured property scope allowlist
- provider readiness at or above the configured minimum
- a recent successful smoke diagnostic
- valid pilot start/end date window
- available daily request and recommendation budget
- mandatory operator review mode

Pilot candidate selection reuses the deterministic automation-to-recommendation
candidate boundary, then filters by canonical reservation property scope and
supported lifecycle state. The provider receives only the sanitized outbound
context and returns structured recommendations. Persisted pilot
recommendations are marked by pilot run id and remain `REVIEW_REQUIRED`.

Daily pilot budget is durable in `recommendation_pilot_budget_daily`.
`recommendation_pilot_run` stores safe run summaries without reservation ids,
property ids, prompts, responses, task descriptions, guest data, or credentials.
Rollback durably disables future pilot runs and preserves existing
recommendations and diagnostics.

## Sprint 14B Scheduled Pilot and Analytics

Pilot scheduling is a thin orchestration layer around
`ExternalRecommendationPilotService`. The scheduler is disabled by default,
uses the shared distributed scheduler lease table, and records runs with
trigger `SCHEDULED`. It never calls repositories, task services, PMS clients,
or provider HTTP clients directly.

Schedule guardrails are evaluated before provider traffic:

- pilot enabled and not operator-disabled
- non-production profile and schedule profile allowlist
- fresh provider smoke readiness
- configured property scope allowlist
- pilot date window and optional schedule override
- daily run limit
- daily request, recommendation, and token budgets
- distributed lease availability

Pause/resume state is durable in `recommendation_pilot_state`. Pause prevents
new scheduled work but does not cancel active work. Run-now is an operator
operation and remains trigger `OPERATOR`, not `SCHEDULED`.

Review analytics are aggregate-only. The query model derives counts and
breakdowns from `reservation_task_recommendation` and
`recommendation_pilot_run`; it does not persist a separate analytics table.
Allowed filters are generated date range, provider, model, category,
confidence, status, and pilot run id. Responses expose only counts, rates,
review-time bands, confidence/category/provider-model distributions, age
bands, duplicate-prevention totals, and failure totals.

Review time is the first review transition timestamp, or the terminal update
timestamp for expired/applied records, minus recommendation creation time.
Analytics return bands instead of per-record durations. Recommendation titles,
descriptions, explanations, reservation ids, task ids, property ids, prompts,
provider responses, guest data, credentials, and raw provider metadata are not
returned.
