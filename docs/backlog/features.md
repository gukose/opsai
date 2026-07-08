# Hotel OpAI Features

## Multi-Hotel SaaS
- **P0** Hotel-scoped APIs and repositories
- **P0** Tenant-aware task creation
- **P1** Tenant management screens

## Auth & User Management
- **P0** Login with JWT
- **P0** User roles: admin, manager, staff
- **P1** Logout and token refresh handling

## UniMock PMS Simulator
- **P0** Simulated rooms, assets, and guest state
- **P0** REST PMS read APIs
- **P1** REST PMS write simulation APIs
- **P1** PMS event replay from JSON fixtures

## Hotel Operations Engine
- **P0** Workflow and state-machine execution
- **P0** Task creation from confirmation
- **P0** Invalid transition rejection
- **P0** Assignment foundation
- **P1** SLA deadline and overdue marking
- **P1** Task history and logs
- **P1** Domain event publication

## Mobile & Backend Integration
- **P0** Auth-aware API client
- **P0** Login, refresh, logout
- **P0** Backend mode for assistant and tasks
- **P1** Current-user context rendering
- **P1** Real task list and home data

## AI Assistant
- **P0** Backend conversation start and send message flow
- **P0** Confirmation-driven task creation
- **P0** Permission-aware current-user context
- **P1** Reset conversation flow
- **P1** Multi-turn clarification flow

## OpenAI
- **P1** Structured JSON interpretation
- **P1** Low-confidence clarification fallback
- **P2** Multi-language intent parsing improvements

## Voice
- **P2** Send transcript as VOICE message type
- **P2** VoiceBubble rendering
- **P3** Audio metadata upload

## Vision
- **P2** Attach images to messages
- **P2** AttachmentBubble rendering
- **P3** Vision-based observations

## Production Readiness
- **P1** Performance and query tuning
- **P1** Caching, pagination, and rate limiting
- **P1** Metrics, tracing, and structured logging
- **P1** Security headers and secret handling
- **P1** CI/CD, Docker build, and Azure prep
- **P1** Flyway validation and smoke/E2E/load tests

## Notifications
- **P2** Task created notification
- **P2** SLA overdue alert
- **P3** Assignment change notification

## Dashboard
- **P2** Manager summary dashboard
- **P2** Workload and overdue widgets

## WhatsApp
- **P3** Guest request entry from WhatsApp
- **P3** QR code guest request landing flow

## Reporting
- **P2** Tasks by type, status, and hotel
- **P2** SLA performance reporting
- **P3** AI-generated recovery recommendations

## Production Infrastructure
- **P0** Environment-based configuration
- **P0** CORS for local development
- **P1** Azure deployment scripts
- **P1** Health checks and logging
