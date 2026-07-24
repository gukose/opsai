# API Versioning and Contract Stability

Sprint 10A keeps the current production API as path-versioned `v1`.

## Strategy

- Public product APIs use `/api/v1/...` paths.
- Future breaking API changes must be introduced under a new path prefix such as `/api/v2/...`.
- Controllers for future versions should delegate to the same application services where behavior is shared.
- Existing `/api/v1` paths remain valid until an announced deprecation and sunset window completes.
- Internal background infrastructure such as the operational outbox and scheduler has no public API.
- Local/test UniMock PMS endpoints remain under `/api/v1/dev/pms/...` but are local/test profile only and permission protected.

Path versioning is used because it is explicit, cache-friendly, easy to route, and already matches the current controller surface. Header or media-type versioning can be added later for specialized clients, but it is not required for the existing mobile and operational API clients.

## Runtime Version Signal

All `/api/v1` responses include:

```http
X-API-Version: v1
```

This is additive and does not change request or response bodies.

## Compatibility Guarantees

Within `v1`, compatible changes include:

- adding optional request fields
- adding nullable or optional response fields
- adding new endpoints
- adding new enum values only after client impact review
- adding response headers
- tightening non-observable internal validation without rejecting previously valid requests

Within `v1`, incompatible changes are not allowed without a new version:

- removing or renaming fields
- changing field types
- changing enum wire values
- changing success status codes or response envelopes
- making optional request fields required
- changing pagination envelope shape
- changing documented error status semantics

## Deprecation Policy

Future deprecated endpoints should use `@DeprecatedApi`.

Deprecated responses may include:

- `Deprecation: true`
- `Sunset: <HTTP date or timestamp>`
- `Link: <deprecation-doc-url>; rel="deprecation"`
- `X-API-Deprecation-Info: <short migration note>`

No endpoint is deprecated in Sprint 10A.

## Error Contract

REST errors use Spring `ProblemDetail` with `application/problem+json` where the request reaches the API/security layer.

Stable fields:

- `type`
- `title`
- `status`
- `detail`
- `instance`

Current status semantics:

- `400`: invalid request shape, unsupported parameter, validation failure, or invalid operation input
- `401`: missing, invalid, expired, or revoked authentication
- `403`: authenticated but missing required permission
- `404`: resource not found or cross-tenant resource hidden by non-leaking lookup
- `409`: concurrency, idempotency, or invalid state conflict
- `429`: rate limit exceeded
- `500`: unexpected server error, with no internal details exposed

## Pagination Contract

Task listing preserves the existing compatibility behavior:

- `GET /api/v1/tasks` without filters or pagination returns a plain array.
- `GET /api/v1/tasks` with any filter or pagination parameter returns the stable paged envelope:
  - `items`
  - `page`
  - `size`
  - `totalItems`
  - `totalPages`
  - `hasNext`
  - `hasPrevious`

New list endpoints should use the paged envelope from the start.

## Timestamp and Enum Contract

- Timestamps are ISO-8601 instants in UTC.
- PostgreSQL-persisted operational timestamps use the backend persistence precision policy documented in `docs/architecture/persistence-timestamp-precision.md`.
- Enum values are serialized as uppercase stable wire values unless an endpoint explicitly documents a lower-case range token such as dashboard `range`.

## OpenAPI

Runtime OpenAPI is generated with SpringDoc.

Public runtime contract:

- JSON: `/v3/api-docs/v1`
- Swagger UI group: `v1`

Development-only UniMock PMS contract:

- JSON: `/v3/api-docs/dev-pms`
- Swagger UI group: `dev-pms`
- Available only when the `local` or `test` profile registers the Dev PMS controller.

Actuator endpoints and internal infrastructure are excluded from the public v1 group.

## Environment Policy

Local:

- OpenAPI JSON is enabled.
- Swagger UI is enabled.
- Swagger UI lists the public `v1` group and the local/test `dev-pms` group.

Test:

- OpenAPI JSON is enabled for contract verification.
- Swagger UI is disabled.

Production:

- OpenAPI JSON is disabled by default.
- Swagger UI is disabled by default.
- Enabling production documentation requires an explicit operational decision; it must not make protected business endpoints public.

## Contract Source Of Truth

The runtime SpringDoc document represents implementation truth.

`docs/api/openapi-v1.yaml` is the reviewed exported v1 snapshot. CI verifies that the runtime contract and committed snapshot are semantically equal after normalizing environment-specific `servers` values.

Verification:

```bash
./gradlew :backend:verifyOpenApiContract
```

Intentional refresh after reviewing API changes:

```bash
./gradlew :backend:refreshOpenApiContract
```

The verify task fails on semantic drift such as path, method, request schema, response schema, status-code, enum, operation-id, or security changes. The refresh task is explicit and is not used by CI.

## Compatibility Classification

Sprint 10C adds a semantic compatibility check between the reviewed snapshot and a Git baseline. The compatibility check is separate from runtime drift verification:

- `verifyOpenApiContract` proves the application still generates the committed snapshot.
- `checkOpenApiCompatibility` classifies how the committed snapshot differs from the selected baseline.

Compatibility categories:

- `BREAKING`: removed paths or methods, removed response statuses, removed response fields, removed enum values, request fields becoming required, nullable fields becoming non-nullable, incompatible type changes, removed parameters, parameter location changes, or newly added authentication requirements.
- `POTENTIALLY_BREAKING`: narrower validation limits, format changes, schema composition changes, changed security metadata where compatibility is ambiguous, or other mutations that cannot be proven safe.
- `BACKWARD_COMPATIBLE`: added endpoints or methods, added optional request fields, added response fields, added enum values under the documented enum policy, added optional response statuses, or removed authentication requirements.
- `DOCUMENTATION_ONLY`: deprecation metadata changes, summaries, descriptions, examples, tag text, formatting, and other non-semantic documentation changes.

The classifier is intentionally conservative. Unknown or ambiguous schema changes should be treated as `POTENTIALLY_BREAKING` until reviewed.

## Baseline Resolution

CI resolves the comparison baseline from Git:

- pull requests use `GITHUB_BASE_REF`, resolved as `origin/<base-ref>`
- direct branch builds fall back to `origin/main`, then local `main`, then `origin/master`, then local `master`
- local development may override the baseline with `OPENAPI_BASELINE_REF` or `-Dhotelopai.openapi.baseline.ref=<git-ref>`

If no baseline snapshot exists, the check bootstraps from the working-tree snapshot and reports no compatibility changes. This supports the first introduction of the contract workflow without copying a second permanent baseline into the repository.

## Change Acknowledgements

Intentional contract changes are acknowledged in `docs/api/api-change-acknowledgements.yaml`.

Each acknowledgement is specific to one generated change identifier and includes:

- `id`
- `apiVersion`
- `classification`
- `reason`
- `migration`
- `sprint`
- `deprecationStatus`
- optional `removalTarget`
- optional `contractCorrection`

Wildcard acknowledgements are not supported. Duplicate acknowledgement IDs fail validation. Stale acknowledgements fail validation when the referenced change is no longer present.

Acknowledging a `BREAKING` v1 change does not make it acceptable by itself. Breaking v1 changes still fail unless the acknowledgement marks `contractCorrection: true`, which is reserved for correcting a documented contract that never matched runtime behavior. Real breaking API behavior changes require a future API version.

## API Changelog

`docs/api/CHANGELOG.md` is generated from the compatibility comparison. It groups changes by API version and classification, lists affected paths, methods, schemas, fields, and includes acknowledgement migration guidance when present.

Verification checks that the changelog is current. Refresh it explicitly after reviewing an intentional contract change:

```bash
./gradlew :backend:generateOpenApiChangelog
```

The changelog avoids timestamps and formatting-only noise so repeated generation is deterministic.

## Developer Workflow

1. Change the API implementation.
2. Refresh the runtime snapshot with `./gradlew :backend:refreshOpenApiContract`.
3. Run `./gradlew :backend:verifyOpenApiContract`.
4. Run `./gradlew :backend:checkOpenApiCompatibility`.
5. Add precise acknowledgements only when the v1 compatibility policy allows the change.
6. Run `./gradlew :backend:generateOpenApiChangelog`.
7. Review the OpenAPI snapshot, acknowledgement file, and changelog diffs.
8. Regenerate and verify the TypeScript SDK with `./gradlew generateTypeScriptApiClient` and `./gradlew verifyTypeScriptApiClient`.
9. Run the SDK build/tests and the normal backend and UniMock test suites.

CI performs runtime drift verification, compatibility classification, acknowledgement validation, changelog freshness validation, generated SDK freshness verification, SDK build/tests, and then the broader test suite. CI never rewrites the snapshot, acknowledgement file, changelog, or generated SDK.

## Deprecation Governance

`@DeprecatedApi` remains the supported implementation hook for future endpoint deprecations. Deprecated operations must appear as deprecated in OpenAPI, carry useful migration guidance through the deprecation headers, and be reflected in the changelog when newly deprecated.

Endpoint removal from v1 is forbidden unless it is a documented contract correction for behavior that never existed. Planned removals require deprecation first and a future API version.

## Compatibility Engine Limits

The Sprint 10C compatibility engine is a focused semantic diff over normalized OpenAPI documents. It covers the stable public contract elements used by the backend today: paths, HTTP methods, parameters, request bodies, responses, component schemas, required fields, nullability, enums, formats, validation narrowing, schema composition, security, and deprecation metadata.

Known limits:

- It is conservative and may classify uncertain schema changes as `POTENTIALLY_BREAKING`.
- It does not replace generated SDK build and consumer tests.
- It does not replace human API review for business-level behavior changes that are not expressible in OpenAPI.
- It compares the reviewed snapshot, not live production traffic.

## TypeScript SDK

Sprint 10D adds an isolated private TypeScript SDK package under `sdk/typescript`.

The SDK is generated only from `docs/api/openapi-v1.yaml`, not from a live backend. Generated files are committed under `sdk/typescript/src/generated` so API contract changes produce reviewable consumer diffs.

SDK release readiness is governed separately in `docs/architecture/sdk-release-governance.md`. That policy maps API compatibility classifications and SDK public export changes to required SDK semantic-version impact while keeping the backend API-version policy authoritative.

Commands:

```bash
./gradlew generateTypeScriptApiClient
./gradlew verifyTypeScriptApiClient
./gradlew buildTypeScriptApiClient
./gradlew testTypeScriptApiClient
```

The SDK supports injected bearer-token resolution, public login/refresh calls without `Authorization`, structured `ProblemDetail` errors through `ApiError`, task pagination types, and `X-API-Version` response-header access.

The current mobile runtime is not migrated to the generated SDK in Sprint 10D.

Grouped YAML publication is deferred; SpringDoc JSON is the runtime contract used by verification.
