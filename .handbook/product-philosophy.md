# Product Philosophy

Hotel OpAI is an operations platform for real hotels, not a demo or prototype.

## Core Principles

- Reduce manual work for hotel teams.
- Turn hotel operations into structured, auditable workflows.
- Let staff interact with the system through natural language, voice, and images.
- Keep the UI compact, fast, and focused on work.
- Avoid introducing ownership conflicts with PMS systems.

## System Roles

- PMS is the System of Record for guest, room, occupancy, and master operational domain data.
- Hotel OpAI is the System of Action for tasks, assignments, approvals, notifications, logs, and assistant-driven workflows.
- AI is the System of Intelligence for interpreting requests, extracting intent, and guiding multi-turn conversations.

## Multi-Tenancy

Every business object that Hotel OpAI owns must be scoped to `hotelId`.
No operational record is global unless it is a shared reference or platform-level configuration.

## Ownership Rule

Hotel OpAI must not become a second PMS.
If a record is master data, the system should read it from UniMock in development or from the real PMS integration later.

