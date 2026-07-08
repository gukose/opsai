Before Sprint 1 implementation, finalize the project structure and environment configuration standards together.

Do NOT implement application code.
Do NOT move existing files yet.

Use the existing documents:
- .handbook/project-structure.md
- .handbook/architecture.md
- .skills/architecture/SKILL.md
- .skills/backend/SKILL.md

Now extend them with environment/stage configuration rules.

Supported stages:
- local
- test
- prod

Create:

config/
local/
backend.env.yaml
unimock.env.yaml
mobile.env
test/
backend.env.yaml
unimock.env.yaml
mobile.env
prod/
backend.env.yaml
unimock.env.yaml
mobile.env

Update:
- .handbook/project-structure.md
- .handbook/backend.md
- .handbook/mobile.md
- .handbook/release-process.md
- .skills/architecture/SKILL.md
- .skills/backend/SKILL.md
- .skills/mobile/SKILL.md

Rules:

1. Do not commit real secrets.
2. Local/test may use dummy values.
3. Prod files must contain placeholders only.
4. Real prod secrets must come from environment variables, Azure Key Vault, or deployment secrets.
5. OpenAI API keys must never be committed.
6. Backend and UniMock must use Spring profiles:
    - local
    - test
    - prod
7. Mobile must use EXPO_PUBLIC_* variables only.
8. Do not hardcode URLs, ports, DB credentials, API keys, or CORS origins in code.
9. Local ports:
    - backend: http://localhost:8080
    - unimock: http://localhost:8090
    - mobile web: http://localhost:8081
10. Local database:
- one PostgreSQL instance
- public schema for Hotel OpAI
- unimock schema for UniMock
11. Seed load/reset endpoints are allowed only in local/test.
12. Debug indicators are allowed only in local/test.
13. CORS must be stage-specific.
14. Logging must avoid sensitive data.

Example backend.env.yaml should include placeholders for:
- spring profile
- server port
- database url
- database username
- database password
- schema
- jwt secret placeholder
- UniMock base URL
- OpenAI config placeholder
- logging level
- CORS origins

Example unimock.env.yaml should include placeholders for:
- spring profile
- server port
- database url
- schema
- active simulation dataset
- seed path
- logging level
- CORS origins

Example mobile.env should include:
- EXPO_PUBLIC_APP_ENV
- EXPO_PUBLIC_API_BASE_URL
- EXPO_PUBLIC_ASSISTANT_DATA_SOURCE
- EXPO_PUBLIC_ASSISTANT_API_BASE_URL

Also document config precedence:

1. default application config
2. stage env yaml
3. environment variables
4. deployment platform secrets

At the end, provide:
- created files
- updated files
- assumptions
- next recommended Sprint 1 planning prompt