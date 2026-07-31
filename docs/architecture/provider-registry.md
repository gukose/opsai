# Provider Registry

Hotel OpAI uses provider registries to keep business services provider-neutral.
Sprint 13E adds this pattern to task recommendations.

`TaskRecommendationProviderRegistry` registers providers by stable provider id,
rejects duplicate ids, resolves the configured active provider, validates
required capabilities, and exposes sanitized provider diagnostics. InternalDemo
and external providers use the same mechanism.

Provider diagnostics may include:

- provider id
- display name
- provider type: `INTERNAL` or `EXTERNAL`
- lifecycle: `REGISTERED`, `AVAILABLE`, `DISABLED`, `MISCONFIGURED`, or
  `UNAVAILABLE`
- active flag
- safe capability names
- active model when configured
- prompt/template version
- safe failure category and coarse response-time/retry summary

Diagnostics must not include prompts, request bodies, responses, credentials,
reservation ids, task ids, raw PMS identifiers, guest data, notes, payment data,
or webhook payloads.

External providers must keep provider-specific DTOs and HTTP concerns inside
their integration package. They receive only the outbound context produced by
`RecommendationPrivacyGateway` and return canonical structured recommendation
responses.

## External Recommendation Readiness

Sprint 13F adds readiness assessment for external recommendation providers.
Readiness answers whether a provider is operationally usable for local smoke or
non-production generation; lifecycle still describes registration/configuration
state. Readiness does not perform network traffic. It uses safe signals:
enabled state, active-provider selection, credential-reference resolvability,
endpoint policy, model and prompt configuration, capability declarations,
environment/profile policy, fallback policy, and the latest smoke diagnostic.

Production activation is prohibited for Sprint 13F. An enabled external
provider in a production profile fails startup with a sanitized actionable
message. Disabled external providers do not fail startup. Local stub endpoints
must be explicitly enabled for smoke testing, and production-like generation
does not accept fixture modes.

Provider diagnostics endpoints expose endpoint classification only:
`LOCAL_STUB`, `EXTERNAL_HTTPS`, `EXTERNAL_HTTP`, or `INVALID`. They never return
endpoint URLs, credentials, prompt text, response bodies, provider request ids,
reservation ids, task ids, guest data, PMS identifiers, or webhook payloads.
