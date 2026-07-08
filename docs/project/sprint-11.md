# Sprint 11 - Reporting AI + Service Recovery

## Goal
Implement Bad Review Risk, Service Recovery, and Manager AI Reporting.

## Business value
Helps managers identify guest-experience risks early and coordinate recovery before issues become negative reviews.

## Architecture impact
- Adds AI-assisted reporting on top of validated operational, PMS, and guest-channel data.
- Keeps recommendations advisory; workflow changes still go through Task Engine controls.

## Backend tasks
- Implement bad-review-risk scoring inputs, service-recovery recommendation APIs, and manager report generation endpoints.
- Add audit records for generated reports and recommended actions.
- Allow managers to convert approved recommendations into tasks.

## Mobile tasks
- Add manager AI report views, risk indicators, recommended recovery actions, and task creation from approved recommendations.
- Show confidence and source context clearly.

## AI tasks
- Use `AiInterpreter` or a reporting-specific abstraction behind the same validation principles.
- Generate structured manager summaries, risk reasons, and service-recovery suggestions.
- Include confidence score, source references, and fallback behavior.

## UniMock tasks
- Provide PMS guest, reservation, event, and guest-request context for reporting AI test scenarios.

## Database tasks
- Add Flyway migrations for risk scores, report runs, report artifacts, recommendation records, source references, and manager approvals.

## Infrastructure tasks
- Add cost controls and scheduled/report-triggered execution limits.
- Add secure storage strategy for report artifacts and audit logs.

## UI tasks
- Add manager-facing reporting AI screens that are concise, inspectable, and action-oriented.
- Distinguish AI recommendations from confirmed operational facts.

## Documentation tasks
- Document risk-score inputs, report schemas, confidence handling, recommendation approval flow, and limitations.

## Testing tasks
- Verify report generation with mocked AI outputs.
- Verify source references and confidence are stored.
- Verify recommendations cannot alter workflow without manager approval.
- Verify fallback when AI output is invalid or unavailable.

## Risks
- AI-generated risk labels can be misleading if source data is incomplete.
- Managers need clear distinction between facts, inference, and recommendation.

## Definition of Done
- Managers can view AI-assisted risk and service-recovery reports.
- Recommendations are structured, auditable, and manager-approved before task creation.

## Dependencies on previous sprints
- Depends on Sprint 9 dashboard/reporting foundation, Sprint 10 guest channels, Sprint 5 AI abstraction, and Sprint 3 Task Engine.
