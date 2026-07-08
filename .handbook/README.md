# Hotel OpAI Engineering Handbook

This handbook is the internal source of truth for how Hotel OpAI is designed, built, tested, and released.

It reflects the current production architecture:

- Hotel OpAI is a multi-tenant SaaS platform.
- PMS is the System of Record.
- Hotel OpAI is the System of Action.
- AI is the System of Intelligence.
- UniMock is the full PMS simulator used in development and testing.
- UniMock owns PMS-like master data.
- Hotel OpAI owns operational data only.
- Every operational record belongs to a `hotelId`.
- The Master Simulation Dataset represents the simulated hotel world used for repeatable tests.

Read the sections below as a working contract, not as product marketing.

## Sections

- [Product Philosophy](product-philosophy.md)
- [Architecture](architecture.md)
- [Project Structure](project-structure.md)
- [Backend](backend.md)
- [Mobile](mobile.md)
- [Design System](design-system.md)
- [Workflow Engine](workflow-engine.md)
- [Task Engine](task-engine.md)
- [AI Engine](ai-engine.md)
- [UniMock](unimock.md)
- [Testing](testing.md)
- [Coding Standards](coding-standards.md)
- [Release Process](release-process.md)
