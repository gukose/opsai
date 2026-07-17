# Local Development Stack

Use these scripts for daily local development. They are separate from Docker smoke validation and do not rebuild images, clear caches, or reinstall dependencies during a normal restart.

## First-Time Setup

Start the local Postgres container once:

```bash
docker compose -f docker/docker-compose.local.yml up -d postgres
```

Install mobile dependencies only when `mobile/node_modules` is missing or incomplete:

```bash
cd mobile
PATH=/opt/homebrew/bin:$PATH npm ci
```

Mobile web requires Node 22 from `/opt/homebrew/bin`. The restart script prepends `/opt/homebrew/bin` for mobile startup.

## Normal Restart

```bash
./scripts/dev/restart.sh
```

This restarts UniMock, backend, and mobile web. It leaves Postgres running if healthy.

## Restart One Service

```bash
./scripts/dev/restart.sh backend
./scripts/dev/restart.sh unimock
./scripts/dev/restart.sh mobile
```

## Mobile Cache-Clear Restart

Use this only when Metro cache is suspected:

```bash
./scripts/dev/restart.sh mobile --clear
```

Normal restarts do not use `--clear`.

## Status

```bash
./scripts/dev/status.sh
```

The status command reports Postgres, UniMock, backend, and mobile state, including PID ownership, ports, health checks, and log locations. Unknown processes occupying expected ports are reported but not killed.

## Logs

```bash
./scripts/dev/logs.sh
./scripts/dev/logs.sh backend
./scripts/dev/logs.sh unimock
./scripts/dev/logs.sh mobile
./scripts/dev/logs.sh mobile --follow
```

Runtime logs live under `scripts/.run/`.

## Stop

```bash
./scripts/dev/stop.sh
```

This stops only project-owned UniMock, backend, and mobile PIDs. Postgres remains running by default.

To also stop Postgres without deleting volumes:

```bash
./scripts/dev/stop.sh --with-postgres
```

## Restart With Postgres

```bash
./scripts/dev/restart.sh --with-postgres
```

This restarts the `hotel-opai-postgres` container without deleting volumes, then restarts UniMock, backend, and mobile.

## When npm ci Is Needed

The restart workflow does not run `npm install` or `npm ci`. If Metro reports a SHA-1 error such as failing to hash `node_modules/expo/src/launch/registerRootComponent.web.tsx`, `mobile/node_modules` may be incomplete. Repair it manually:

```bash
cd mobile
PATH=/opt/homebrew/bin:$PATH npm ci
```

Do not repeatedly delete `node_modules` as part of normal restarts.

## Mobile Sandbox Limitation

Expo web must bind `localhost:8081`. In managed sandboxed execution environments, a Node port bind can fail with `EPERM`, which Expo may display as a false "port is being used" prompt. Run the restart command from a normal local terminal when this happens.

## Daily Restart vs Docker Smoke

Daily local development uses:

```bash
./scripts/dev/restart.sh
```

Docker smoke validation uses `docker/docker-compose.smoke.yml` and `scripts/smoke/api-smoke.sh`. Smoke validation is for release checks, not daily local restarts.
