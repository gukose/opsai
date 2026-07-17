# Operational Observability

Sprint 8D adds a small backend observability baseline using the existing
SLF4J and Micrometer infrastructure.

## Correlation IDs

- Request header: `X-Correlation-Id`.
- Accepted values are trimmed, at most 128 characters, and must match
  `[A-Za-z0-9._:-]+`.
- Missing, blank, malformed, or oversized values are replaced with a generated
  UUID.
- The final safe value is returned in `X-Correlation-Id`, stored in MDC as
  `correlationId`, and cleared after the request.
- Async correlation propagation is deferred.

## Metric Exposure

- Metrics are recorded internally through Micrometer.
- Public actuator exposure remains health/info only.
- `/actuator/metrics` is not public.
- Prometheus, OpenTelemetry, tracing, log shipping, and APM agents are deferred.
- Multi-instance metric aggregation requires future monitoring infrastructure.

## Metric Semantics

Sprint 8D metrics represent service-return outcomes. Sprint 8E adds outbox
metrics that represent enqueue, processing, retry, failure, and recovery
outcomes for internal event delivery. They are still internal application
metrics and are not exported through a public actuator metrics endpoint.

## Counters

- `hotelopai.assistant.conversation.total`
- `hotelopai.assistant.message.total`
- `hotelopai.assistant.confirmation.total`
- `hotelopai.attachment.registration.total`
- `hotelopai.vision.analysis.total`
- `hotelopai.vision.import.total`
- `hotelopai.task.lifecycle.total`
- `hotelopai.task.attachment.link.total`
- `hotelopai.task.attachment.read.total`
- `hotelopai.notification.operation.total`
- `hotelopai.security.denial.total`
- `hotelopai.rate_limit.rejection.total`
- `hotelopai.outbox.event.total`

## Timers

- `hotelopai.assistant.interpretation.duration`
- `hotelopai.assistant.confirmation.duration`
- `hotelopai.vision.analysis.duration`
- `hotelopai.vision.import.duration`
- `hotelopai.dashboard.summary.duration`
- `hotelopai.dashboard.reporting.duration`
- `hotelopai.notification.list.duration`
- `hotelopai.task.search.duration`
- `hotelopai.outbox.processing.duration`

The task-search timer is recorded at the task search application boundary.

## Allowed Tags

Only these low-cardinality tags are allowed:

- `operation`
- `outcome`
- `provider`
- `confidence_bucket`
- `status`
- `source_type`
- `endpoint_group`
- `event_type`
- `reason_code`
- `range`
- `transition`

Do not use tenant, user, resource, filename, room number, correlation ID, free
text, exception message, URI path with IDs, or payload values as metric tags.

## Finite Vocabularies

Common values include:

- `outcome`: `success`, `failure`, `not_found`, `conflict`, `duplicate`,
  `idempotent_reuse`, `metadata_conflict`, `validation_failure`,
  `clarification`, `preview`, `completed`, `ineligible`, `concurrency_conflict`
- `provider`: `deterministic`, `openai`, `unavailable`, `other`
- `confidence_bucket`: `low`, `medium`, `high`, `none`
- `endpoint_group`: `auth`, `assistant`, `tasks`, `notifications`,
  `dashboard`, `reporting`, `dev_pms`, `actuator`, `other`
- `source_type`: `assistant_message`, `vision_analysis`, `mixed`, `none`
- `event_type`: `task_created`
- `range`: `today`, `shift`, `7d`
- `reason_code`: stable application codes such as `none`,
  `operation_failed`, `validation_failure`, `task_not_found`,
  `limit_exceeded`, `concurrency_conflict`, and
  `idempotency_metadata_mismatch`
- `operation` for outbox: `enqueue`, `process`, `recover`
- `outcome` for outbox: `success`, `duplicate`, `retry`, `failed`,
  `recovered`
- `reason_code` for outbox: `none`, `event_already_exists`,
  `malformed_payload`, `unknown_payload_version`, `payload_mismatch`,
  `task_not_found`, `unsupported_event`, `handler_failure`, `stale_lock`

## Sensitive Data Policy

Operational logs and metric tags must not include:

- assistant text, transcripts, image observation text, prompts, or provider
  request/response bodies
- JWTs, refresh tokens, authorization headers, passwords, or API keys
- local URIs, file URIs, base64, binary data, or raw provider payloads
- filenames, room numbers, email addresses, hotel IDs, user IDs, task IDs,
  conversation IDs, attachment IDs, analysis IDs, import IDs, or notification IDs

Structured logs should use stable fields such as `event`, `operation`,
`outcome`, `reasonCode`, `provider`, `confidenceBucket`, and `sourceType`.

## Current Limitations

- Metrics are not exported to Prometheus or an external monitoring backend.
- There is no distributed tracing.
- DTO binding failures that occur before service execution are not counted by
  service-specific metrics unless a narrow future handler is added.
- Async MDC propagation is not implemented.
- Metrics are not aggregated across backend instances until future monitoring
  infrastructure is added.
