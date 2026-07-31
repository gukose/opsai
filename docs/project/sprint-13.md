# Sprint 13 - Pilot Hotel Readiness

## Sprint 13A - Reservation-Driven Task Automation Foundation

Sprint 13A adds a disabled-by-default deterministic automation engine that
turns safe canonical reservation events into operational task proposals.

Implemented architecture:

- Added provider-neutral automation concepts for rule id, rule version, trigger
  event type, task proposal, execution outcome, skip reason, and execution
  history.
- Added `ReservationTaskAutomationRule`, a side-effect-free rule contract.
  Rules evaluate a canonical reservation snapshot plus safe reservation outbox
  context and return task proposals only.
- Added initial deterministic rules for arrival preparation, same-day
  departure review, room-assignment change review, cancellation cleanup,
  no-show review, check-in follow-up, and checkout follow-up.
- Added `ReservationTaskAutomationService`, which claims eligible reservation
  outbox events, loads canonical reservation snapshots, evaluates enabled
  rules, and creates tasks through the existing task lifecycle boundary.
- Added durable idempotency through
  `reservation_task_automation_execution.deduplication_key`.
- Added execution history with safe outcomes and retry metadata. The history
  stores internal reservation ids, rule ids, outcomes, task ids, timestamps, and
  safe categories only.
- Scoped the existing task-created outbox processor to `TASK_CREATED`; the
  reservation automation processor owns reservation outbox events.
- Added internal-only operations for rule listing, bounded batch processing,
  execution history, execution detail, and retry.

Automation is disabled by default with
`ops.ai.reservation.task-automation.enabled=false`. Enabled configuration must
include a non-empty internal `hotel-id`. Generated tasks use existing task
source `IMPORT`; automation origin is stored in internal execution metadata so
no public task enum or mobile behavior changes.

Sprint 13A does not add AI decision-making, automatic scheduling, outbound PMS
updates, external publication, public APIs, SDK surface, or mobile changes.

Known limitation: Sprint 12 reservation outbox uniqueness is still one event of
each type per reservation. Automation deduplicates task creation safely, but a
future sprint should refine reservation event identity before supporting
multiple repeated same-type lifecycle events over time.

## Sprint 13B - Scheduled Reservation Task Automation And Rule Policy

Sprint 13B adds controlled scheduled processing and configurable deterministic
rule policy without changing public APIs, SDKs, mobile behavior, PMS behavior,
or task user-visible behavior.

Implemented architecture:

- Added `reservation_task_automation_schedule_state` for durable pause/resume
  state, last attempted/successful execution timestamps, processed count,
  created-task count, and safe failure category.
- Added `reservation_task_automation_scheduler`, disabled by default, using the
  shared `scheduler_lock` table and `DistributedScheduledJobRunner`.
- Extended internal task automation operations with sanitized schedule status,
  run-now, pause, and resume actions.
- Added rule-specific policy configuration for enablement, priority override,
  local due time, due-date offset, minimum lead time, timezone, maximum trigger
  age, and past-due clamping.
- Added a shared timezone-safe due-date policy that uses local dates and
  configured `ZoneId` values instead of fixed-hour arithmetic across daylight
  saving boundaries.
- Added startup validation for unknown enabled rule ids, unknown rule policy
  ids, missing enabled rules, invalid batch/retry bounds, invalid schedule
  activation, and profile allowlist mismatches.
- Preserved task lifecycle safety: repeated evaluation hits durable
  idempotency and does not overwrite or reopen staff-managed tasks.

Automation scheduling remains disabled by default with
`ops.ai.reservation.task-automation.schedule.enabled=false`. Run-now actions
are operator-triggered and are recorded separately from scheduled execution.

Sprint 13B still does not add AI rule evaluation, outbound PMS updates, message
brokers, external publication, public APIs, SDK surface, mobile changes, or
automatic enablement.

## Sprint 13C - AI-Assisted Task Recommendation Foundation

Sprint 13C adds an advisory recommendation layer beside deterministic
reservation task automation. Recommendations never create or modify tasks until
an internal operator explicitly approves and applies them.

Implemented architecture:

- Added provider-neutral recommendation concepts for id, source, status,
  confidence, category, explanation, outcome, and safe failure category.
- Added `TaskRecommendationProvider`, which receives a sanitized reservation
  and deterministic automation context and returns task recommendations only.
- Added `InternalDemoRecommendationProvider`, a deterministic non-external
  provider used to prove the boundary without OpenAI or another LLM.
- Added `ReservationTaskRecommendationService` for disabled-by-default,
  operator-triggered, bounded recommendation generation.
- Added durable recommendation persistence in
  `reservation_task_recommendation` with deduplication by logical safe state.
- Added internal-only review operations for list, detail, generate batch,
  approve, reject, expire, apply, and retry.
- Applying a recommendation creates exactly one task through the existing task
  lifecycle boundary and marks the recommendation `APPLIED`.

Sanitized recommendation context may include lifecycle status, stay timing,
occupancy counts, room-assignment availability, deterministic automation
outcomes, and safe backlog bands. It must not include guest names, contact
details, reservation notes, raw PMS payloads, external reservation references,
raw property identifiers, provider DTOs, webhook payloads, payment data, or
credentials.

Recommendations are disabled by default with
`ops.ai.reservation.task-recommendations.enabled=false`. Sprint 13C does not
add a scheduler, external LLM calls, AI-created tasks, outbound PMS updates,
message brokers, public APIs, SDK surface, or mobile changes.

## Sprint 13D - Scheduled Recommendation Generation & Provider Governance

Sprint 13D adds the operational governance layer around advisory
recommendations:

- `TaskRecommendationProviderRegistry` resolves recommendation providers by
  stable provider id, rejects duplicate ids, validates the configured active
  provider, and exposes only safe metadata.
- Recommendation provider capabilities are explicit and include batch
  generation, structured explanations, confidence scoring, retryable execution,
  model metadata, and deterministic output.
- The sanitized recommendation context is versioned as
  `reservation-task-recommendation-context-v1` and now includes safe operational
  bands such as active recommendation count, unresolved automation failure,
  room-assignment completeness, stay proximity, lifecycle recency, and property
  capability flags.
- Durable generation-run history records trigger, provider id, status,
  candidate counts, generated/duplicate/skipped/failed counts, and safe failure
  category without reservation ids, task ids, prompts, or recommendation
  content.
- A disabled-by-default scheduler invokes only
  `ReservationTaskRecommendationService`, uses the shared `scheduler_lock`
  table, supports durable pause/resume state, and never approves or applies
  recommendations.
- Internal operations add provider status, scheduler status, run-now, pause,
  resume, generation-run history, expiration, and retention cleanup. These
  endpoints remain under `/api/v1/internal/**`, require
  `RESERVATION_SYNC_OPERATIONS`, and are excluded from the public OpenAPI/SDK
  contract.

External LLM integration, prompt submission, automatic recommendation
application, outbound PMS updates, message brokers, public API changes, and
mobile changes remain out of scope.

## Sprint 13E - External LLM Provider Foundation

Sprint 13E adds the disabled-by-default external LLM provider foundation:

- Providers now declare type (`INTERNAL` or `EXTERNAL`) and lifecycle
  (`REGISTERED`, `AVAILABLE`, `DISABLED`, `MISCONFIGURED`, `UNAVAILABLE`).
- `ExternalLlmRecommendationProvider` defines the provider-neutral external LLM
  boundary. Implementations receive sanitized context and return structured
  recommendations only.
- `RecommendationPrompt` separates system instructions, template id/version,
  structured context, and expected output schema. Prompt text is not persisted.
- `RecommendationPrivacyGateway` transforms recommendation context into an
  outbound schema and asserts that restricted personal, PMS, webhook, property,
  and reservation fields are absent.
- `OpenAiRecommendationProvider` is registered as `openai`, disabled by
  default, isolated under the OpenAI integration package, and performs no
  traffic unless explicitly enabled and selected.
- Provider-neutral HTTP, credential resolution, response validation, safe
  failure categories, metrics, and diagnostics were added.

InternalDemo remains the default active provider. Deterministic automation
remains authoritative, and AI recommendations remain advisory until reviewed
and applied by an internal operator.

## Sprint 13F - External Provider Operational Readiness

Sprint 13F completes the non-production operational readiness layer for
external recommendation providers while keeping `internal-demo` as the default
active provider and OpenAI disabled by default:

- External provider activation is governed by global and provider-specific
  profile/environment policy. Production activation is explicitly prohibited
  for Sprint 13F.
- Readiness is separate from lifecycle and reports safe states such as
  `DISABLED`, `MISCONFIGURED`, `READY_FOR_LOCAL_SMOKE`,
  `READY_FOR_NON_PRODUCTION`, `TEMPORARILY_UNAVAILABLE`, and
  `PRODUCTION_BLOCKED`.
- Local/stub smoke testing runs through the real OpenAI adapter, privacy
  gateway, canonical prompt model, HTTP boundary, and structured response
  validation, but uses deterministic fixtures and never calls live OpenAI in
  tests or default verification.
- Smoke diagnostics are durably persisted in
  `recommendation_provider_diagnostic` with safe metadata only. Prompts,
  responses, request bodies, credentials, provider request ids, reservation
  context, reservation ids, property ids, guest data, and recommendation
  content are not stored.
- Internal operations add readiness inspection, smoke-test execution,
  diagnostic history, diagnostic detail, and diagnostic cleanup under
  `/api/v1/internal/reservations/task-recommendations/providers/**`, guarded by
  `RESERVATION_SYNC_OPERATIONS`.
- Fallback to `internal-demo` is never silent. It may only be configured
  explicitly and is reported through diagnostics and audit.

External recommendations remain advisory and `REVIEW_REQUIRED`. Sprint 13F
does not add production use, live OpenAI CI calls, public API changes, SDK
changes, mobile changes, AI task auto-creation, outbound PMS updates, message
brokers, or deployment artifacts.

## Goal
Prepare Hotel OpAI for a controlled pilot hotel rollout.

## Business value
Moves the product from internal development toward real operational validation with hotel staff and managers.

## Architecture impact
- Hardens tenant onboarding, hotel configuration, operational roles, support processes, and pilot observability.
- Freezes major feature scope for pilot stabilization.

## Backend tasks
- Add pilot hotel onboarding tools, configuration validation, data seeding/import helpers, and support/admin endpoints.
- Review authorization coverage for all pilot workflows.
- Add operational audit exports if needed.

## Mobile tasks
- Polish staff and manager flows for pilot use.
- Add support/contact surfaces, pilot feedback capture, and resilient offline/error states where practical.

## AI tasks
- Tune prompts, confidence thresholds, multilingual behavior, and fallback copy using pilot hotel workflows.
- Ensure AI features can be disabled per tenant if needed.

## UniMock tasks
- Provide pilot rehearsal data that mirrors expected hotel operations.
- Use UniMock to test onboarding and PMS-like workflows before live pilot setup.

## Database tasks
- Add Flyway migrations for pilot configuration, feature flags, feedback, audit exports, or onboarding metadata if needed.
- Validate migration rollback/recovery strategy for pilot environments.

## Infrastructure tasks
- Prepare pilot environment configuration, secrets, backups, monitoring, log retention, and access control.
- Add environment-specific feature flags.

## UI tasks
- Polish pilot-critical screens for clarity, speed, and accessibility.
- Remove or hide incomplete experimental surfaces.

## Documentation tasks
- Create pilot runbook, onboarding checklist, support procedures, training guide, and incident-response notes.

## Testing tasks
- Run end-to-end pilot workflow tests across auth, tasks, assistant, AI, voice/vision if enabled, notifications, dashboards, and guest channels if enabled.
- Run migration rehearsal and backup/restore checks.

## Risks
- Pilot feedback can expose workflow assumptions that require rapid adjustment.
- Feature flags and tenant configuration must prevent unfinished features from leaking into pilot use.

## Definition of Done
- A pilot hotel can be onboarded, configured, trained, monitored, and supported.
- Pilot-critical workflows pass end-to-end tests.
- Rollback, backup, and support paths are documented.

## Dependencies on previous sprints
- Depends on all product increments through Sprint 12 and especially Sprint 1 tenant/auth foundations.
