# Vision Pipeline API Contracts

Sprint 7 exposes metadata and provenance contracts only. It does not expose binary upload, media download, server thumbnails, OpenAI Vision, or a production image-analysis endpoint.

## Shared Rules

- All public endpoints require authentication unless explicitly noted.
- Hotel and user scope are derived from the authenticated session.
- Clients must not supply ownership fields as authority.
- Non-owned conversations, analyses, attachments, and tasks use safe non-leaking not-found behavior.
- No endpoint accepts or returns raw binary, base64, local URI, device/file URI, arbitrary media URL, provider secret, or raw provider payload.
- `REGISTERED` means backend-owned metadata identity only. Current `REGISTERED` attachments have `storageReference = null`.

## Register Assistant Attachment Metadata

`POST /api/v1/assistant/conversations/{conversationId}/attachments`

Registers declared metadata for an attachment owned by the authenticated conversation scope.

Accepted request fields:

- `type`: `IMAGE`, `PDF`, or `DOCUMENT`
- `originalFileName`
- `mimeType`
- `sizeBytes`
- `widthPx`, image only
- `heightPx`, image only

Forbidden request fields:

- client `id`
- `hotelId`, `userId`, `ownerId`, or other ownership fields
- `storageReference`
- `storageStatus`
- provider URL or arbitrary media URL
- `localReference`, `localUri`, `fileUri`, `deviceUri`
- raw binary, raw bytes, or base64 fields

Response fields:

- `attachmentId`
- `conversationId`
- `type`
- `originalFileName`
- `mimeType`
- `sizeBytes`
- `widthPx`
- `heightPx`
- `storageStatus = REGISTERED`
- `storageReference = null`
- `createdAt`

Registration does not upload bytes, create a task, trigger analysis, create a preview, or make media provider-accessible. Registration is a write; mobile retries are manual only.

## Send Assistant Message

`POST /api/v1/assistant/conversations/{conversationId}/messages`

The existing assistant message endpoint accepts text, client transcript metadata, legacy local attachment metadata, user-provided image notes, and registered attachment references.

Registered attachment references:

- use `attachmentIds`
- must belong to the same authenticated hotel, user, and conversation
- are resolved server-side before the Conversation Engine processes the message

Legacy metadata-only attachments:

- use `attachments`
- remain `LOCAL_METADATA_ONLY`
- do not create `assistant_attachment` rows by themselves
- do not create durable task attachment links

Forbidden message fields:

- client `hotelId` and `userId`
- raw audio or image bytes
- base64 media
- local/device/file URIs as durable media
- arbitrary image/audio URLs
- storage references supplied by the client

The endpoint returns the normal `AssistantConversationResponse`. Task creation still requires preview and explicit confirmation.

## Deterministic Vision Analysis Boundary

Sprint 7B added an internal provider-neutral `VisionAnalysisPort` and deterministic local/test provider. This is not a production HTTP API.

Deterministic fixture analysis:

- is available only in test/local-safe configuration
- uses explicit internal fixture keys
- does not inspect real images
- does not read local files
- does not follow URLs
- does not use `storageReference`
- does not call OpenAI or any external provider

Real-provider analysis remains unavailable for `REGISTERED` metadata-only attachments because no provider-accessible durable media storage exists.

## Import Completed Vision Analysis

`POST /api/v1/assistant/conversations/{conversationId}/vision-analyses/{analysisId}/import`

Imports an already persisted `COMPLETED` analysis into the normal Conversation Engine path.

Accepted client input:

- path `conversationId`
- path `analysisId`
- empty body

The client cannot supply:

- observation text
- confidence
- provider metadata
- `attachmentId`
- `hotelId`
- `userId`
- storage or media fields

Eligibility:

- analysis exists
- analysis status is `COMPLETED`
- analysis belongs to the authenticated hotel, user, and conversation
- analysis attachment belongs to the same authenticated scope
- attachment type is `IMAGE`
- analysis has at least one usable nonblank observation

Idempotency:

- unique `(conversation_id, analysis_id)` prevents duplicate imports
- duplicate completed import returns the existing conversation result or a deterministic no-op
- import reservation, conversation mutation, and completed import marker run transactionally

Imported observations are advisory `VISION_ANALYSIS` observations. They do not create tasks directly, bypass required-field validation, bypass preview, or bypass confirmation.

Confidence thresholds:

- LOW: `< 0.50`
- MEDIUM: `>= 0.50 and < 0.80`
- HIGH: `>= 0.80`

LOW confidence cannot independently create a preview or task. HIGH confidence still requires normal validation, preview, and explicit confirmation.

## Confirm Assistant Task

`POST /api/v1/assistant/conversations/{conversationId}/confirm`

Confirms an existing assistant preview using the existing confirmation idempotency contract.

Request fields:

- `idempotencyKey`

Confirmation behavior:

- requires authenticated ownership of the conversation
- requires the conversation to be waiting for confirmation
- creates a task through the existing task lifecycle path
- links only eligible `REGISTERED` attachments that participated in the confirmed semantic flow
- creates no links for unrelated historical conversation attachments
- creates no links for `LOCAL_METADATA_ONLY` attachments
- retries with the same idempotency key return the existing task and do not duplicate links

The confirmation transaction covers task creation, task attachment link creation, idempotency persistence, and conversation task-created state persistence.

## Read Task Attachments

`GET /api/v1/tasks/{taskId}/attachments`

Returns tenant-scoped task attachment metadata and provenance only.

Response fields:

- `attachmentId`
- `conversationId`
- `type`
- `originalFileName`
- `declaredMimeType`
- `declaredSizeBytes`
- `widthPx`
- `heightPx`
- `storageStatus`
- `sourceType`: `ASSISTANT_MESSAGE` or `VISION_ANALYSIS`
- `analysisId`
- `analysisImportId`
- `createdAt`

Intentionally absent fields:

- binary
- base64
- local URI
- local reference
- storage reference
- download URL
- server thumbnail URL
- arbitrary media URL
- raw provider payload
- provider secret

Cross-hotel task IDs use safe not-found behavior. A task with no durable attachment links returns an empty list.
