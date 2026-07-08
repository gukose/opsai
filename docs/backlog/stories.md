# Hotel OpAI Stories

## Multi-Hotel SaaS
- **P0** As a platform, I can scope every request to a `hotelId`.
- **P0** As a tenant, I can only access my hotel's operational data.

## Auth & User Management
- **P0** As a user, I can log in with JWT.
- **P0** As an admin, I can manage hotel users and roles.

## UniMock PMS Simulator
- **P0** As a developer, I can start the app against UniMock instead of a real PMS.
- **P1** As a tester, I can replay a hotel world from JSON fixtures.

## Hotel Operations Engine
- **P0** As a staff member, I can create a task through a multi-turn assistant flow.
- **P0** As the system, I can create a persisted task only after confirmation.
- **P0** As a user, I cannot move a task to an invalid status.
- **P1** As a manager, I can assign tasks to teams or people.
- **P1** As the system, I can ask for missing fields one at a time.

## Mobile & Backend Integration
- **P0** As a staff member, I can log in from the mobile app.
- **P0** As a staff member, I can call protected backend APIs from the mobile app.
- **P1** As a staff member, I can switch between mock and backend modes.
- **P1** As a staff member, I can see my current user context on mobile.

## AI Assistant
- **P0** As a staff member, I can type a request and receive a conversational response.
- **P1** As the system, I can show follow-up questions when information is missing.

## OpenAI
- **P1** As the system, I can interpret multilingual requests through a structured AI adapter.
- **P1** As the system, I can fall back safely when AI confidence is low.

## Voice
- **P2** As a staff member, I can send a voice transcript as a request.
- **P2** As the UI, I can render a voice bubble with transcript and metadata.

## Vision
- **P2** As a staff member, I can attach an image to an assistant message.
- **P2** As the UI, I can show attachment metadata inline.

## Production Readiness
- **P1** As a developer, I can run performance checks before broader rollout.
- **P1** As an operator, I can inspect metrics, traces, logs, and health checks.
- **P1** As a team, I can run smoke, E2E, and load tests before release.

## Notifications
- **P2** As a staff member, I receive a notification when a task is created.
- **P2** As a manager, I receive an SLA alert before a task becomes overdue.

## Dashboard
- **P2** As a manager, I can view hotel-wide task summaries.

## WhatsApp
- **P3** As a guest, I can send a request through WhatsApp.
- **P3** As a guest, I can scan a QR code to start a request.

## Reporting
- **P2** As a manager, I can see operational performance by task type.
- **P3** As an operator, I can receive AI-assisted service recovery suggestions.

## Production Infrastructure
- **P0** As a developer, I can run the backend and mobile app locally with safe configuration.
- **P1** As an operator, I can deploy the platform to Azure.
