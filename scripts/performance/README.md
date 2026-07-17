# Sprint 8F Performance Scripts

These scripts are for disposable local PostgreSQL databases only.

They create deterministic `sprint-8f-*` fixture data, run `ANALYZE`, and print
representative `EXPLAIN (ANALYZE, BUFFERS)` plans.

Do not run these scripts against production.

Typical local use with the smoke Postgres container:

```bash
docker compose -f docker/docker-compose.smoke.yml up -d postgres
env SPRING_PROFILES_ACTIVE=local OPS_AI_DB_URL=jdbc:postgresql://localhost:5432/hotelopai OPS_AI_DB_USERNAME=hotelopai OPS_AI_DB_PASSWORD=hotelopai OPS_AI_AUTH_JWT_SECRET=hotel-opai-measure-secret-hotel-opai-measure-secret OPS_AI_AUTH_SEED_ENABLED=false ASSISTANT_AI_PROVIDER=deterministic ASSISTANT_AI_FALLBACK_ENABLED=false ./gradlew :backend:bootRun
docker compose -f docker/docker-compose.smoke.yml exec -T postgres psql -U hotelopai -d hotelopai -f /repo/scripts/performance/explain-sprint-8f.sql
```

The last command assumes the repository is mounted in the container. If it is
not, pipe the script into `psql` from the host:

```bash
docker compose -f docker/docker-compose.smoke.yml exec -T postgres psql -U hotelopai -d hotelopai < scripts/performance/explain-sprint-8f.sql
```
