# Sprint 10

## Sprint 10A: API Versioning and Contract Stability

Sprint 10A establishes the API compatibility baseline for external clients.

Implemented scope:

- Confirmed the existing REST API surface is path-versioned under `/api/v1`.
- Added a runtime `X-API-Version: v1` response header for `/api/v1` responses.
- Added `@DeprecatedApi` and deprecation response-header support for future endpoint migrations.
- Documented the version lifecycle, compatibility rules, deprecation policy, error contract, pagination contract, timestamp policy, and endpoint inventory.
- Added a static OpenAPI v1 contract artifact at `docs/api/openapi-v1.yaml`.

No endpoint is deprecated in Sprint 10A.

No request or response DTO schema was changed.

Deferred:

- `/api/v2` endpoints.
- Automated client SDK generation.
- Formal public API changelog automation.

## Sprint 10B: Runtime OpenAPI Publishing and Contract Drift Protection

Sprint 10B publishes and verifies the runtime API contract.

Implemented scope:

- Added SpringDoc runtime OpenAPI generation for Spring Boot 4.
- Published public v1 OpenAPI at `/v3/api-docs/v1`.
- Published local/test Dev PMS OpenAPI separately at `/v3/api-docs/dev-pms`.
- Kept Actuator and infrastructure endpoints out of the public v1 group.
- Added reusable OpenAPI components for bearer JWT auth, ProblemDetail errors, task pagination, API version headers, and deprecation headers.
- Added deterministic snapshot verification against `docs/api/openapi-v1.yaml`.
- Added explicit Gradle workflows:
  - `./gradlew :backend:verifyOpenApiContract`
  - `./gradlew :backend:refreshOpenApiContract`
- Added CI contract verification before the general backend test run.

Environment policy:

- Local: OpenAPI JSON and Swagger UI enabled.
- Test: OpenAPI JSON enabled; Swagger UI disabled.
- Production: OpenAPI JSON and Swagger UI disabled by default.

No controller path, DTO, authentication, or business behavior was changed.

Deferred:

- API v2.
- Client SDK generation.
- Public hosted documentation portal.
- Automatic semantic-version release notes for API changes.

## Sprint 10C: API Compatibility Classification, Changelog Automation, and Release Governance

Sprint 10C adds release-governance checks around the reviewed OpenAPI snapshot.

Implemented scope:

- Added a deterministic semantic compatibility classifier for `docs/api/openapi-v1.yaml`.
- Added baseline comparison against the target Git branch, with local override support through `OPENAPI_BASELINE_REF` or `-Dhotelopai.openapi.baseline.ref=<git-ref>`.
- Added `docs/api/api-change-acknowledgements.yaml` for precise intentional-change acknowledgement.
- Added stale and duplicate acknowledgement validation.
- Added forbidden v1 breaking-change enforcement; breaking changes require API v2 unless explicitly marked as a contract correction for behavior that never existed.
- Added deterministic API changelog generation at `docs/api/CHANGELOG.md`.
- Added Gradle workflows:
  - `./gradlew :backend:checkOpenApiCompatibility`
  - `./gradlew :backend:generateOpenApiChangelog`
- Updated CI so runtime drift verification runs before compatibility and changelog validation.

Compatibility categories:

- `BREAKING`
- `POTENTIALLY_BREAKING`
- `BACKWARD_COMPATIBLE`
- `DOCUMENTATION_ONLY`

The classifier is intentionally conservative. Ambiguous schema mutations are treated as potentially breaking until reviewed.

No runtime endpoint path, request DTO, response DTO, authentication behavior, status code, or business behavior was changed in Sprint 10C.

Deferred:

- API v2.
- Client SDK generation.
- Hosted API documentation portal.
- Release creation, tagging, or publishing automation.

## Sprint 10D: Type-Safe Client SDK Generation and Consumer Contract Verification

Sprint 10D proves that the governed public v1 OpenAPI snapshot can produce a usable TypeScript consumer artifact.

Implemented scope:

- Added isolated private SDK package at `sdk/typescript`.
- Selected and pinned `openapi-typescript` for deterministic OpenAPI type generation.
- Generated committed SDK types and operation functions from `docs/api/openapi-v1.yaml`.
- Added a small handwritten fetch transport for bearer-token injection, structured `ProblemDetail` errors, and response metadata.
- Added SDK commands:
  - `./gradlew generateTypeScriptApiClient`
  - `./gradlew verifyTypeScriptApiClient`
  - `./gradlew buildTypeScriptApiClient`
  - `./gradlew testTypeScriptApiClient`
- Added consumer contract tests for authentication, bearer headers, public refresh/login behavior, task pagination, ProblemDetail errors, route scope, and response-header access.
- Updated CI so SDK freshness, build, and consumer tests run after API compatibility governance.
- Corrected the OpenAPI description for `GET /api/v1/tasks` to document the existing compatibility response shape: `TaskResponse[]` or `TaskPageResponse`.

No mobile runtime code imports the SDK in Sprint 10D.

No runtime endpoint path, request DTO, response DTO, authentication behavior, status code, permission, or business behavior was changed.

Deferred:

- Mobile migration to the generated SDK.
- npm package publishing.
- Client SDK release automation.
- API v2.

## Sprint 10E: Mobile SDK Adoption and Typed API Client Migration

Sprint 10E migrates the mobile application to the generated public v1 TypeScript SDK without changing backend runtime behavior.

Implemented scope:

- Added the private SDK as a local mobile dependency with `@hotelopai/api-client: file:../sdk/typescript`.
- Added a mobile SDK adapter at `mobile/src/api/hotelOpAiClient.ts`.
- Replaced public v1 handwritten URL construction and bearer-header construction in auth, task, dashboard, and assistant API wrappers.
- Kept secure storage, session restoration, refresh-token handling, and logout/session clearing in the mobile application.
- Ensured login and refresh calls are sent without stale bearer tokens.
- Preserved one-shot expired-token refresh retry behavior for protected `GET` calls.
- Replaced duplicated transport DTOs with SDK-derived schema types where they represent wire contracts.
- Preserved mobile domain/view models by mapping SDK transport responses at service boundaries.
- Centralized task list array-versus-page normalization in the task service boundary.
- Preserved structured `ProblemDetail` errors through the existing `AppApiError` model.
- Kept dev-only and infrastructure endpoints out of the SDK.
- Removed obsolete `FetchApiClient` and `ApiClient` code after public v1 callers moved to the SDK adapter.
- Updated CI so OpenAPI checks, compatibility governance, SDK verification, and mobile verification run before backend and UniMock tests.

No backend endpoint path, request DTO, response DTO, authentication rule, permission, status code, or business behavior was changed.

Deferred:

- Publishing the SDK.
- Migrating any future development-only API surface into a generated development SDK.
- Changing mobile screens, navigation, state management, or user-visible behavior.
- API v2.

## Sprint 10F: SDK Versioning, Release Readiness, and Contract-to-Consumer Release Governance

Sprint 10F adds internal SDK release-readiness automation without publishing packages or changing runtime behavior.

Implemented scope:

- Added SDK semantic-version policy and compatibility-to-version mapping.
- Added deterministic release metadata at `sdk/typescript/release.yaml`.
- Added normalized OpenAPI snapshot hash validation.
- Added public SDK export manifest governance at `sdk/typescript/public-api-manifest.json`.
- Added deterministic SDK release notes at `sdk/typescript/CHANGELOG.md`.
- Added package privacy, export, file allowlist, side-effect, and engine metadata.
- Added local package creation under ignored `sdk/typescript/build/package/`.
- Added package-content checks for allowed files, publication safeguards, and secret-like content.
- Added isolated TypeScript consumer verification against the locally packed SDK archive.
- Added release-readiness Gradle and npm commands.
- Updated CI so SDK release-readiness checks run before mobile and backend/UniMock tests.

No SDK is published. No tags, GitHub releases, deployment artifacts, commits, or pushes are created by this workflow.

Deferred:

- Public or private registry publishing.
- Release/tag creation automation.
- API v2 and SDK major-version release workflow execution.
- Byte-for-byte archive reproducibility guarantees beyond verified unpacked package contents.
