# Sprint 10 - QR Concierge + WhatsApp Integration

## Goal
Implement QR Concierge and WhatsApp Integration for guest-initiated service requests.

## Business value
Lets guests create service requests through familiar channels while routing hotel work into the same operational task flow.

## Architecture impact
- Adds guest-channel adapters that produce conversation or task previews through controlled backend workflows.
- Keeps guest identity, consent, and PMS reservation context separated from employee auth.

## Backend tasks
- Implement QR entry flow, guest session model, WhatsApp webhook handling, message normalization, and request intake.
- Route validated guest requests into Conversation Engine and Task Engine paths.
- Add consent, rate limiting, abuse prevention, and audit logging.

## Mobile tasks
- Add staff visibility for guest-originated requests and WhatsApp/QR source indicators.
- Add manager controls for QR/WhatsApp channel status if needed.

## AI tasks
- Support guest-language interpretation through `AiInterpreter`.
- Keep AI suggestions behind validation and task preview rules.

## UniMock tasks
- Use UniMock reservations, guests, rooms, and guest requests to validate guest context.
- Log PMS updates through UniMock where guest request status changes are pushed.

## Database tasks
- Add Flyway migrations for QR codes, guest sessions, guest messages, WhatsApp channel records, consent records, and guest request mappings.

## Infrastructure tasks
- Add WhatsApp provider configuration, webhook verification, secrets, retry handling, and local webhook test tooling.
- Add rate limits and audit logging for public guest entry points.

## UI tasks
- Add source badges and guest request details to operational task views.
- Keep guest-facing QR pages minimal, mobile-first, and branded to the hotel context.

## Documentation tasks
- Document QR flow, WhatsApp webhook contract, guest privacy, consent model, rate limits, and PMS guest-context lookup.

## Testing tasks
- Verify QR session creation and request submission.
- Verify WhatsApp webhook verification and message handling.
- Verify guest requests create confirmed operational work through approved paths.
- Verify abuse and rate-limit behavior.

## Risks
- Public entry points increase security and abuse risk.
- WhatsApp provider constraints can affect message timing and templates.

## Definition of Done
- Guests can submit requests through QR and WhatsApp.
- Staff can see and act on guest-originated tasks.
- Public channels are secured, audited, and rate-limited.

## Dependencies on previous sprints
- Depends on Sprint 4 Conversation Engine, Sprint 5 multilingual AI support, Sprint 3 Task Engine, and Sprint 2 UniMock guest/reservation context.
