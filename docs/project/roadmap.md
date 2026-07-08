# Hotel OpAI Sprint Roadmap

This roadmap defines the agreed delivery sequence for Hotel OpAI.

## Current product decisions

- Hotel OpAI is a multi-tenant SaaS platform.
- PMS is the System of Record.
- Hotel OpAI is the System of Action.
- AI is the System of Intelligence.
- UniMock is a separate full PMS simulator.
- UniMock owns PMS-like master data.
- Hotel OpAI owns operational data only.
- `docs/ui-reference/` is the UI source of truth.
- The roadmap should remain stable once these sprints are underway.
- New ideas go to backlog first unless they are critical architectural corrections.

## Delivery principles

- Every sprint must deliver a working increment.
- Infrastructure work is part of the sprint that needs it.
- In-memory repositories are temporary and must not become permanent runtime paths.
- PMS master data must not be duplicated inside Hotel OpAI.
- OpenAI, voice, and vision must enter through stable abstractions and existing workflow validation.

## Sprint sequence

1. [Sprint 0](sprint-00.md) - Architecture & Planning
2. [Sprint 1](sprint-01.md) - Platform Foundation
3. [Sprint 2](sprint-02.md) - UniMock PMS Simulator
4. [Sprint 3](sprint-03.md) - Hotel Operations Engine
5. [Sprint 4](sprint-04.md) - Mobile & Backend Integration
6. [Sprint 5](sprint-05.md) - AI Assistant
7. Sprint 5.5 - Production Readiness
8. [Sprint 6](sprint-06.md) - Hotel Experience Expansion

## Roadmap notes

- Sprint 0 is done.
- Sprint 1 is done or nearly done and should be treated as the platform baseline.
- Sprint 2 introduces UniMock as a separate Spring Boot module with its own PostgreSQL schema.
- Sprint 3 establishes the Task Engine, Workflow Engine, state machine, assignment foundation, SLA, task history, task logs, notification foundation, and domain events.
- Sprint 4 moves mobile/backend integration earlier so the real app can exercise auth, tasks, and assistant flows before deeper AI work.
- Sprint 5 adds the AI Assistant on top of the existing conversation engine and task confirmation flow.
- Sprint 5.5 hardens the platform before broader feature expansion.
- Sprint 6 expands the hotel experience surface after the core platform is stable.
- Later roadmap items will be re-planned after Sprint 6 based on what has been validated in production-like use.
