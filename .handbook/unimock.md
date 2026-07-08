# UniMock

UniMock is the full PMS simulator used by Hotel OpAI during development and automated testing.

## Ownership

UniMock owns simulated PMS-like master data and simulation state:

- rooms
- room types
- floors
- occupancy
- room status
- assets
- issue types
- minibar
- public areas
- reservations
- guests
- guest requests
- events

## Rules

- Hotel OpAI must not own or mutate this master data.
- Hotel OpAI interacts with UniMock through REST clients only.
- Integration code belongs in infrastructure adapters.
- UniMock simulation data lives in a separate PostgreSQL schema named `unimock` for development.
- PMS update verification must be recorded in `pms_mock_verification_log`.

## API Namespace

All UniMock PMS APIs must be namespaced under `/api/pms`.

Read APIs:

- `GET /api/pms/rooms`
- `GET /api/pms/rooms/{roomNumber}`
- `GET /api/pms/rooms/{roomNumber}/status`
- `GET /api/pms/rooms/{roomNumber}/occupancy`
- `GET /api/pms/rooms/{roomNumber}/assets`
- `GET /api/pms/assets/{assetId}`
- `GET /api/pms/issue-types`
- `GET /api/pms/public-areas`
- `GET /api/pms/reservations`
- `GET /api/pms/reservations/{reservationId}`
- `GET /api/pms/guests/{guestId}`
- `GET /api/pms/events`

Update APIs:

- `POST /api/pms/rooms/{roomNumber}/status`
- `POST /api/pms/guest-requests`
- `POST /api/pms/guest-requests/{guestRequestId}/status`
- `POST /api/pms/minibar/updates`
- `POST /api/pms/maintenance/updates`
- `POST /api/pms/events`

Verification APIs:

- `GET /api/pms/mock-updates/verification-log`
- `GET /api/pms/mock-updates/events`

Admin seed-control APIs:

- `POST /api/admin/simulation/load`
- `POST /api/admin/simulation/reset`
- `GET /api/admin/simulation/current`

## Simulation Data

The Master Simulation Dataset is the canonical simulated hotel world used in tests.
Use it to validate realistic end-to-end workflows without depending on a real PMS.

Recommended future-proof seed layout:

```text
unimock/
  simulation/
    grand-hotel/
      simulation.json
      hotel.json
      master/
        rooms.json
        room-types.json
        floors.json
        public-areas.json
        assets.json
        issue-types.json
      operations/
        reservations.json
        guests.json
        occupancy.json
        room-status.json
        minibar.json
        guest-requests.json
        events.json
      scenarios/
        checkout-morning.json
        vip-arrival.json
        busy-day.json
        maintenance-heavy.json
```

Scenario files are reserved for future use and must not be executed in Sprint 2.
