# Hotel OpAI Epics

## Multi-Hotel SaaS
- **P0** Tenant-scoped platform foundation
- **P0** Hotel-aware authorization and data isolation
- **P1** Tenant administration and provisioning

## Auth & User Management
- **P0** JWT authentication
- **P0** User identity and role model
- **P1** Role-based access control
- **P2** Session and device management

## UniMock PMS Simulator
- **P0** Full PMS simulation boundary
- **P0** JSON simulation dataset loading
- **P1** PMS event simulation
- **P1** PMS update verification tracking

## Hotel Operations Engine
- **P0** Workflow engine and state machine
- **P0** Task lifecycle and confirmation persistence
- **P0** Assignment foundation
- **P1** SLA and overdue management
- **P1** Task history, logs, and audit trail
- **P1** Domain events for operational consumers

## Mobile & Backend Integration
- **P0** Auth-aware mobile API client
- **P0** Login/logout/refresh integration
- **P0** Backend mode for assistant and task APIs
- **P1** Current-user context in mobile
- **P1** Real task list and home data integration

## AI Assistant
- **P0** Multi-turn conversation engine
- **P0** Intent detection and missing field handling
- **P0** Task preview and confirmation flow
- **P1** Follow-up question generation
- **P1** Conversation reset and recovery

## OpenAI
- **P1** Structured AI interpretation
- **P1** Confidence-based fallback
- **P2** Prompt and schema tuning

## Voice
- **P2** Voice transcript ingestion
- **P2** Voice bubble UI
- **P3** Audio upload and storage

## Vision
- **P2** Image attachment ingestion
- **P2** Attachment bubble UI
- **P3** Image analysis and observations

## Production Readiness
- **P1** Performance review and optimization
- **P1** Caching, pagination, and query tuning
- **P1** Metrics, tracing, logs, and health checks
- **P1** Security headers and secret handling
- **P1** CI/CD, Docker build, and Azure deployment prep
- **P1** Flyway validation and smoke/E2E/load testing

## Notifications
- **P2** Task and workflow notifications
- **P2** SLA alerts and escalations
- **P3** Notification preferences

## Dashboard
- **P2** Manager overview dashboard
- **P2** Operational KPIs and summaries

## WhatsApp
- **P3** WhatsApp inbound guest flow
- **P3** QR-assisted guest request entry

## Reporting
- **P2** Operational reporting views
- **P2** Service recovery insights
- **P3** AI-assisted reporting summaries

## Production Infrastructure
- **P0** Environment configuration strategy
- **P0** CORS and local dev support
- **P1** Azure deployment baseline
- **P1** Observability, logging, and health checks
- **P1** Database migration strategy
