# Sprint 7 - Vision Pipeline Foundation

## Goal

Build the safe foundation for image-assisted assistant workflows without claiming that binary upload, durable media storage, provider-accessible images, or real image analysis exists.

Sprint 7 lets staff select an image locally, register declared metadata, reference a backend-owned attachment identity in assistant messages, optionally exercise deterministic local/test analysis fixtures, import completed analysis as advisory conversation input, and link eligible registered attachments to confirmed tasks.

## Implemented Lifecycle

1. Mobile selects an image locally.
2. Mobile shows a local-only preview.
3. Mobile registers declared metadata with the backend.
4. Backend creates a server-generated `REGISTERED` attachment identity.
5. Assistant messages reference registered attachments through `attachmentIds`.
6. Local/test code may invoke deterministic fixture analysis explicitly.
7. Backend persists `VisionAnalysis` lifecycle records.
8. Completed analysis may be explicitly imported into the Conversation Engine.
9. Imported provider output becomes advisory `VISION_ANALYSIS` image observations.
10. Normal required-field validation and follow-up behavior still applies.
11. Preview generation happens only through the existing Conversation Engine.
12. Task creation still requires explicit confirmation.
13. Successful confirmation creates task attachment/provenance links.
14. Task attachment metadata is read through tenant-scoped task APIs.

## State and Terminology

- `LOCAL_SELECTED`: mobile-only image metadata and preview reference before backend registration.
- `REGISTERING`: mobile is actively sending declared metadata to the registration endpoint.
- `REGISTRATION_FAILED`: registration failed and requires manual retry.
- `LOCAL_METADATA_ONLY`: legacy message-local metadata from Sprint 6D. It does not create a backend-owned attachment row by itself and never creates durable task attachment links.
- `REGISTERED`: backend-owned durable metadata identity with a server-generated attachment ID. Current registered attachments have `storageReference = null`.
- `PENDING` analysis: analysis request metadata has been persisted but no terminal result exists.
- `COMPLETED` analysis: deterministic/local or future provider result completed and sanitized metadata is persisted.
- `FAILED` analysis: provider/service attempt failed without fabricating observations.
- `INELIGIBLE` analysis: attachment cannot be analyzed by the requested path.
- `USER_PROVIDED` image observation: user-written image note.
- `VISION_ANALYSIS` advisory observation: imported sanitized provider/deterministic result.
- `ASSISTANT_MESSAGE` provenance: task link came from a registered attachment referenced by the confirmed assistant flow.
- `VISION_ANALYSIS` provenance: task link came from an imported completed analysis that participated in the confirmed assistant flow.

## Current Boundaries

`REGISTERED` means only:

- backend-owned durable metadata identity
- server-generated attachment ID
- metadata can be referenced by assistant messages
- confirmed tasks can expose safe metadata/provenance links

`REGISTERED` does not mean:

- image bytes were uploaded
- image bytes were stored
- image is downloadable
- server thumbnail is available
- provider-accessible media exists
- image was inspected
- image was analyzed

Current implementation does not include binary upload, object storage, filesystem media storage, Azure Blob Storage, S3, pre-signed URLs, download APIs, server thumbnails, OCR, malware scanning, content inspection, real vision-provider calls, or OpenAI Vision calls.

No local URI, local reference, device/file URI, base64 payload, raw binary, fake media URL, storage URL, provider secret, or raw provider payload reaches OpenAI or any vision-provider prompt.

## Sprint 7A - Attachment Storage Contract Foundation

- Added `assistant_attachment` metadata registration through `POST /api/v1/assistant/conversations/{conversationId}/attachments`.
- The endpoint accepts declared metadata only: type, filename, MIME type, size, and optional image dimensions.
- Attachment IDs, hotel ownership, user ownership, `storageStatus`, and `storageReference` are backend-owned.
- Forbidden ownership, storage, URL, local reference, base64, and raw byte fields are rejected.
- Existing `LOCAL_METADATA_ONLY` message attachments remain backward-compatible.
- Registered attachment references in assistant messages are accepted only when the attachment belongs to the same authenticated hotel, user, and conversation.

## Sprint 7B - Image Analysis Port and Deterministic Provider Adapter

- Added provider-neutral `VisionAnalysisPort`.
- Added deterministic local/test provider fixtures: `leaking-sink`, `broken-window`, `dirty-room`, and `unknown`.
- Deterministic fixtures operate only on explicit internal fixture keys. They do not inspect real image bytes, read files, follow URLs, or use storage references.
- Real-provider analysis remains unavailable for metadata-only `REGISTERED` attachments because no durable provider-accessible storage exists.
- `VisionAnalysis` records persist lifecycle status, provider identity, sanitized metadata, confidence, ordered observations, and failure details.
- No analysis result alters conversation state, creates observations, creates previews, creates tasks, or invokes semantic normalization in 7B.

## Sprint 7C - Conversation Engine Integration

- Only persisted `COMPLETED` analyses can be imported.
- Import validates hotel, user, conversation, attachment, status, and attachment type server-side.
- Client import requests supply only path `conversationId` and `analysisId`; clients cannot submit observation text, confidence, provider metadata, ownership fields, storage fields, media URLs, local/device/file URIs, base64, or binary.
- Imported observations are advisory `VISION_ANALYSIS` records with source analysis ID, attachment ID, bounded confidence, sanitized provider ID, order, and `advisory = true`.
- Confidence thresholds:
  - LOW: `< 0.50`
  - MEDIUM: `>= 0.50 and < 0.80`
  - HIGH: `>= 0.80`
- Boundary semantics are `0.49 = LOW`, `0.50 = MEDIUM`, `0.79 = MEDIUM`, and `0.80 = HIGH`.
- LOW confidence cannot independently create a preview or task.
- MEDIUM and HIGH confidence can enter semantic interpretation, but required-field validation remains mandatory.
- HIGH confidence still requires normal preview and explicit confirmation.
- Import idempotency is enforced by unique `(conversation_id, analysis_id)`.
- Import does not directly create tasks and does not bypass the Conversation Engine.

## Sprint 7D - Task Attachment Linking After Confirmation

- Added `task_attachment_link` records after successful explicit assistant confirmation.
- Links are created only from registered attachments that participated in the confirmed semantic flow.
- Older unrelated conversation attachments are not linked.
- `LOCAL_METADATA_ONLY` attachments never create durable task links.
- Link provenance is `ASSISTANT_MESSAGE` or `VISION_ANALYSIS`.
- Vision provenance stores analysis/import IDs when available, but no raw provider payload, provider secret, storage reference, URL, binary, base64, or local reference.
- Confirmation covers task creation, attachment-link creation, idempotency persistence, and conversation `createdTaskId`/`TASK_CREATED` persistence in one transaction.
- Retrying the same confirmation idempotency key returns the existing task and does not duplicate links.
- `GET /api/v1/tasks/{taskId}/attachments` returns metadata/provenance only.

## Sprint 7E - Mobile Registration and Preview Flow

- Mobile uses `expo-image-picker` for camera/gallery selection where supported.
- Selection creates local preview state and declared metadata only.
- Local preview references are device/browser local only and are never durable backend media references.
- Registration sends declared metadata only and stores the server `attachmentId` after a `REGISTERED` response.
- UI wording uses “Local preview only”, “Registering metadata”, “Registered metadata”, “Registration failed · Retry”, and “Sending message”.
- UI must not say “Uploaded”, “Stored”, “Upload complete”, or show fake upload percentages.
- Registration is manual retry only. Reconnect and draft restoration do not replay writes.
- Drafts may persist text, transcript, user-provided image notes, local preview references where supported, declared metadata, registration state, server attachment ID, and ordering. Drafts do not persist binary or base64.
- Assistant messages reference registered attachments through `attachmentIds`.
- Registration alone does not send a message, invoke analysis, create a preview, create a task, or bypass confirmation.
- Task detail displays metadata/provenance only and does not show server thumbnails, download/open actions, storage references, provider payloads, or provider secrets.

## Privacy, Retention, Deletion, and Ownership

- Attachment metadata is scoped by hotel, user, and conversation.
- Vision analysis is scoped by hotel, user, conversation, and attachment.
- Vision analysis imports are scoped by authenticated hotel/user/conversation ownership.
- Task attachment reads are scoped by task hotel.
- Cross-hotel and cross-user access is rejected server-side with non-leaking behavior.
- No raw media retention exists because no raw media is uploaded or stored.
- Conversation reset affects conversation state and draft semantics; it is not a raw-media deletion mechanism.
- Attachment metadata, vision analysis, vision imports, and task links are persisted as audit metadata according to their tables.
- Task deletion cascades `task_attachment_link` rows.
- Linked attachment, conversation, analysis, and analysis-import deletion is restricted by foreign keys so audit provenance is not silently destroyed while task links exist.

Future production media storage requires separate design and approval for:

- user/operator consent
- explicit retention periods
- deletion workflow
- malware scanning
- content inspection
- encryption at rest
- encryption in transit
- signed/time-limited access
- authorization for media retrieval
- provider data-processing terms
- provider retention policy
- regional data residency
- backup/deletion semantics
- audit requirements

These are future prerequisites, not Sprint 7 functionality.

## Observability Expectations

Current release validation relies on existing application logs, automated tests, and smoke checks. A future observability pass should add project-consistent metrics or structured logs for:

- attachment registration success/failure
- registration validation failures
- vision-analysis status counts
- deterministic fixture invocation in local/test
- analysis import success/failure/duplicate counts
- LOW/MEDIUM/HIGH confidence distribution
- task attachment link success/failure
- task attachment read failures
- tenant-scope denials without leaking identifiers
- correlation across registration, analysis, import, confirmation, and link creation

Logs must not include raw media, base64, local URI, device/file URI, storage/provider secrets, raw provider payloads, authorization tokens, or sensitive observation text unless a future logging policy explicitly allows sanitized text.

## Testing and Release Validation

- Backend regression: `./gradlew :backend:test :unimock:test`
- Mobile dependency install: `cd mobile && npm ci`
- Mobile type check: `cd mobile && npx tsc --noEmit`
- Mobile tests: `cd mobile && npm test`
- Docker smoke: `scripts/smoke/api-smoke.sh` against `docker/docker-compose.smoke.yml`

Sprint 7 smoke covers public API lifecycle pieces only. Deterministic analysis remains validated by backend integration tests because it is not exposed as a production HTTP endpoint solely for smoke testing.

## Definition of Done

- The metadata-only attachment lifecycle is documented end to end.
- False upload/storage/provider-analysis claims are removed.
- `REGISTERED` semantics are explicit and unchanged.
- Public API contracts and unsafe fields are documented.
- Privacy, ownership, retention, deletion behavior, and future production media prerequisites are documented.
- CI runs all intended mobile tests.
- Backend, UniMock, TypeScript, mobile tests, and Docker smoke pass.
- Task creation still requires preview and explicit confirmation.
- Task attachment reads remain tenant-scoped and metadata/provenance-only.
- No real provider/storage functionality is enabled accidentally.

## Dependencies on Previous Sprints

- Sprint 4 Conversation Engine and state machine.
- Sprint 5 AI abstraction and deterministic assistant path.
- Sprint 6D attachment metadata contracts.
- Sprint 6E user-provided image observations and semantic input metadata.
- Sprint 6F draft/offline preservation.
