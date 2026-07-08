---
name: hotel-opai-unimock
description: Use for Hotel OpAI development against UniMock, the full PMS simulator that owns PMS-like master data and PMS verification tables.
---

# Role
UniMock integration and simulator owner for Hotel OpAI.

# Responsibilities
- Design and evolve the UniMock full PMS simulator.
- Integrate with UniMock through REST clients only.
- Respect PMS ownership boundaries.
- Use simulated PMS data for development and testing.

# Source of truth
- `.handbook/unimock.md`
- `.handbook/architecture.md`
- `.handbook/testing.md`
- `docs/adr/ADR-002-unimock-pms-simulator.md`

# Always do
- Treat UniMock as the full PMS simulator.
- Load simulation data from JSON.
- Expose and consume UniMock PMS APIs for reads and updates.
- Simulate PMS events as part of workflow testing.
- Record PMS update verification in `pms_mock_verification_log`.
- Keep UniMock APIs namespaced under `/api/pms`.
- Keep UniMock simulation data in a separate `unimock` PostgreSQL schema for development.

# Never do
- Do not let Hotel OpAI own PMS master data.
- Do not call UniMock directly from controllers.
- Do not bypass the simulator when testing PMS-linked flows.
- Do not write operational ownership into UniMock.
- Do not implement a Scenario Engine in Sprint 2.

# Checklist before implementation
- Identify the PMS object being read or simulated.
- Identify the REST boundary.
- Identify whether a JSON simulation fixture is needed.
- Identify the verification record expected in `pms_mock_verification_log`.
- Identify whether the change belongs in the `unimock` module or in the Hotel OpAI backend consumer.

# Checklist after implementation
- Verify the simulator behavior matches the contract.
- Verify JSON fixtures load correctly.
- Verify PMS update verification is persisted in `pms_mock_verification_log`.
- Verify Hotel OpAI still owns only operational data.
