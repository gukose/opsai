# ADR-001: Multi-Tenant SaaS

## Context
Hotel OpAI is a production SaaS platform used by many hotels. Operational data, conversations, tasks, assignments, and notifications must be isolated per hotel.

## Decision
Hotel OpAI will be built as a multi-tenant SaaS system. Every operational record must include `hotelId` and all application logic must resolve data in the context of a hotel.

For the Sprint 1 MVP, `User` and `Employee` each belong to exactly one `Hotel`. Future multi-hotel membership will be introduced later through a dedicated membership model such as `user_hotel_membership`.

## Alternatives considered
- Single-tenant deployment per hotel
- Shared global data model without tenant scoping
- Hybrid model with selective tenant scoping

## Consequences
- Stronger data isolation
- Tenant-aware APIs, repositories, and tests become mandatory
- Filtering and authorization must always include `hotelId`
- The Sprint 1 identity model stays simple and avoids premature membership complexity

## Future impact
This keeps the platform scalable for thousands of hotels and makes later tenant-level isolation or per-tenant deployment feasible without redesigning the domain model.
