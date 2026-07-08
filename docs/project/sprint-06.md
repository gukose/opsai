# Sprint 6 - Hotel Experience Expansion

## Goal
Expand the hotel experience surface with notifications, dashboard, reporting, search, filters, offline support, attachments, voice, and image understanding.

## Business value
Broadens Hotel OpAI from core task handling into a richer operational platform for hotel teams.

## Architecture impact
- Adds higher-level hotel experience features on top of the already stable platform, workflow, mobile, and AI foundations.
- Reuses the Assistant, Task Engine, and authorization model instead of introducing parallel flows.
- Introduces richer input modalities and operational views without changing the core ownership boundaries.

## Backend tasks
- Add notification and dashboard APIs.
- Add search and filter support for operational data.
- Add attachment, voice, and image-related backend contracts where needed.

## Mobile tasks
- Add notification surfaces, dashboard views, offline-friendly behavior where needed, and richer attachment/voice/image UI states.

## AI tasks
- Extend assistant interpretation for attachments, voice, and image-derived observations through existing abstractions.

## UniMock tasks
- Extend PMS-backed data exposure only where required by the new hotel experience surfaces.

## Database tasks
- Add support tables or indexes needed for notification, search, and richer operational views.

## Infrastructure tasks
- Add the operational support needed for search, logging, offline-safe sync, and richer media handling.

## UI tasks
- Keep UI aligned with `docs/ui-reference/` as new surfaces are added.
- Preserve the compact, work-focused product style.

## Documentation tasks
- Document new experience surfaces, media handling boundaries, search/filter behavior, and offline expectations.

## Testing tasks
- Verify notification delivery paths, search behavior, filters, and new media input contracts.
- Verify voice and image inputs still route through the same workflow safeguards.

## Risks
- Feature expansion can pull attention away from the platform core if it starts before the assistant and workflow foundations are stable.
- Offline and media features tend to add complexity quickly if their storage and sync rules are vague.

## Definition of Done
- Core experience expansion features are implemented without breaking the platform foundations.
- New input and view types still respect auth, workflow, and confirmation rules.

## Dependencies
- Depends on Sprints 1 through 5.
- Should follow the production-readiness hardening phase if risk or scale demand it.
