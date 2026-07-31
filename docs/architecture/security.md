# Security Architecture

This document captures cross-cutting security boundaries that do not belong to
one public API contract.

## Recommendation LLM Boundary

Sprint 13E keeps external LLM recommendation providers disabled by default.
The active default remains `internal-demo`.

External recommendation providers must receive only the outbound context
produced by `RecommendationPrivacyGateway`. The outbound schema excludes guest
names, emails, phone numbers, notes, payment data, reservation ids, external
reservation references, raw PMS property identifiers, webhook payloads, provider
DTOs, raw prompts with personal data, request bodies, responses, and
credentials.

Credential values are resolved only at the provider adapter boundary through
`RecommendationCredentialResolver`. They are never persisted, logged, returned
from diagnostics, audited, or included in metrics. Environment variables are
the only implemented source in Sprint 13E; future secret references and vault
integration must keep the same no-exposure contract.

Internal diagnostics may expose provider id, lifecycle, capabilities, active
model name, prompt/template version, coarse response time band, retry summary,
and safe failure category. Diagnostics must not expose prompts, generated
responses, request bodies, tokens, guest data, reservation references, task ids,
or PMS identifiers.

Sprint 13F adds local/stub smoke testing and durable provider diagnostic
history. Smoke tests use fixed synthetic context only and do not query
reservation storage. Diagnostic records store safe metadata such as outcome,
failure category, latency band, validation outcome, prompt version, model
identifier, environment class, and endpoint classification. They never store
prompt text, response bodies, request payloads, credentials, provider request
ids, recommendation content, guest data, reservation ids, property ids, raw PMS
identifiers, or webhook payloads.

External provider production activation is prohibited for Sprint 13F. Local
fixture modes require explicit local/test smoke configuration and cannot be
selected through production-like runtime generation.
