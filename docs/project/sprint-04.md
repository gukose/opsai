# Sprint 4 - Mobile & Backend Integration

## Goal
Connect the mobile app to the real backend for auth, current-user context, assistant entry points, and task-aware data surfaces.

## Business value
Lets the team test the real product from the real app earlier, which shortens the feedback loop before deeper AI work.

## Architecture impact
- Moves mobile/backend integration earlier than the original plan.
- Makes the mobile client consume authenticated APIs through a reusable API client abstraction.
- Ensures current user, hotel context, and authorization behavior are visible in the app.
- Reduces the amount of hand-testing needed before assistant and task features are expanded.

## Backend tasks
- Keep auth and task APIs stable for the mobile client.
- Expose clean response models for current-user and task data.

## Mobile tasks
- Implement login, logout, refresh handling, and auth guards.
- Add Authorization header support in the API client.
- Connect the assistant shell and task surfaces to backend mode.
- Use the current-user context in the app.
- Fall back to mock mode only when backend mode is not enabled.

## AI tasks
- Keep assistant data flow compatible with future AI and task APIs.
- Do not introduce OpenAI yet.

## UniMock tasks
- Keep backend mobile flows compatible with UniMock-backed data.

## Database tasks
- No new persistence structures required for this sprint.

## Infrastructure tasks
- Add environment/config switches for backend mode vs mock mode.
- Keep local development support simple and deterministic.

## UI tasks
- Preserve the existing UI structure.
- Keep implementation aligned with `docs/ui-reference/`.

## Documentation tasks
- Document mobile auth flow, backend mode switching, API client behavior, and current-user handling.

## Testing tasks
- Verify login, logout, refresh, and protected API access from the app.
- Verify backend mode and mock mode can both be exercised.
- Verify current-user context is available in mobile state.

## Risks
- If mobile/backend integration is delayed too long, later AI work will stay trapped in Postman instead of the product.
- Token handling bugs become visible quickly once the real app is connected.

## Definition of Done
- Mobile can authenticate against the backend and call protected APIs.
- Backend mode is easy to enable.
- Mock mode remains available.

## Dependencies
- Depends on Sprint 1 auth and API foundations.
- Benefits from Sprint 3 task APIs where available.
- Makes Sprint 5 AI work faster and safer.
