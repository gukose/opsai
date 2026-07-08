# ADR-014: Future Scalability

## Context
Hotel OpAI is expected to grow into a platform used by thousands of hotels worldwide.

## Decision
The architecture will favor modular boundaries, tenant scoping, reusable engines, explicit ports, and adapter-based integrations so that scale improvements can be made incrementally.

## Alternatives considered
- Optimize for a small internal deployment only
- Use a monolith with no boundaries
- Over-engineer premature microservices

## Consequences
- Current implementation remains maintainable
- Later scaling options stay open
- The system can evolve without rewriting core business concepts

## Future impact
This enables growth in tenants, workflow complexity, assistant sophistication, and integration breadth while keeping the core platform coherent.

