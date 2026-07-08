# Sprint 8 - Notification Engine + SLA Alerts

## Goal
Implement Notification Engine, push notifications, SLA alerts, and escalation.

## Business value
Keeps staff and managers aware of assigned work, approaching SLA breaches, breached SLAs, and escalations.

## Architecture impact
- Adds notification delivery as a dedicated engine fed by workflow, assignment, and SLA events.
- Establishes escalation policies without hard-coding them into task lifecycle logic.

## Backend tasks
- Implement Notification Engine, notification preferences, delivery records, templates, and escalation rules.
- Emit notifications for assignment, status changes, comments/logs if supported, SLA warning, SLA breach, and escalation.
- Add idempotency for notification event handling.

## Mobile tasks
- Register device tokens, handle push permissions, deep link to task detail, and display notification inbox/history.
- Respect quiet hours or preference settings if included.

## AI tasks
- Support AI-generated notification summaries only through validated templates or disabled placeholders.
- Do not let AI decide escalation policies.

## UniMock tasks
- Provide PMS-backed task context for notification test scenarios.

## Database tasks
- Add Flyway migrations for notification preferences, device tokens, notification events, delivery attempts, templates, and escalation policies.
- Add indexes for due alerts and delivery retries.

## Infrastructure tasks
- Configure push notification provider, credentials, retry policy, dead-letter handling, and local test mode.
- Add scheduled processing for SLA alert evaluation.

## UI tasks
- Add notification permission prompts, inbox/list, unread indicators, and task deep-link behavior.

## Documentation tasks
- Document notification event types, escalation policy model, push setup, retry behavior, and SLA alert thresholds.

## Testing tasks
- Verify push registration and delivery record creation.
- Verify SLA warning, breach, and escalation events.
- Verify idempotent notification processing and retry behavior.

## Risks
- Noisy alerts can reduce adoption.
- Escalation logic must stay configurable for different hotel operations models.

## Definition of Done
- Notifications are generated from task/SLA events and delivered or recorded with retry status.
- Mobile users can receive and navigate from push notifications.
- Escalation rules are configurable and tested.

## Dependencies on previous sprints
- Depends on Sprint 3 task/SLA foundations and Sprint 1 user/role foundations.
