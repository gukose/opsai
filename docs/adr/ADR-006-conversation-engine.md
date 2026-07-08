# ADR-006: Conversation Engine

## Context
The assistant must support multi-turn conversations with text, future voice transcripts, future images, missing field detection, preview generation, confirmation, and reset.

## Decision
Conversation handling will use a generic conversation engine that depends on intent flow definitions and an interpreter interface. It will not hardcode specific intents in the engine itself.

## Alternatives considered
- Intent-specific conversation services
- UI-driven conversation logic
- Direct OpenAI responses without an engine

## Consequences
- The engine remains reusable and maintainable
- Deterministic behavior can exist alongside future AI interpretation
- New intents can be introduced by adding flow definitions

## Future impact
The engine can absorb richer inputs, better validation, and advanced AI interpretation while keeping the same public API for mobile clients.

