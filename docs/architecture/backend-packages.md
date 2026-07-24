# Backend Package Structure

This document is intentionally brief.

The canonical backend package strategy for Hotel OpAI is defined in
`.handbook/project-structure.md` and summarized in `.handbook/backend.md`.

Use feature-first packages only. The backend must not reintroduce layer-first
top-level packages such as `api`, `application`, `domain`, or `infrastructure`
as the primary organization model.

Required feature roots include:

- `assistant`
- `task`
- `workflow`
- `auth`
- `hotel`
- `employee`
- `integration`
- `pms`
- `reservation`
- `shared`
- `common`
- `config`

Keep the handbook and implementation aligned. If the code diverges, the code
must be moved back to the handbook layout rather than duplicating a second
package strategy in the docs.

The `reservation` feature root owns the canonical reservation aggregate,
provider-neutral PMS mapping boundary, durable snapshot repository, explicit
on-demand synchronization service, and reservation sync state. Provider DTOs
remain under `integration/*` and PMS-facing models remain under `pms`.
