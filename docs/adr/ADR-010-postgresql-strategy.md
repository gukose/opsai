# ADR-010: PostgreSQL Strategy

## Context
Hotel OpAI needs durable persistence for operational data and production SaaS behavior, but it must not replicate PMS master data.

## Decision
PostgreSQL will be the production database for Hotel OpAI-owned operational data only. PMS-like master data remains outside Hotel OpAI and lives in UniMock or the future real PMS integration.

## Alternatives considered
- Store everything in PostgreSQL, including PMS master data
- Use only in-memory repositories
- Split data across multiple unrelated stores without ownership rules

## Consequences
- Operational data can be persisted reliably
- Ownership boundaries remain clean
- Database schemas stay aligned with system responsibilities

## Future impact
This allows gradual migration from in-memory infrastructure to production persistence without changing the domain ownership model.

