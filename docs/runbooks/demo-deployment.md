# DEMO deployment

This is a manual runbook. Repository preparation creates no cloud resources or builds.

## Architecture

- Expo/EAS produces native reviewer builds.
- Railway runs a public backend and private UniMock service.
- Supabase Free PostgreSQL stores backend `public` and UniMock `unimock` schemas.
- InternalDemo remains the PMS simulation; real OpenAI is backend-secret opt-in.

## Exact manual sequence

1. Create a Supabase project/database. Never configure schema reset.
2. In Supabase **Connect**, obtain Shared Pooler session-mode values (port `5432`). Prefer it when Railway cannot reach direct IPv6; require SSL.
3. Create a Railway backend service. Configure variables from [demo-environment-variables.md](demo-environment-variables.md). It uses `railway.json` and `backend/Dockerfile`.
4. Create a second Railway service named `unimock`. Set config path `/railway.unimock.json`, do not create a public domain, provide DB variables, and use `SPRING_PROFILES_ACTIVE=prod`. Leave `PORT` managed by Railway; UniMock binds the IPv6 wildcard (`::`) on the injected port and defaults to `8090` only outside Railway. Do not set `SERVER_ADDRESS` to an IPv4 address.
5. Set backend `OPS_AI_UNIMOCK_BASE_URL` to `http://${{unimock.RAILWAY_PRIVATE_DOMAIN}}:${{unimock.PORT}}`. If the service has a different Railway name, update both reference-variable names to match it. Deploy UniMock, verify its readiness health check, and only then deploy the backend manually. Flyway applies forward-only migrations, then DEMO bootstrap runs idempotently. Verify successful Flyway logs without credential values.
6. Verify `/actuator/health` and `/actuator/health/readiness` return `UP`; application APIs remain authenticated.
7. Add `OPENAI_API_KEY` only to the Railway backend and explicitly enable desired providers.
8. Set EAS preview `EXPO_PUBLIC_API_BASE_URL=https://<backend-domain>`.
9. From `mobile/`, run `eas login`, then `eas build --platform android --profile demo` or `eas build --platform ios --profile demo`.
10. Install the EAS internal link. Android is an APK. iOS requires Apple Developer signing and reviewer-device registration for ad-hoc distribution.
11. Follow [mvp-demo-walkthrough.md](mvp-demo-walkthrough.md).

## Provider reality

| Capability | Status | Notes |
|---|---|---|
| Voice/STT | REAL when configured | OpenAI transcription; otherwise unavailable, never faked |
| Assistant/intent | REAL when configured | OpenAI interpreter plus deterministic safety rules |
| Embeddings | REAL when configured | OpenAI embeddings adapter |
| Knowledge Assistant answer | REAL when configured | Bounded OpenAI answer provider |
| Vision | SIMULATED | Only deterministic fixture provider exists |
| Reservation recommendation | SIMULATED | InternalDemo active; OpenAI path remains governed smoke/pilot |
| PMS, folio, room-ready | SIMULATED | Private UniMock |
| WhatsApp/external guest delivery | SIMULATED | InternalDemo/local provider |

The demo profile binds Railway `PORT`, honors forwarded headers, hides stack traces/health details, disables Swagger, preserves production guards, caps Hikari at five connections and bounds AI sizes, retries, rates, daily Knowledge requests and concurrency.

References: [Railway config](https://docs.railway.com/config-as-code), [Railway healthchecks](https://docs.railway.com/deployments/healthchecks), [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [Expo internal distribution](https://docs.expo.dev/build/internal-distribution/).
