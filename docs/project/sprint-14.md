# Sprint 14

## Sprint 14A - Non-Production External Recommendation Pilot

Sprint 14A adds a controlled pilot workflow for real non-production external
recommendation generation while keeping `internal-demo` as the default provider
and external generation disabled by default.

The pilot is configured under
`ops.ai.reservation.task-recommendations.pilot.*`. Enabled pilot configuration
requires allowed profiles, allowed external provider ids, explicit property
scope allowlist, bounded per-run limits, daily request budget, optional daily
token budget, fresh successful smoke diagnostics, pilot date-window validity,
and mandatory operator approval mode. Production profiles remain blocked.

Pilot readiness is provider-neutral and evaluates external provider readiness,
recent successful smoke test age, profile policy, credential/endpoint/model and
prompt configuration, allowed property scopes, budgets, pilot dates, and
operator-approval mode. Responses expose safe blocking reason codes only.

Pilot runs are durable in `recommendation_pilot_run`. Run summaries store safe
metadata only: run id, provider id, trigger, counts, model presence, prompt and
context versions, timestamps, failure category, and budget usage. They do not
store prompts, provider responses, reservation ids, property ids, guest data,
task text, raw PMS identifiers, or credentials.

Candidate selection reuses the deterministic recommendation candidate boundary
from successful reservation task automation executions, then restricts
candidates by configured property scope, supported reservation state, active
recommendation absence, maximum candidate age, and pilot batch limits.

The real provider generation boundary uses the configured external provider,
`RecommendationPrivacyGateway`, canonical prompt model, structured response
validation, durable budget reservation, and the existing recommendation
repository. Recommendations are persisted only as `REVIEW_REQUIRED`; no
recommendation approves, applies, or creates a task by itself.

Pilot rollback is deterministic: future pilot runs are durably disabled,
existing recommendations and diagnostics are preserved, and operators are
directed back to `internal-demo`. Rollback does not delete applied tasks and
does not require database restart.

Out of scope: production activation, autonomous AI action, live credentials in
the repository, public API changes, generated SDK changes, mobile changes,
outbound PMS updates, message brokers, and provider-side prompt/response
storage.

## Sprint 14B - Scheduled Pilot Runs and Operator Review Analytics

Sprint 14B adds disabled-by-default scheduled execution for the
non-production external recommendation pilot. The scheduler uses the existing
distributed `scheduler_lock` infrastructure with the dedicated job name
`reservation-task-recommendation-pilot`, calls only
`ExternalRecommendationPilotService`, and records pilot runs with trigger
`SCHEDULED`. Operator run-now remains trigger `OPERATOR`.

Pilot scheduling is configured under
`ops.ai.reservation.task-recommendations.pilot-schedule.*`. Enabled scheduling
requires the pilot itself to be enabled, an allowed non-production profile,
valid interval/startup/lease bounds, and the same pilot readiness, budget,
provider, and property-scope guardrails used for operator-triggered pilot
runs. Production profiles remain blocked.

Durable schedule state is stored on `recommendation_pilot_state`: paused state,
last attempted/successful run, last outcome, selected candidate count,
generated recommendation count, budget-rejection count, and safe failure
category. Pause survives restart and active runs are not cancelled.

Scheduled runs stop safely when readiness is blocked, the pilot date window
expires, the schedule is paused, another instance holds the lease, the daily
run limit is reached, or request/recommendation/token budgets are exhausted.
Already generated recommendations remain visible for operator review and stay
`REVIEW_REQUIRED`.

Sprint 14B also adds safe operator review analytics for pilot
recommendations. Analytics are derived from existing recommendation and
pilot-run records. Responses include only aggregate counts, rates, confidence
and category distributions, provider/model distribution, age bands, review-time
bands, duplicate-prevention totals, and failure totals. They do not expose
recommendation text, rationale text, reservation ids, task ids, property ids,
guest data, prompts, responses, credentials, raw PMS identifiers, or raw
provider payloads.

Review duration is defined as review transition timestamp minus recommendation
generated timestamp. Responses use bands: `under_5_minutes`,
`5_to_30_minutes`, `30_minutes_to_2_hours`, `2_to_24_hours`, and
`over_24_hours`.

Retention cleanup for pilot runs and pilot recommendations is explicit and
disabled by default. Cleanup uses a separate distributed job name,
`reservation-task-recommendation-pilot-cleanup`, preserves applied
recommendations according to longer retention policy, and does not remove
audit history.

## Future Production Infrastructure Notes

The broader Sprint 14 production-infrastructure plan remains future scope after
the non-production recommendation pilot is proven.

### Goal

Prepare production deployment on Azure with Container Apps, Blob Storage,
monitoring, security, performance, and CI/CD.

### Business value

Creates the operational platform needed to run Hotel OpAI reliably outside
pilot-only environments.

### Architecture impact

- Moves runtime strategy from local/pilot setup to production-grade cloud
  deployment.
- Completes production infrastructure work without treating infrastructure as a
  separate sprint category.

### Backend tasks

- Add production-ready health checks, readiness checks, configuration
  validation, graceful shutdown, and structured logging.
- Review API security headers, rate limits, tenant isolation, and audit
  coverage.

### Mobile tasks

- Configure production API environments, release channels, crash reporting, and
  production build settings.
- Validate push, voice, vision, and guest-channel configuration in
  production-like environments.

### AI tasks

- Configure production OpenAI and voice/vision provider settings through
  secrets.
- Enforce production cost limits, rate limits, retries, and monitoring alerts.

### UniMock tasks

- Decide whether UniMock is deployed only for staging/demo or excluded from
  production runtime.
- Configure staging UniMock with isolated data and access controls if deployed.

### Database tasks

- Configure managed PostgreSQL, Flyway migration execution strategy, backup
  policy, retention, PITR expectations, and database monitoring.
- Review indexes and query plans for production workloads.

### Infrastructure tasks

- Deploy Azure Container Apps for backend and supporting services.
- Configure Azure Blob Storage for attachments.
- Configure monitoring, logging, alerts, dashboards, secrets, network rules,
  CI/CD pipelines, and environment promotion.
- Add performance and load testing infrastructure.

### UI tasks

- Validate production mobile UI across target devices.
- Confirm external links, deep links, push navigation, and attachment rendering.

### Documentation tasks

- Document deployment runbook, CI/CD flow, environment variables, secret
  rotation, monitoring dashboards, incident response, and production support
  responsibilities.

### Testing tasks

- Run production-like smoke tests, load tests, security checks, migration dry
  runs, backup/restore checks, and release pipeline validation.
- Verify monitoring alerts fire for representative failures.

### Risks

- Cloud configuration drift can break reproducibility without automated
  deployment.
- Production secrets, storage, and logs can expose sensitive hotel data if not
  reviewed carefully.

### Definition of Done

- Production-like Azure environment can be deployed through CI/CD.
- Backend, mobile configuration, database, Blob Storage, monitoring, logging,
  and alerts are validated.
- Security and performance checks meet launch criteria.

### Dependencies on previous sprints

- Depends on pilot readiness from Sprint 13 and storage/attachment foundations
  from Sprint 7.
