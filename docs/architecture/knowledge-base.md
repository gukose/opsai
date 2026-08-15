# Knowledge Base Foundation

Sprint 15A introduced an internal, provider-neutral knowledge base foundation.
Sprint 15B adds disabled-by-default semantic retrieval using provider-neutral
embeddings. The module stores Hotel OpAI owned operational knowledge only and
does not call external AI providers unless semantic search is explicitly enabled
with an external provider. Sprint 15C adds a disabled-by-default OpenAI
embedding adapter, durable provider diagnostics, scheduled refresh controls, and
retrieval-readiness reporting. Sprint 15D adds retrieval quality evaluation and
benchmarking so future RAG work can be gated by measured retrieval behavior
before any answer generation is introduced. Sprint 15E adds repository-owned
curated retrieval fixtures, explicit quality gates, CI verification, and
provider-neutral RAG context assembly. Sprint 15F adds strict prompt assembly
and disabled-by-default advisory answer generation using assembled Hotel OpAI
knowledge only.

## Domain

The knowledge domain owns:

- `KnowledgeDocument`: canonical document aggregate with title, source,
  category, language, optional authenticated hotel scope, metadata, timestamps,
  and generated chunks.
- `KnowledgeDocumentId` and `KnowledgeChunkId`: internal identifiers.
- `KnowledgeSource`: owned source classification such as imported markdown,
  imported text, SOP, maintenance manual, operations guide, training, and
  internal.
- `KnowledgeCategory`: operational category such as maintenance, housekeeping,
  front desk, safety, PMS, AI operations, and general.
- `KnowledgeChunk`: deterministic chunk of document text with order and optional
  heading.
- `KnowledgeMetadata`: normalized tags and bounded string attributes.

The document and chunk domain does not contain provider DTOs, prompts,
credentials, or AI responses. Embedding records live behind the application and
repository boundary and are keyed by chunk, provider, model, dimension, and a
deterministic content fingerprint.

## Persistence

PostgreSQL persistence uses:

- `knowledge_document` for optional hotel scope, title, category, source,
  language, original content, metadata, timestamps, and version.
- `knowledge_chunk` for chunk text, chunk order, optional heading, metadata,
  timestamps, and version.
- `knowledge_metadata` for normalized tags and bounded attributes.
- `knowledge_chunk_embedding` for chunk-level embedding status, provider id,
  model id, vector dimension, PostgreSQL `double precision[]` vector storage,
  content fingerprint, generated timestamp, retry metadata, and safe failure
  category.

The schema intentionally does not persist provider requests, provider responses,
prompts, credentials, or diagnostic payloads. `pgvector` is not required for the
current local/test setup; the repository owns vector comparison so a future
pgvector migration can replace the storage implementation without changing the
application boundary.

## Chunking

`DeterministicKnowledgeChunker` supports markdown and plain text imports.
Markdown imports preserve heading context. Both formats preserve paragraphs
before splitting long paragraphs into bounded chunks. Chunk order is stable and
based only on normalized input text plus configured `chunk-size` and
`chunk-overlap`.

## Import Pipeline

`KnowledgeBaseService` is the application boundary for imports. Markdown,
plain-text, future PDF, and future DOCX importers should all normalize into the
same `KnowledgeImportCommand` and then use the shared chunker and repositories.

## Embedding Lifecycle

`KnowledgeEmbeddingProvider` is the provider-neutral abstraction for bounded
batch embedding. Providers expose a stable id, model identifier, vector
dimension, provider type, readiness, and safe failure categories. The
deterministic local provider remains the default. The OpenAI provider is
implemented behind `ExternalKnowledgeEmbeddingProvider`, but it is disabled by
default and blocked in production unless a future sprint changes that policy.

`KnowledgeEmbeddingService` finds chunks with missing, failed, stale, or
fingerprint-mismatched embeddings, embeds them in bounded batches, and stores
READY or FAILED records idempotently. Re-chunking replaces chunks and therefore
invalidates obsolete embeddings through the chunk foreign key. Content changes
are detected with a SHA-256 fingerprint of normalized chunk text so unchanged
chunks are skipped.

Provider diagnostics are stored in `knowledge_embedding_provider_diagnostic`.
They contain provider id, model presence, readiness, outcome, latency band,
batch size, timestamp, and safe failure category only. Diagnostics never store
vectors, content, prompts, credentials, request bodies, response bodies, or raw
provider payloads.

## Search

Search supports three internal modes:

- `KEYWORD`: existing deterministic title, tag, and chunk-text ranking.
- `SEMANTIC`: embeds the query and ranks READY chunk embeddings by cosine
  similarity with configurable threshold and result limit.
- `HYBRID`: combines normalized keyword and semantic scores with configured
  weights and stable tie-breaking.

Strict semantic search fails safely when semantic search is disabled or the
active provider is unavailable. Keyword fallback is used only for hybrid
searches when `keyword-fallback-enabled` is explicitly configured. Search
responses expose document/chunk metadata, snippets, and safe numeric score
components only; raw vectors are never returned.

Internal API calls set document and search scope from the authenticated user's
hotel claim. Direct application-service use may omit scope for system tests and
local workflows, but controllers must not return another hotel's documents or
chunks.

## Operations

Internal endpoints live under `/api/v1/internal/knowledge` and require
`KNOWLEDGE_OPERATIONS`.

- `POST /documents/import`
- `GET /documents`
- `GET /documents/{documentId}`
- `DELETE /documents/{documentId}`
- `POST /documents/{documentId}/rechunk`
- `GET /search`
- `GET /embeddings/status`
- `GET /embeddings/providers`
- `GET /embeddings/retrieval-readiness`
- `POST /embeddings/generate`
- `POST /documents/{documentId}/embeddings/regenerate`
- `GET /embeddings/failures`
- `POST /embeddings/retry`
- `GET /embeddings/diagnostics`
- `GET /embeddings/diagnostics/{diagnosticId}`
- `POST /embeddings/diagnostics/cleanup`
- `GET /embeddings/schedule`
- `POST /embeddings/schedule/run-now`
- `POST /embeddings/schedule/pause`
- `POST /embeddings/schedule/resume`
- `POST /retrieval/evaluations/run`
- `GET /retrieval/evaluations`
- `GET /retrieval/evaluations/{runId}`
- `POST /retrieval/benchmark`
- `GET /retrieval/readiness-report`
- `GET /retrieval/curated-dataset/validate`
- `POST /retrieval/quality-gate/run`
- `GET /retrieval/quality-gate/latest`
- `POST /retrieval/context/assemble`
- `POST /answers/test-query`
- `GET /answers`
- `GET /answers/{answerId}`
- `POST /answers/{answerId}/retry`
- `POST /answers/cleanup`
- `GET /answers/provider-readiness`

These endpoints are internal-only and are not mobile workflows. They do not
expose prompts, AI responses, credentials, provider configuration, provider
payloads, or raw embedding vectors.

## Configuration

Semantic search is disabled by default under `ops.ai.knowledge.semantic-search`.
Enabled configuration validates the active provider, vector dimension, batch
size, timeout, profile allowlist, similarity threshold, result limit, and hybrid
weights at startup. Disabled semantic search must not block application startup.

OpenAI embedding configuration lives under
`ops.ai.knowledge.semantic-search.external-providers.openai.*`. Enabled OpenAI
configuration validates endpoint policy, profile allowlist, credential
reference, model, dimension, timeout, and production prohibition without making
a network request at startup. Local endpoints require explicit smoke-test
enablement.

The refresh scheduler lives under `ops.ai.knowledge.semantic-search.schedule.*`
and is disabled by default. When enabled, it uses the shared distributed
`scheduler_lock` infrastructure and processes bounded batches through
`KnowledgeEmbeddingService`; pause and resume state is durable in
`knowledge_embedding_schedule_state`.

Retrieval readiness reports `DISABLED`, `NOT_INDEXED`, `NOT_EVALUATED`,
`QUALITY_GATE_FAILED`, `READY_WITH_WARNINGS`, `READY`, `DEGRADED`,
`PARTIALLY_INDEXED`, `INDEXING`, `MISCONFIGURED`, or `PROVIDER_UNAVAILABLE`
from provider readiness, total chunk count, READY/FAILED/STALE embedding
counts, scheduler pause state, the latest retrieval evaluation result, and
enabled quality thresholds. It does not perform provider network calls.

## Retrieval Evaluation

Retrieval evaluation accepts bounded internal datasets containing a query,
expected document ids, expected chunk ids, and optional relevance score. The
query is used to execute retrieval but is not persisted in the run summary.
Runs store safe aggregate metadata only: run id, name, status, case count, `k`,
modes, timestamps, safe failure category, and per-mode aggregate metrics.

Metrics are deterministic:

- Precision@K
- Recall@K
- Mean Reciprocal Rank
- NDCG
- Hit Rate

Benchmarking runs the same dataset across `KEYWORD`, `SEMANTIC`, and `HYBRID`
through the existing `KnowledgeBaseService` search boundary. Evaluation metrics
are retrieval quality gates for future RAG; they are not answer quality or model
accuracy.

## Curated Quality Gates

Sprint 15E owns a synthetic Hotel OpAI retrieval dataset in application code.
The dataset covers maintenance procedures, housekeeping standards, check-in and
checkout procedures, emergency instructions, facilities hours, room equipment
troubleshooting, guest-service procedures, and incident escalation. Cases use
stable case ids, queries, expected document references, relevance levels,
retrieval modes, category, and language. Fixture documents use the metadata key
`curated_ref` when imported into isolated verification databases.

Dataset validation checks unique case and document references, non-empty
queries, valid expected references, supported relevance values, deterministic
ordering, supported language/category values, and obvious sensitive-data
patterns. Invalid datasets fail before evaluation starts.

Quality thresholds live under `ops.ai.knowledge.retrieval-quality.*` and are
disabled by default outside explicit verification. Thresholds can be mode
specific for `KEYWORD`, `SEMANTIC`, and `HYBRID` and include Precision@K,
Recall@K, MRR, NDCG, Hit Rate, maximum average latency, and minimum evaluated
query count. A failed mode is reported independently; another mode cannot hide
it.

The Gradle task `:backend:verifyKnowledgeRetrievalQuality` imports the curated
fixtures into an isolated PostgreSQL test database, uses the deterministic local
embedding provider, runs the quality gate, and fails with an actionable report
when mandatory thresholds are missed. It performs no OpenAI or live external
provider calls.

## RAG Context Assembly

`KnowledgeContextAssembler` is the future RAG boundary. It accepts a sanitized
query plus hotel scope, calls existing retrieval services, selects bounded top
results, removes exact duplicates, enforces per-document and total character
limits, filters by score, category, and language, and returns structured context
items.

Each context item contains selected bounded text, retrieval mode, safe score
components, and a structured citation with document reference, chunk reference,
title, category, chunk position, retrieval score, and content fingerprint. It
does not produce prompts or answers and never returns vector values.

## RAG Prompt And Advisory Answers

`KnowledgePromptAssembler` converts an operator query and assembled context into
a versioned prompt object with system instructions, ordered context items,
citation ids, and an expected structured output schema. Prompt text is transient
and is never persisted.

`KnowledgeAnswerPrivacyGateway` rejects unsupported sensitive query and output
patterns before or after provider generation. It guards against contact data,
payment wording, credentials, raw PMS-style identifiers, and webhook payload
markers. This is an application boundary and does not rely on prompt
instructions alone.

`KnowledgeAnswerProvider` is the provider-neutral answer abstraction. The
current `InternalDemoKnowledgeAnswerProvider` is deterministic and local.
External answer providers remain disabled by default and should follow the same
credential, HTTP, validation, and diagnostics conventions as the embedding and
recommendation providers.

Grounding validation requires `ANSWERED` responses to include at least one
known citation id from the supplied context. Unknown citations, missing
citations, overlong answers, sensitive output, and task/reservation action
directives fail safely. `INSUFFICIENT_CONTEXT` responses must not fabricate
citations. Answers are advisory only and cannot create tasks, modify
reservations, or trigger operational actions.

Answer history is stored in `knowledge_answer_history`. It contains safe
execution metadata: answer id, optional hotel scope, provider/model,
prompt/template version, retrieval mode, context schema version, status,
confidence, citation references, timestamps, request fingerprint, safe failure
category, and optional final answer text. It never stores full prompts, raw
provider responses, credentials, embeddings, provider payloads, reservation
data, guest data, PMS data, or webhook payloads.

## Internal Knowledge Assistant And External Answer Pilot

Sprint 16A adds an internal operator assistant surface on top of the existing
grounded answer boundary. The backend route family remains
`/api/v1/internal/knowledge/**` and requires `KNOWLEDGE_OPERATIONS`; the mobile
Knowledge Assistant tab is hidden unless that permission is present in the
current token.

Answer-provider governance mirrors the recommendation and embedding provider
patterns:

- `InternalDemoKnowledgeAnswerProvider` remains the default and has no network
  dependency.
- `OpenAiKnowledgeAnswerProvider` is registered as an external provider, but is
  disabled by default.
- External answer providers are explicitly blocked for `prod`/`production`
  profiles during this sprint.
- Local/stub smoke tests require explicit `smoke-test-enabled` and
  `fixture-mode-enabled` configuration.
- Provider diagnostics store only safe metadata: provider id, diagnostic type,
  trigger, timestamps, outcome, failure category, latency band, validation
  outcome, prompt/template version, model presence, and environment class.

The smoke-test service uses a fixed synthetic knowledge context. It never reads
database documents, creates answer-history records, creates tasks, or persists
prompts/provider responses. Fixture modes cover success, empty success,
malformed response, timeout, rate limit, authentication failure, and provider
unavailability.

Operator feedback is stored in `knowledge_answer_feedback` with answer id,
feedback type, actor id, and timestamp. Free-text feedback is intentionally out
of scope. Feedback does not modify answers, trigger retraining, or affect task
or reservation state.

Answer quotas are checked per hotel/operator before provider invocation. Quota
rejections persist a safe answer-history failure category and emit safe metrics;
they do not expose query text in audit or diagnostics.

Sprint 16B adds `knowledge_answer_request_lifecycle` and
`knowledge_answer_inflight_scope`. The scope row serializes in-flight capacity
acquisition per hotel/operator across backend instances. Lifecycle records move
through `REQUESTED`, `RETRIEVING`, `GENERATING`, `VALIDATING`, and terminal
states. Active records older than the configured abandoned timeout are recovered
as `ABANDONED` and no longer consume capacity.

Lifecycle records store only safe metadata: request id, answer-link presence,
hotel/actor scope, provider/model presence, retrieval mode, status, timestamps,
safe failure category, citation-count band, latency band, and request
fingerprint. They never store query text, prompt text, retrieved chunk text,
provider responses, credentials, embeddings, PMS data, reservation data, guest
data, or payment data.

The internal Knowledge Assistant dashboard aggregates provider readiness,
retrieval readiness, recent outcome counts, quota usage, in-flight count,
abandoned count, feedback distribution, citation bands, latency bands, and safe
failure categories. Mobile displays only these aggregate fields for users with
`KNOWLEDGE_OPERATIONS`.

Cancellation is cooperative. The backend can mark active lifecycle records as
`REJECTED` with `CANCELLED`, which releases capacity and prevents a partial
answer from being treated as successful. The current HTTP boundary does not
interrupt an already-running provider transport call.

## Future Work

Future sprints can add PDF/DOCX parsers, pgvector query acceleration, document
retention policies, richer curated datasets, stricter environment-specific
quality gates, richer answer review analytics, external-provider production
approval, and user-facing RAG. Those additions should plug into the current
import, chunk, embedding-provider, repository, scheduler, readiness,
evaluation, quality-gate, context-assembly, prompt, privacy, answer-provider,
and search boundaries without changing public product APIs.
