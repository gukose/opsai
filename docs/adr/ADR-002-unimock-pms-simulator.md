# ADR-002: UniMock PMS Simulator

## Context
Hotel OpAI must not own PMS master data. Development and testing need a deterministic replacement for the PMS.

## Decision
UniMock will act as a full PMS simulator and will run as a separate module in the same repo. It owns PMS-like master data, exposes `/api/pms` APIs, simulates PMS events, loads JSON fixtures, and stores PMS update verification in the `pms_mock_verification_log` table inside a separate `unimock` PostgreSQL schema for development.

## Alternatives considered
- Integrate directly with a real PMS in development
- Use static mocks only
- Let Hotel OpAI own a partial copy of PMS data
- Split UniMock into a separate repository immediately

## Consequences
- Development can proceed without a live PMS
- Integration flows can be validated end to end
- Simulator behavior must stay aligned with the PMS contract
- Hotel OpAI can consume UniMock through the existing REST client boundary without direct controller coupling
- Simulation data stays isolated from Hotel OpAI operational data

## Future impact
UniMock provides a stable boundary for future real PMS integrations and for repeatable test environments using the Master Simulation Dataset. The module boundary and schema boundary also make it easier to replace the simulator later without rewriting Hotel OpAI.
