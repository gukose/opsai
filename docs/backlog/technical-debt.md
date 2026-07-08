# Hotel OpAI Technical Debt

## Multi-Hotel SaaS
- **P1** Replace any accidental non-tenant-scoped state.
- **P2** Introduce stronger tenant validation in application boundaries.

## Auth & User Management
- **P1** Add refresh token support if sessions become long-lived.
- **P2** Centralize auth error handling across backend and mobile.

## UniMock PMS Simulator
- **P1** Consolidate simulation fixtures into a single dataset strategy.
- **P2** Add stronger contract checks between UniMock and Hotel OpAI.

## Workflow Engine
- **P1** Remove any intent-specific shortcuts that leak into the generic engine.
- **P2** Improve flow definition reuse across intents.

## Task Engine
- **P1** Add persistence-backed idempotency if in-memory confirmation tracking becomes limiting.
- **P2** Introduce richer audit trail projection if reporting needs it.

## Assignment Engine
- **P2** Reduce duplicated assignment metadata mapping if multiple clients emerge.

## Mobile Operations
- **P1** Remove any remaining mock-only assumptions from backend mode paths.
- **P2** Consolidate assistant state mapping helpers where duplication appears.

## AI Assistant
- **P1** Tighten structured output validation and error reporting.
- **P2** Improve conversation recovery after partial failures.

## OpenAI
- **P1** Tune prompt size and response schema for cost efficiency.
- **P2** Add request/response observability without exposing sensitive data.

## Voice
- **P2** Add audio upload plumbing when product scope requires it.

## Vision
- **P2** Add file storage and vision-specific pipeline when needed.

## Notifications
- **P1** Move from simple notifications to channel-aware delivery if needed.

## Dashboard
- **P2** Introduce read models for manager views if API joins become expensive.

## WhatsApp
- **P3** Build an inbound channel adapter only when the feature is approved.

## Reporting
- **P2** Separate reporting projections from transactional tables if load increases.

## Production Infrastructure
- **P1** Replace in-memory repositories with PostgreSQL-backed persistence where required.
- **P1** Add deployment automation once the release process stabilizes.
- **P2** Formalize environment secrets management.

