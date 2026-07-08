# Sprint 5 - AI Assistant

## Goal
Build the AI Assistant on top of the deterministic conversation engine, then add OpenAI behind the `AiInterpreter` abstraction.

## Business value
Lets staff create operational work through natural conversation while preserving controlled validation and confirmation.

## Architecture impact
- Keeps the assistant flow generic and reusable across intents.
- Introduces `AiInterpreter` as the stable AI boundary.
- Allows deterministic and OpenAI-backed interpretation to coexist.
- Enforces structured output, validation, missing-field detection, follow-up questions, task previews, and confirmation before task creation.

## Backend tasks
- Refine the deterministic conversation engine.
- Add OpenAI integration behind `AiInterpreter`.
- Validate interpreter output before it affects workflow state.
- Keep task creation behind confirmation and the Task Engine.

## Mobile tasks
- Connect the assistant UI to the backend conversation flow.
- Render follow-up questions, previews, confirmation actions, and reset behavior.

## AI tasks
- Implement structured JSON interpretation.
- Add intent detection, missing-field extraction, and confidence handling.
- Keep multilingual support and fallback logic deterministic and testable.

## UniMock tasks
- Provide PMS lookup context to support assistant conversations where needed.

## Database tasks
- Persist conversation state, messages, previews, and confirmation records if not already present.

## Infrastructure tasks
- Add configuration for interpreter selection, timeouts, fallbacks, and secrets.

## UI tasks
- Keep the assistant experience compact and conversation-first.
- Avoid modal-heavy flows.

## Documentation tasks
- Document conversation state, interpreter contracts, confidence rules, fallback behavior, and confirmation requirements.

## Testing tasks
- Verify deterministic paths.
- Verify OpenAI adapter contract and fallback handling.
- Verify no AI path can create a task without confirmation.

## Risks
- Over-permissive AI output handling can weaken workflow integrity.
- If the deterministic engine drifts from the generic contract, later intent expansion becomes costly.

## Definition of Done
- Assistant conversations work end to end with deterministic interpretation.
- OpenAI can be enabled behind `AiInterpreter`.
- Task preview and confirmation remain mandatory.

## Dependencies
- Depends on Sprint 3 workflow/task foundation and Sprint 4 mobile/backend integration.
- Leads into Sprint 5.5 hardening after the assistant path is working.
