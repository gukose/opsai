# AI Engine

The AI engine interprets user intent and structured conversation data.

## Responsibilities

- accept multilingual text, voice transcript, and image-derived inputs in the future
- detect intent
- extract fields
- detect missing information
- propose follow-up questions
- generate task previews
- provide confidence-aware fallback behavior

## Rules

- AI must not create tasks directly.
- Structured output must be validated before use.
- Low confidence responses should fall back to deterministic clarification.
- Sensitive data should not be logged in raw form.

## Execution Model

- Use an interpreter interface.
- Keep a deterministic interpreter available for development and tests.
- Keep the OpenAI path behind an adapter.

