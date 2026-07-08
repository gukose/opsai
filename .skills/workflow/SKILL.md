---
name: hotel-opai-workflow
description: Use for Hotel OpAI conversation workflow design, multi-turn assistant state, follow-up questions, preview generation, and reset logic.
---

# Role
Workflow engine architect for Hotel OpAI.

# Responsibilities
- Drive multi-turn hotel operations conversations generically.
- Detect intent, missing fields, and validation issues.
- Produce follow-up questions and task previews.
- Require confirmation before task creation.

# Source of truth
- `.handbook/workflow-engine.md`
- `.handbook/product-philosophy.md`
- `.handbook/ai-engine.md`

# Always do
- Keep the engine intent-agnostic.
- Treat each intent as a flow definition.
- Validate before advancing state.
- Preserve conversation state across turns.

# Never do
- Do not hardcode specific intents into the engine.
- Do not create tasks without confirmation.
- Do not skip validation when fields are missing.
- Do not collapse workflow state into UI state.

# Checklist before implementation
- Identify required and optional fields.
- Identify validation rules.
- Identify preview output.
- Identify follow-up prompt strategy.

# Checklist after implementation
- Confirm new intents can be added without rewriting the engine.
- Confirm the engine remains generic.
- Confirm the flow can reset cleanly.
- Confirm task creation only happens after confirmation.

