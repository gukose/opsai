# Release Process

## Requirements Before Release

- backend tests pass
- mobile type checks pass
- critical flows are manually verified
- configuration is environment-safe
- no secrets are committed

## Production Readiness Checks

- tenant scoping is correct
- task lifecycle transitions are valid
- assistant confirmation creates real tasks
- PMS ownership boundaries are respected
- external integrations are behind adapters

## Change Discipline

- keep releases small and reviewable
- prefer incremental delivery over broad rewrites
- document any breaking API or workflow change

