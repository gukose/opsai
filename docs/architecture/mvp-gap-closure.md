# MVP Gap Closure architecture

Hotel OpAI keeps PMS as the System of Record and Task as the System of Action. New operational state is separated into bounded, hotel-scoped domains rather than expanding the public Task contract.

## Boundaries

- `housekeeping`: cleaning lifecycle, active/paused duration segments, checklist inspection and rework.
- `workforce`: operational employee availability, deterministic assignment, shift handover.
- `inventory`, `minibar`, `damage`, `finance`: durable stock ledger and human-reviewed financial proposals.
- `guest`, `recovery`: opaque QR sessions, provider-neutral message simulation, surveys/risk/recovery foundations.
- `reporting`, `billing`, `gamification`: predefined hotel-scoped queries and immutable ledgers.
- `offline`: idempotent client mutation submission; mobile owns a tenant/user-scoped durable queue.

Migrations V37–V41 are forward-only. New staff/admin APIs are internal, so the generated public SDK remains stable.

## Provider truthfulness

InternalDemo supports deterministic room-ready and folio outcomes. Apaleo advertises neither capability because this implementation does not contain a verified safe provider operation. Real Meta WhatsApp and external STT remain disabled and are not represented as working integrations.

## Safety invariants

Every query includes hotel scope. Foreign-tenant resources resolve as absent. Financial posting requires a persisted review transition. AI/fixture output is advisory and cannot approve a charge. Idempotency keys protect PMS writes, charge proposals, guest messages, billing events, minibar inspections, interruptions, and offline submissions.
