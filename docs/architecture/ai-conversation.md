# AI Conversation Engine

## Purpose

The AI Conversation Engine converts natural user input into hotel operations without exposing users to forms. It supports text, voice, metadata-only image context, user-provided image notes, imported advisory vision-analysis observations, and mixed inputs. It must collect enough information, confirm the generated operation, create the task, assign it, notify the correct team, and call UniMock only when PMS-owned state needs to change.

## Core Responsibilities

- Accept text, transcript, metadata-only attachment, and advisory vision-analysis conversation inputs.
- Detect intent and language.
- Extract operational fields such as room, area, issue type, priority, asset, department, description, attachments, and due time.
- Ask focused follow-up questions when required information is missing or ambiguous.
- Generate an inline task preview before creation.
- Create the confirmed task in Hotel OpAI.
- Trigger assignment and notification workflows.
- Call UniMock through REST for PMS-owned reads or state changes.
- Persist conversation state and audit events.

## Non-Responsibilities

- Owning PMS master data.
- Mutating rooms, assets, minibar, public areas, or guest data directly in Hotel OpAI tables.
- Letting the AI write directly to the database.
- Creating tasks without deterministic validation.
- Treating model output as trusted application state.

## Production Flow

1. Mobile sends a conversation message to the backend.
2. Backend stores the message and allowed attachment metadata. Sprint 7 does not upload or store image bytes.
3. Conversation application service loads the active conversation state.
4. Context builder fetches only required operational context:
   - current user profile
   - hotel configuration
   - department routing rules
   - relevant UniMock data such as rooms, public areas, assets, minibar items, or issue types
5. AI adapter calls OpenAI with a constrained schema.
6. Backend validates the model response against domain rules.
7. Conversation state machine chooses the next transition:
   - ask follow-up
   - ask confirmation
   - create task
   - reject unsupported request
8. Mobile renders assistant response, questions, and task preview inline.

## Model Output Contract

The model should return structured output, not free-form business decisions.

Required fields:

- `intent`
- `confidence`
- `language`
- `extractedFields`
- `missingFields`
- `ambiguities`
- `assistantMessage`
- `nextAction`
- `taskDraft` when enough data exists

The backend must validate:

- intent is supported
- room or area exists in UniMock when required
- assigned department exists in Hotel OpAI configuration
- priority is allowed
- task category maps to a known operational workflow
- PMS mutation is allowed for the task type

## Context Boundaries

Hotel OpAI context:

- users
- roles
- departments
- tasks
- assignments
- notifications
- approvals
- audit logs
- conversation state
- AI interaction logs

UniMock context:

- rooms
- room types
- occupancy
- assets
- issue types
- minibar catalog/state
- public areas
- guest requests
- events

The AI engine may read UniMock data through integration services. It must never assume PMS data from cached model memory.

## Safety Rules

- The AI proposes; the backend decides.
- All task creation goes through domain services.
- Every AI response used for task creation must be schema-validated.
- User confirmation is required before task creation unless the task type is explicitly configured as a flash task.
- PMS writes must be idempotent where possible.
- Store enough logs to reconstruct why a task was created.
- `REGISTERED` attachments are metadata identity only. They are not uploaded, stored, downloadable, provider-accessible, or analyzed by default.
- `VISION_ANALYSIS` observations are advisory. LOW confidence cannot independently create a preview or task, and HIGH confidence still requires required-field validation, preview, and explicit confirmation.
- No local URI, storage URL, base64, raw binary, provider secret, or raw provider payload may enter interpreter prompts.

## Backend Components

- `ConversationController`
- `ConversationApplicationService`
- `ConversationStateMachine`
- `ConversationRepository`
- `ConversationMessageRepository`
- `AiAssistantPort`
- `OpenAiAssistantAdapter`
- `ConversationContextBuilder`
- `TaskDraftValidator`
- `TaskCreationService`
- `UniMockPort`

## Observability

Track:

- intent classification
- missing field loops
- task preview generation
- confirmation rate
- task creation success/failure
- UniMock call latency/failure
- model latency/token usage
- unsupported intent frequency
- attachment registration success/failure
- vision analysis status and confidence bucket
- analysis import success/failure/duplicate
- task attachment link success/failure

Do not log raw sensitive guest data unless explicitly required and protected by retention policy.
Do not log raw media, base64, local/device/file URIs, authorization tokens, storage/provider secrets, raw provider payloads, or sensitive observation text unless an explicit sanitized logging policy exists.
