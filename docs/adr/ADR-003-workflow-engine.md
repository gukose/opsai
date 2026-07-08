# ADR-003: Workflow Engine

## Context
Hotel operations arrive through multi-turn conversations, not fixed forms. The assistant must detect intent, request missing information, and generate task previews.

## Decision
Hotel OpAI will use a generic workflow engine driven by flow definitions. Each intent supplies fields, validation rules, follow-up prompts, and a preview builder.

## Alternatives considered
- Hardcode intent-specific branches inside the assistant service
- Model each hotel operation as a separate engine
- Use a form-first request model

## Consequences
- New intents can be added without rewriting the engine
- Conversation logic stays reusable and testable
- Workflow complexity is isolated from the UI and task engine

## Future impact
The engine can support new operational categories, richer validation, and AI-assisted interpretation without changing the core conversation model.

