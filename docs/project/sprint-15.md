# Sprint 15 - Hardening + Release Candidate

## Goal
Complete bug fixing, performance tuning, documentation, regression testing, and release-candidate preparation.

## Business value
Reduces launch risk by stabilizing the full product and operational support model.

## Architecture impact
- Freezes architecture except for fixes required by security, reliability, or performance findings.
- Converts production readiness into a release candidate.

## Backend tasks
- Fix high-priority bugs across auth, tenant isolation, workflow, assistant, AI adapters, notifications, guest channels, reporting, and simulation boundaries.
- Tune slow endpoints and remove unsafe temporary runtime paths.
- Review error handling and audit logging.

## Mobile tasks
- Fix release-blocking mobile defects, accessibility issues, navigation edge cases, offline/error states, and push/deep-link issues.
- Prepare store/release artifacts if applicable.

## AI tasks
- Tune prompts, validation, confidence thresholds, fallback behavior, multilingual responses, and cost controls.
- Confirm AI cannot bypass preview/confirmation or manager approval rules.

## UniMock tasks
- Use UniMock and Simulation Engine runs for regression and demo validation.
- Fix simulator contract issues that affect testing confidence.

## Database tasks
- Review Flyway migration history, indexes, constraints, data-retention jobs, backup/restore rehearsal, and migration performance.
- Remove obsolete tables only through explicit migrations if safe.

## Infrastructure tasks
- Tune production resource limits, autoscaling, alert thresholds, log retention, and CI/CD gates.
- Complete vulnerability/dependency checks and release pipeline hardening.

## UI tasks
- Polish release-critical screens and fix layout, text overflow, state, and accessibility defects.
- Ensure incomplete or risky features are disabled by feature flag.

## Documentation tasks
- Finalize release notes, admin guide, support runbook, troubleshooting guide, API notes, and regression checklist.

## Testing tasks
- Run full regression suite, end-to-end tests, performance tests, security checks, migration tests, backup/restore tests, and mobile release validation.
- Verify known pilot issues are fixed or explicitly accepted.

## Risks
- Late architecture changes can destabilize the release candidate.
- Hidden tenant-isolation or notification defects can be expensive after launch.

## Definition of Done
- Release candidate is built, tested, documented, and accepted against launch criteria.
- Critical and high-priority launch defects are resolved or formally accepted.
- Regression results are documented.

## Dependencies on previous sprints
- Depends on Sprint 14 production infrastructure and all previous product increments.

## Sprint 15A - Knowledge Base Foundation & Retrieval Architecture

Sprint 15A adds an internal backend-only knowledge base foundation. It does not
change public APIs, generated SDKs, mobile behavior, AI provider activation, or
external provider traffic.

Implemented foundation:

- Added provider-neutral knowledge domain models for documents, chunks,
  sources, categories, metadata, and internal identifiers.
- Added PostgreSQL persistence for documents, chunks, and metadata through
  Flyway migration `V30__create_knowledge_base_tables.sql`.
- Added deterministic markdown and plain-text chunking with configurable chunk
  size, overlap, paragraph preservation, and markdown heading awareness.
- Added an import pipeline that currently accepts markdown and plain text and
  leaves PDF/DOCX support as future adapters into the same command boundary.
- Added keyword-only retrieval across title, tags, and chunk text with
  deterministic ranking and bounded pagination.
- Added internal operations under `/api/v1/internal/knowledge` requiring
  `KNOWLEDGE_OPERATIONS`.
- Added safe metrics for imports, chunk generation, searches, and deletions.

Privacy and scope:

- No embeddings are stored.
- No vector database integration exists.
- No OpenAI or external AI provider calls are made.
- Knowledge operations do not expose prompts, AI responses, credentials, or
  provider configuration.

## Sprint 15B - Semantic Search & Embedding Foundation

Sprint 15B adds provider-neutral embedding infrastructure and internal semantic
retrieval while preserving keyword search. Semantic search remains disabled by
default and no external AI provider is called by default.

Implemented foundation:

- Added `KnowledgeEmbeddingProvider` with stable provider id, model identifier,
  vector dimension, bounded batch embedding, readiness, and safe failure
  categories.
- Added a deterministic local embedding provider for tests and local
  development.
- Added typed `ops.ai.knowledge.semantic-search.*` configuration for active
  provider, model, dimension, batch size, timeout, retry bounds, profile
  allowlist, similarity threshold, result limits, hybrid weights, and explicit
  keyword fallback.
- Added Flyway migration `V31__create_knowledge_chunk_embeddings.sql` for
  PostgreSQL-backed chunk embeddings using `double precision[]` vectors and
  deterministic content fingerprints.
- Added an embedding lifecycle service that finds missing, failed, stale, or
  fingerprint-mismatched chunks, embeds bounded batches, stores results
  idempotently, skips unchanged chunks, and invalidates obsolete embeddings
  after re-chunking or deletion.
- Extended internal knowledge search with `KEYWORD`, `SEMANTIC`, and `HYBRID`
  modes. Hybrid search combines normalized keyword and semantic scores with
  configured weights and deterministic tie-breaking.
- Scoped internal knowledge import, document lookup, deletion, re-chunking, and
  keyword/semantic/hybrid retrieval to the authenticated user's hotel.
- Added internal embedding operations for status, batch generation, document
  regeneration, failed-record listing, and retry under `KNOWLEDGE_OPERATIONS`.
- Added safe metrics for embedding batches, chunk outcomes, semantic/hybrid
  searches, fallback usage, and result-count bands.

Privacy and scope:

- Raw vectors are never returned by internal APIs.
- Provider request/response bodies, prompts, credentials, and provider payloads
  are not logged, audited, returned, or persisted.
- Strict semantic search fails safely when disabled or unavailable.
- Keyword fallback happens only for hybrid search when explicitly configured.
- RAG answer generation, external embedding providers, vector databases, and
  OpenAI calls remain out of scope.

## Sprint 15C - External Embedding Provider, Scheduled Refresh & Retrieval Readiness

Sprint 15C adds operational readiness around embeddings without changing public
API, SDK, or mobile behavior.

Implemented foundation:

- Added `ExternalKnowledgeEmbeddingProvider` and a disabled-by-default OpenAI
  embedding adapter behind the existing `KnowledgeEmbeddingProvider` interface.
- Added typed external provider configuration for enablement, model, endpoint,
  dimensions, timeout, retry bounds, profile allowlist, credential reference,
  endpoint policy, production blocking, and smoke-test enablement.
- Added durable provider diagnostics in
  `knowledge_embedding_provider_diagnostic` with safe metadata only.
- Added durable embedding refresh schedule state in
  `knowledge_embedding_schedule_state`.
- Added disabled-by-default scheduled embedding refresh using the shared
  distributed `scheduler_lock` infrastructure and bounded calls through
  `KnowledgeEmbeddingService`.
- Added internal scheduler status, run-now, pause, resume, diagnostics, cleanup,
  provider summary, and retrieval-readiness operations under
  `KNOWLEDGE_OPERATIONS`.
- Added retrieval readiness states: `DISABLED`, `READY`,
  `PARTIALLY_INDEXED`, `INDEXING`, `MISCONFIGURED`, and
  `PROVIDER_UNAVAILABLE`.

Privacy and scope:

- OpenAI remains disabled by default and production activation remains blocked
  by default.
- Startup validation for enabled external providers is sanitized and does not
  perform network calls.
- Diagnostics and internal operations never expose embeddings, prompts,
  credentials, provider request/response bodies, raw provider payloads, or
  document content beyond the existing internal knowledge document/search
  responses.
- RAG answer generation remains out of scope.

## Sprint 15D - Retrieval Quality Evaluation & RAG Readiness

Sprint 15D adds internal retrieval quality evaluation and benchmarking without
generating AI answers.

Implemented foundation:

- Added bounded retrieval evaluation datasets with query, expected document ids,
  expected chunk ids, optional relevance score, modes, and `k`.
- Added PostgreSQL-backed evaluation run history in
  `knowledge_retrieval_evaluation_run` and aggregate per-mode metrics in
  `knowledge_retrieval_evaluation_metric`.
- Added deterministic Precision@K, Recall@K, Mean Reciprocal Rank, NDCG, and
  Hit Rate calculations.
- Added benchmarking across `KEYWORD`, `SEMANTIC`, and `HYBRID` through the
  existing provider-neutral search boundary.
- Added evaluation-aware retrieval readiness with `READY_WITH_WARNINGS`,
  `NOT_EVALUATED`, and `DEGRADED` states.
- Added internal operations for running evaluations, listing history, reading
  details, running benchmarks, and viewing the readiness report under
  `KNOWLEDGE_OPERATIONS`.

Privacy and scope:

- Evaluation run summaries store aggregate metrics only and do not persist
  embeddings, provider payloads, request/response bodies, credentials, prompts,
  or generated answers.
- RAG answer generation remains out of scope.

## Sprint 15E - Curated Retrieval Evaluation, Quality Gates & RAG Context Assembly

Sprint 15E adds deterministic retrieval quality gates and a provider-neutral
RAG context assembly boundary. It preserves public API, SDK, mobile behavior,
internal-only knowledge operations, disabled external providers, and the
no-answer-generation boundary.

Implemented foundation:

- Added a repository-owned synthetic hotel-operations curated dataset covering
  maintenance, housekeeping, front desk, safety, facilities, room equipment,
  guest-service, and incident escalation scenarios.
- Added curated dataset validation for stable ids, expected references,
  non-empty queries, relevance values, language/category support, deterministic
  ordering, and obvious sensitive-data patterns.
- Added typed `ops.ai.knowledge.retrieval-quality.*` configuration with
  disabled-by-default gates and mode-specific thresholds for Precision@K,
  Recall@K, MRR, NDCG, Hit Rate, latency, and minimum evaluated query count.
- Added `KnowledgeRetrievalQualityGateService`, which evaluates each requested
  retrieval mode independently and returns `PASS`, `PASS_WITH_WARNINGS`, or
  `FAIL` without mutating production knowledge documents.
- Added `:backend:verifyKnowledgeRetrievalQuality`, which loads curated
  fixtures into an isolated PostgreSQL test database, indexes them with the
  deterministic local embedding provider, and fails on missed mandatory gates.
- Extended retrieval readiness with `NOT_INDEXED` and
  `QUALITY_GATE_FAILED` states when quality gates are active.
- Added `KnowledgeContextAssembler` for bounded, tenant-scoped, provider-neutral
  RAG context assembly with duplicate removal, source citations, category and
  language filters, score thresholds, and per-document/total character limits.
- Added internal operations for curated dataset validation, quality-gate
  execution, latest quality report, RAG context assembly, and readiness
  inspection under `KNOWLEDGE_OPERATIONS`.

Privacy and scope:

- The curated dataset is synthetic and contains no real hotel, guest, employee,
  reservation, property, credential, prompt, provider payload, or response data.
- Quality reports persist aggregate retrieval metrics only.
- Context assembly may return bounded selected knowledge text to authorized
  internal operators, but returns no vectors, prompts, credentials, provider
  payloads, request bodies, or response bodies.
- CI quality verification uses local deterministic embeddings only and makes no
  OpenAI or live external provider calls.

## Sprint 15F - RAG Prompt Assembly & Advisory Answer Generation Foundation

Sprint 15F adds the first advisory answer-generation boundary for retrieved
knowledge. It does not create tasks, mutate reservations, trigger operational
actions, call external providers by default, or expose the answer surface
outside internal knowledge operations.

Implemented foundation:

- Added provider-neutral answer models for request, answer, status, confidence,
  citation, safe failure category, and answer repository boundaries.
- Added `KnowledgePromptAssembler` for deterministic, versioned prompt objects
  containing system instructions, sanitized operator query, structured context,
  citation ids, and expected structured output schema.
- Added `KnowledgeAnswerPrivacyGateway` to reject unsupported sensitive query
  and output patterns before relying on provider instructions.
- Added `KnowledgeAnswerProvider` and deterministic
  `InternalDemoKnowledgeAnswerProvider` for local/test answer generation.
- Added grounding validation requiring answered responses to cite supplied
  context and rejecting unknown citations, missing citations, overlong answers,
  sensitive output, and unsupported task/reservation action directives.
- Added PostgreSQL-backed answer history in
  `V34__create_knowledge_answer_history.sql` with safe metadata and optional
  final answer text only.
- Added duplicate suppression using a stable request fingerprint and bounded
  duplicate window.
- Added internal answer operations under `/api/v1/internal/knowledge/answers`
  requiring `KNOWLEDGE_OPERATIONS`.

Privacy and scope:

- Answer generation is disabled by default under `ops.ai.knowledge.answers.*`.
- Prompt text, raw provider responses, credentials, embeddings, provider
  payloads, PMS data, webhook payloads, guest data, reservation data, and
  payment data are not persisted or exposed.
- Every answered response must include source citations from assembled
  knowledge context.
- Insufficient grounding returns `INSUFFICIENT_CONTEXT` or a safe validation
  failure instead of fabricating an answer.
