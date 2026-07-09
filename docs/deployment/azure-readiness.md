# Azure Readiness

This is a deployment checklist only. Sprint 5.5E does not add Azure production deployment automation.

## Candidate Services

- Azure Container Apps or App Service for Containers for backend and UniMock.
- Azure Database for PostgreSQL for relational persistence.
- Azure Container Registry for built images.
- Azure Key Vault or managed container app secrets for sensitive values.

## Required Configuration

Backend:

- `SPRING_PROFILES_ACTIVE=prod`
- `OPS_AI_DB_URL`
- `OPS_AI_DB_USERNAME`
- `OPS_AI_DB_PASSWORD`
- `OPS_AI_UNIMOCK_BASE_URL`
- `OPS_AI_AUTH_JWT_SECRET`
- `ASSISTANT_AI_PROVIDER`
- `ASSISTANT_AI_FALLBACK_ENABLED`
- `OPENAI_API_KEY`, when `ASSISTANT_AI_PROVIDER=openai`

UniMock:

- `SPRING_PROFILES_ACTIVE=prod`
- `OPS_AI_UNIMOCK_DB_URL`
- `OPS_AI_UNIMOCK_DB_USERNAME`
- `OPS_AI_UNIMOCK_DB_PASSWORD`

## Probes

Backend:

- liveness: `/actuator/health/liveness`
- readiness: `/actuator/health/readiness`

UniMock:

- health: `/actuator/health`

## Pre-Deployment Checks

- Confirm database connectivity from the target network.
- Confirm Flyway migration ownership and rollout order.
- Confirm JWT secret rotation process.
- Confirm OpenAI key storage and access restrictions.
- Confirm CORS origins for the deployed mobile/web clients.
- Confirm logs do not include secrets or bearer tokens.

## Not Included Yet

- Bicep, Terraform, or ARM templates.
- Registry push jobs.
- Environment promotion gates.
- Production traffic cutover or rollback automation.
