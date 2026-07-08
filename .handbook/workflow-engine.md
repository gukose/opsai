# Workflow Engine

The workflow engine drives multi-turn hotel operations conversations.

## Responsibilities

- detect intent
- identify missing fields
- ask follow-up questions
- collect answers across turns
- generate task previews
- require confirmation before task creation
- reset or end conversations safely

## Design

- The engine must operate generically on conversation steps.
- Intents provide their own field definitions, validation rules, follow-up prompts, and preview builders.
- The engine must not hardcode a specific hotel operation type.

## Conversation Safety

- Never create a task without explicit confirmation.
- Never advance to task creation if required data is missing.
- Preserve conversation state until reset or completion.

