# TypeScript API Client

Sprint 10D added an isolated generated TypeScript client for the governed public v1 API contract. Sprint 10E migrates the mobile application to consume that client for public v1 calls through a mobile-owned adapter.

## Source Contract

The only SDK input is:

```text
docs/api/openapi-v1.yaml
```

The SDK is not generated from a live backend or runtime OpenAPI endpoint. Runtime drift verification and compatibility governance must pass before SDK verification.

## Package Location

```text
sdk/typescript/
```

The package is private and is not published:

```json
{
  "private": true
}
```

The mobile runtime consumes this package as a local file dependency:

```json
{
  "@hotelopai/api-client": "file:../sdk/typescript"
}
```

The SDK remains private and unpublished.

## Generator

Selected generator:

- `openapi-typescript` `7.10.1`

Rationale:

- OpenAPI 3.x support
- deterministic TypeScript type generation
- small toolchain footprint
- no framework-specific runtime client
- no hosted service dependency
- works with the repository's Node 22 CI setup

Alternatives considered:

- OpenAPI Generator: broader ecosystem, but heavier Java generator surface and more generated runtime code than needed.
- Orval: strong client generation, but more frontend-framework-oriented defaults than this repository needs.

The handwritten wrapper provides fetch transport, bearer-token injection, `ProblemDetail` error handling, and operation functions generated from stable OpenAPI operation IDs.

## File Boundaries

Generated:

```text
sdk/typescript/src/generated/schema.ts
sdk/typescript/src/generated/operations.ts
```

Handwritten:

```text
sdk/typescript/src/client.ts
sdk/typescript/src/index.ts
sdk/typescript/src/examples.ts
sdk/typescript/scripts/
sdk/typescript/tests/
```

Generated files are committed so API changes create visible consumer diffs in review. They must not be edited manually.

## Commands

```bash
cd sdk/typescript
npm ci
npm run generate
npm run verify
npm run build
npm test
```

Root Gradle equivalents:

```bash
./gradlew generateTypeScriptApiClient
./gradlew verifyTypeScriptApiClient
./gradlew buildTypeScriptApiClient
./gradlew testTypeScriptApiClient
```

## Authentication

The SDK accepts an injected access-token provider:

```ts
createHotelOpAiClient({
  baseUrl,
  accessToken: () => currentAccessToken
});
```

Login and refresh are generated as public operations because the OpenAPI snapshot marks them with `security: []`. Protected operations request the current token when called.

The SDK does not persist tokens, refresh tokens, log credentials, or orchestrate session refresh.

## Mobile Adapter

Mobile integration is isolated in:

```text
mobile/src/api/hotelOpAiClient.ts
```

Responsibilities:

- construct the generated SDK client with the configured API base URL
- inject the current access token through a mobile-owned provider
- omit bearer tokens for login and refresh calls
- retry one expired-token `GET` after the mobile session service refreshes the token
- preserve existing request timeout, correlation ID, and safe retry behavior
- map SDK `ApiError` objects into the existing mobile `AppApiError`
- preserve structured `ProblemDetail` data and `X-API-Version` response metadata

Secure token storage, refresh-token rotation, session restoration, logout clearing, and failed-refresh session invalidation remain mobile responsibilities. The generated SDK does not persist or refresh tokens.

UI components should not import generated SDK operations directly. Public v1 operations are exposed through mobile API/service boundaries such as `HttpAuthApi`, `HttpTaskApi`, `HttpDashboardApi`, and `HttpAssistantApi`.

## Error Handling

Non-2xx responses throw `ApiError`.

`ApiError` exposes:

- `status`
- structured `ProblemDetail` when returned by the backend
- response `headers`
- `apiVersion` from `X-API-Version`

The SDK does not collapse structured API errors into strings.

## Pagination

Task pagination is represented by the generated `TaskPageResponse` schema. The task list operation also preserves the reviewed contract behavior where the response may be either the legacy array shape or the paged envelope, according to the OpenAPI snapshot.

Mobile normalizes both task list response shapes at the task service boundary so screens continue to consume stable task summaries.

## Public Versus Dev-Only APIs

The generated SDK is built only from the public v1 OpenAPI snapshot. It does not include:

- `/api/v1/dev/pms/**`
- Actuator endpoints
- test-only authentication-context endpoints

If development-only mobile API calls are needed later, they must remain behind a clearly named development-only client and environment guard rather than being added to the public SDK.

## Verification

CI runs:

1. runtime OpenAPI snapshot verification
2. API compatibility governance
3. generated SDK freshness verification
4. SDK type-check
5. SDK consumer tests
6. SDK semantic-version and release-metadata verification
7. SDK public export-manifest verification
8. SDK release-note verification
9. local SDK package creation and packed-consumer verification
10. mobile dependency install, type-check, and tests
11. backend and UniMock tests

`verifyTypeScriptApiClient` regenerates into a temporary directory and compares against committed generated files without modifying the working tree.

## Generator Upgrades

Generator upgrades must be reviewed like API contract changes:

1. update the pinned version in `sdk/typescript/package.json`
2. run `npm install` in `sdk/typescript`
3. regenerate the SDK
4. review generated diffs
5. run SDK and API contract verification

## Release Governance

Sprint 10F adds SDK release-readiness automation without publishing the package. The release policy is documented in [SDK Release Governance](sdk-release-governance.md).

Key artifacts:

```text
sdk/typescript/release.yaml
sdk/typescript/public-api-manifest.json
sdk/typescript/CHANGELOG.md
```

The required SDK semantic-version impact is derived from API compatibility output and public SDK export-manifest changes. Local package archives are built under `sdk/typescript/build/package/` and verified through an isolated TypeScript consumer.

## Limitations

- It does not implement token refresh orchestration.
- It does not publish an npm package.
- It validates consumer contract shape, not backend business behavior.
- Mobile still owns view/domain models and maps SDK transport types at service boundaries.
- Mobile runtime behavior was intentionally preserved rather than redesigned.
- Byte-for-byte package archive reproducibility is not guaranteed by npm tooling; unpacked package contents and file hashes are verified.
