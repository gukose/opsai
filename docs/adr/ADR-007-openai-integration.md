# ADR-007: OpenAI Integration

## Context
Hotel OpAI needs AI assistance for multilingual intent detection, field extraction, and follow-up generation, but task creation must remain controlled and deterministic.

## Decision
OpenAI will be integrated behind an interpreter interface. The AI layer may propose structured output, but OpenAI never creates tasks directly. All AI output must be validated before use.

## Alternatives considered
- Call OpenAI directly from controllers
- Let OpenAI create tasks and mutate state
- Use OpenAI only as a free-form chat model

## Consequences
- AI stays safely behind a boundary
- Deterministic validation remains in control
- Low-confidence output can fall back to clarification

## Future impact
This enables future prompt and model upgrades without changing workflow or task ownership rules.

