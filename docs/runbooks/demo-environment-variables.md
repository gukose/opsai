# DEMO environment variables

Store values in Railway or EAS; never in Git or Expo public configuration.

## Backend runtime

- `SPRING_PROFILES_ACTIVE`: `demo`.
- `PORT`: injected by Railway; fallback `8080`.
- `OPS_AI_AUTH_JWT_SECRET`: high-entropy signing secret.
- `OPS_AI_DEMO_BOOTSTRAP_ENABLED`: enables idempotent bootstrap.
- `OPS_AI_UNIMOCK_BASE_URL`: use Railway reference variables, normally `http://${{unimock.RAILWAY_PRIVATE_DOMAIN}}:${{unimock.PORT}}` when the service is named `unimock`.
- `OPS_AI_DB_MAXIMUM_POOL_SIZE`, `OPS_AI_DB_MINIMUM_IDLE`: optional Hikari limits.

## Supabase PostgreSQL

- `SUPABASE_DB_HOST`
- `SUPABASE_DB_PORT` (session pooler normally `5432`)
- `SUPABASE_DB_NAME`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`
- `OPS_AI_DB_URL`, `OPS_AI_DB_USERNAME`, `OPS_AI_DB_PASSWORD`: optional complete overrides.

Use the free Supavisor **session pooler** for a persistent Railway JVM when direct IPv6 is unavailable. It supports prepared statements and Flyway. Require SSL. Transaction mode (`6543`) targets transient/serverless clients.

## UniMock runtime

- `SPRING_PROFILES_ACTIVE`: `prod`.
- `PORT`: leave this managed by Railway. UniMock listens on the injected value, and the backend private URL references the same `${{unimock.PORT}}`. Outside Railway, UniMock defaults to `8090`.
- `OPS_AI_UNIMOCK_DB_URL`: JDBC URL with `sslmode=require`.
- `OPS_AI_UNIMOCK_DB_USERNAME`
- `OPS_AI_UNIMOCK_DB_PASSWORD`
- `OPS_AI_UNIMOCK_DB_MAXIMUM_POOL_SIZE`: optional; use a small value for the shared demo database.

Keep UniMock private: do not generate a public Railway domain. Its application also honors a Railway-injected `PORT`; `SERVER_PORT` remains a non-Railway compatibility fallback.

## OpenAI — backend only

- `OPENAI_API_KEY`
- `ASSISTANT_AI_PROVIDER`, `OPENAI_MODEL`
- `OPS_AI_VOICE_PROVIDER`, `OPS_AI_OPENAI_VOICE_ENABLED`, `OPENAI_STT_MODEL`
- `OPS_AI_OPENAI_KNOWLEDGE_ENABLED`, `OPENAI_KNOWLEDGE_MODEL`
- `OPS_AI_OPENAI_EMBEDDINGS_ENABLED`, `OPENAI_EMBEDDING_MODEL`, `OPENAI_EMBEDDING_DIMENSIONS`

## DEMO passwords

- `DEMO_GM_PASSWORD`
- `DEMO_HOUSEKEEPING_SUPERVISOR_PASSWORD`
- `DEMO_HOUSEKEEPER_PASSWORD`
- `DEMO_TECHNICIAN_PASSWORD`
- `DEMO_RECEPTION_PASSWORD`
- `DEMO_GUEST_RELATIONS_PASSWORD`
- `DEMO_ADMIN_PASSWORD`

Each must contain at least 12 characters. Existing accounts and reviewer data are not reset.

## Mobile EAS

- `EXPO_PUBLIC_APP_ENV`: `demo` in the checked-in profile.
- `EXPO_PUBLIC_API_BASE_URL`: Railway backend public HTTPS origin.

No provider/database/JWT secret belongs in an `EXPO_PUBLIC_*` variable.
