# External Recommendation Provider Smoke Testing

Sprint 13F supports non-production operational readiness checks for external
task-recommendation providers. `internal-demo` remains the default active
provider. OpenAI is disabled by default, and production activation is blocked.

## Configuration

Use placeholders only and inject secret values through the configured reference
source. Do not place raw credentials in application files.

```yaml
ops:
  ai:
    reservation:
      task-recommendations:
        active-provider: internal-demo
        external-providers:
          allowed-profiles: [local, test]
          production-prohibited: true
          require-https-outside-local: true
          diagnostics-retention: P30D
          diagnostics-cleanup-batch-size: 100
        providers:
          openai:
            enabled: true
            activation-mode: SMOKE_TEST_ONLY
            allow-fallback-to-internal-demo: false
            endpoint: http://localhost:PORT/v1/chat/completions
            model: MODEL_PLACEHOLDER
            credential-reference:
              source: ENVIRONMENT
              name: OPENAI_API_KEY_PLACEHOLDER
            smoke:
              enabled: true
              fixture-mode-enabled: true
```

For a non-production HTTPS endpoint, keep `fixture-mode-enabled=false` and use
`activation-mode=SMOKE_TEST_ONLY` unless runtime generation is intentionally
enabled for a controlled non-production test. Sprint 13F rejects production
profiles.

## Readiness

Inspect readiness through the internal recommendation-provider operations. Safe
readiness states are:

- `DISABLED`: provider is configured off.
- `MISCONFIGURED`: required model, credential reference, endpoint, or local
  stub configuration is missing or invalid.
- `BLOCKED_BY_ENVIRONMENT`: the active profile is not allowed.
- `READY_FOR_LOCAL_SMOKE`: deterministic local/stub smoke testing can run.
- `READY_FOR_NON_PRODUCTION`: non-production runtime generation policy is valid.
- `TEMPORARILY_UNAVAILABLE`: latest smoke failure was transient.
- `PRODUCTION_BLOCKED`: production activation was rejected.

Readiness does not perform network requests.

## Smoke Tests

The smoke service builds a fixed synthetic context with no real reservation,
property, guest, webhook, PMS, or task identifiers. The request runs through
the privacy gateway, prompt model, OpenAI adapter, HTTP boundary, and response
validator. It does not persist recommendations or create tasks.

Allowed local fixture modes:

- `SUCCESS`
- `EMPTY_SUCCESS`
- `MALFORMED_RESPONSE`
- `TIMEOUT`
- `RATE_LIMITED`
- `AUTHENTICATION_FAILURE`
- `PROVIDER_UNAVAILABLE`

Fixture mode is accepted only when provider smoke mode and fixture mode are
explicitly enabled in a local/test profile.

## Diagnostics

Smoke results are stored in `recommendation_provider_diagnostic`. Diagnostics
contain provider id, diagnostic type, trigger type, timestamps, outcome, safe
failure category, latency band, retry count, validation outcome, prompt
version, model identifier, environment class, and endpoint classification.

Diagnostics never store prompts, responses, request bodies, credentials,
provider request ids, reservation context, recommendation content, reservation
ids, property ids, guest data, notes, PMS DTOs, or webhook payloads.

Retention cleanup is explicit and bounded. It preserves active diagnostics and
keeps the latest provider result where practical.

## Rollback

To stop external traffic:

1. Set `ops.ai.reservation.task-recommendations.providers.openai.enabled=false`.
2. Set `ops.ai.reservation.task-recommendations.active-provider=internal-demo`.
3. Remove local stub endpoint and credential-reference overrides.
4. Restart the backend and inspect provider readiness. OpenAI should report
   `DISABLED`.

No fallback to `internal-demo` is silent. If fallback is explicitly configured,
it must appear in diagnostics and audit.

## Troubleshooting

`TIMEOUT` or `PROVIDER_UNAVAILABLE` indicates transient external reachability
or stub availability issues. A later success records recovery.

`RATE_LIMIT` indicates the provider rejected the request rate. Smoke tests do
not consume recommendation-generation retry budgets.

`AUTHENTICATION` or `MISCONFIGURED` indicates credential-reference or provider
authorization problems. Diagnostic output is redacted and should not include
credential values.

`INVALID_RESPONSE` indicates the adapter received malformed or unsupported
structured output. The response body is not persisted or returned.

## Non-Production Pilot

After smoke readiness is fresh, a non-production pilot can be enabled with a
bounded allowlist and budget:

```yaml
ops:
  ai:
    reservation:
      task-recommendations:
        pilot:
          enabled: true
          allowed-profiles: [local, test]
          allowed-provider-ids: [openai]
          allowed-property-scopes: [PROPERTY_SCOPE_PLACEHOLDER]
          max-reservations-per-run: 5
          max-recommendations-per-run: 10
          daily-request-budget: 25
          daily-token-budget: 100000
          required-successful-smoke-age: PT24H
          mandatory-operator-approval-mode: true
        pilot-schedule:
          enabled: false
          execution-interval: PT6H
          startup-delay: PT2M
          batch-size: 5
          max-runs-per-day: 2
          allowed-profiles: [local, test]
          lock-timeout: PT10M
          minimum-interval-between-runs: PT1H
```

Before starting a run, inspect pilot readiness and budget through internal
operations. A pilot run selects bounded canonical reservation candidates,
invokes the configured external provider, and persists only
`REVIEW_REQUIRED` recommendations. Operators must still approve and apply a
recommendation through the existing review workflow before any task is created.

To stop the pilot, use the internal disable operation. To roll back, use the
rollback-to-InternalDemo operation, then remove external provider overrides.
Rollback preserves recommendation history, diagnostics, and applied tasks.
Production activation remains prohibited.

## Scheduled Pilot Runs

Scheduled pilot execution remains disabled by default. To test it in
non-production, enable `pilot.enabled`, verify a recent successful smoke test,
configure the property scope allowlist and daily budgets, then enable
`pilot-schedule.enabled`. The scheduler uses a distributed lease and the same
readiness/budget guardrails as operator-triggered pilot runs.

Use internal operations to inspect schedule status, pause, resume, or run one
bounded batch now. Run-now is recorded as an operator action, not as an
automatic scheduled run. Pausing prevents future scheduled starts but does not
cancel an active run.

Daily run limits and request/recommendation/token budgets are hard stops. If a
limit is reached, the run is rejected or finalized safely and already generated
recommendations remain available for review.

## Review Analytics

Pilot analytics are internal, aggregate-only, and safe for operator review.
Use the analytics endpoints to inspect generated, approved, rejected, expired,
and applied counts, approval/rejection/apply rates, review-time bands,
confidence and category distributions, provider/model distribution, age bands,
duplicate prevention, and failure counts.

Analytics never return recommendation text, rationale text, reservation ids,
task ids, property ids, prompts, responses, credentials, guest data, raw PMS
identifiers, or raw provider metadata.

## Stop and Roll Back

To stop scheduled pilot activity, pause the pilot schedule and disable future
pilot generation. To roll back provider selection, use the
rollback-to-InternalDemo operation and remove external provider overrides from
the environment. Existing recommendations, diagnostics, audit history, and
applied tasks are preserved.
