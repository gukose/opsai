# Sprint 9 - Manager Dashboard + Live KPIs

## Goal
Deliver Manager Dashboard, reporting foundation, and live KPIs.

## Business value
Gives managers real-time visibility into hotel operations, workload, SLA health, and recovery priorities.

## Architecture impact
- Adds read-optimized reporting queries and KPI aggregation without changing task ownership rules.
- Establishes dashboard APIs that later Reporting AI can consume.

## Backend tasks
- Implement manager dashboard APIs for workload, open tasks, SLA health, team performance, category breakdowns, and recent escalations.
- Add role-based access for manager-only views.
- Add reporting query services separate from command-side task services.

## Mobile tasks
- Add manager dashboard screens, KPI tiles, filters, drilldowns, and task navigation.
- Preserve staff task flows while exposing manager-only views by permission.

## AI tasks
- Prepare dashboard data contracts for later manager AI reporting.
- Keep AI-generated reports out of scope until Sprint 11.

## UniMock tasks
- Provide PMS context and seeded operational variety for dashboard demos.

## Database tasks
- Add Flyway migrations for reporting snapshots or materialized views if needed.
- Add indexes for KPI queries by hotel, status, assignee, department, category, and SLA due time.

## Infrastructure tasks
- Add performance budgets for dashboard endpoints.
- Add local load fixtures for realistic task and SLA volumes.

## UI tasks
- Implement dense, scannable manager UI for repeated operational use.
- Include empty, loading, error, and filtered states.

## Documentation tasks
- Document KPI definitions, manager permissions, reporting query model, and dashboard endpoint contracts.

## Testing tasks
- Verify KPI calculations.
- Verify manager access controls.
- Verify dashboard filters and drilldowns.
- Verify query performance against representative data.

## Risks
- KPI definitions can be disputed unless documented precisely.
- Heavy dashboard queries can affect operational task performance if not isolated.

## Definition of Done
- Managers can see live operational KPIs and drill into underlying tasks.
- Dashboard APIs are permissioned, tested, and performant enough for pilot usage.

## Dependencies on previous sprints
- Depends on Sprint 3 task/SLA data, Sprint 8 notifications/escalations, and Sprint 1 roles/permissions.
