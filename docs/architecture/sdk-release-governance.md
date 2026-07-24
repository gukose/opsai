# SDK Release Governance

Sprint 10F prepares the private TypeScript API client for internal release review. It does not publish the package, create tags, create GitHub releases, or change backend/mobile runtime behavior.

## Source Of Truth

The SDK release chain starts from the reviewed public API snapshot:

```text
docs/api/openapi-v1.yaml
```

Runtime OpenAPI drift checks and API compatibility governance remain authoritative. SDK release readiness consumes those outputs; it does not replace them with a second OpenAPI compatibility engine.

## Semantic Version Policy

`MAJOR` is required for public API v2 introduction, removal of previously supported SDK APIs, incompatible SDK wrapper changes, incompatible generated model changes, or forbidden breaking v1 changes if they are ever delivered through a new major SDK version.

Selecting a major SDK version does not automatically permit a breaking v1 backend API change. The backend API-version policy remains authoritative.

`MINOR` is required for new backward-compatible endpoints, new optional request properties, new response properties, new supported operations, newly deprecated operations, backward-compatible enum expansion under the documented enum policy, or new SDK helpers that do not break existing consumers.

`PATCH` is required for documentation-only contract changes, corrected descriptions/examples, internal build fixes, non-breaking transport fixes, generated-code determinism fixes, SDK implementation fixes that preserve public API, or contract corrections that do not alter actual runtime behavior.

`POTENTIALLY_BREAKING` API changes require explicit review. They are conservatively treated as major-impact for SDK release readiness unless acknowledgement metadata and API-version governance allow a narrower interpretation.

## Compatibility Mapping

- `BREAKING` -> major review required
- `POTENTIALLY_BREAKING` -> major review required until explicitly acknowledged
- `BACKWARD_COMPATIBLE` -> minor
- `DOCUMENTATION_ONLY` -> patch
- no contract change -> no contract-driven bump

Public SDK export changes are also considered:

- removed public export -> major
- added public export -> minor
- unchanged exports -> no SDK-surface bump

## Release Metadata

Release metadata lives at:

```text
sdk/typescript/release.yaml
```

It records package name/version, API version, internal release status, `publicationAllowed: false`, normalized OpenAPI snapshot SHA-256, required semantic-version impact, generator name/version, public export-manifest hash, and local package archive output directory.

The OpenAPI hash is generated from a parsed and stably serialized snapshot, not from Git commit IDs. This keeps the identity reproducible in dirty local worktrees.

## Public Export Manifest

The public SDK surface is reviewed through:

```text
sdk/typescript/public-api-manifest.json
```

The manifest includes the package entry point, ESM module format, runtime assumptions, root runtime exports, root TypeScript type exports, generated operation functions, and generated operation option/request/response types.

Private implementation details, scripts, tests, and source files outside the package root entry point are not public SDK API.

## Release Notes

SDK release notes live at:

```text
sdk/typescript/CHANGELOG.md
```

The generated current entry includes SDK version, associated API version, required version impact, contract hash, export-manifest policy, and publication status. It summarizes consumer impact instead of duplicating the full API changelog.

## Package Contents

The package remains private and uses an allowlist. Local packing includes only `package.json`, `README.md`, `CHANGELOG.md`, compiled JavaScript under `dist/`, and TypeScript declarations under `dist/`.

It excludes tests, scripts, source fixtures, `node_modules`, temporary files, local environment files, CI files, internal reports, credentials, and Gradle build output.

Archives are written only under ignored local build output:

```text
sdk/typescript/build/package/
```

Standard `npm pack` metadata can prevent byte-for-byte archive reproducibility across environments. Sprint 10F verifies deterministic unpacked package contents and file hashes instead.

## Isolated Consumer Verification

Packed-package verification creates a temporary npm project, installs the local tarball, and compiles a strict TypeScript consumer that imports only from `@hotelopai/api-client`.

The consumer verifies typed login, protected current-user lookup, task pagination, `ApiError`, and package-root exports without starting the backend or mobile app.

## Publication Safeguards

The release-readiness check fails if `private` is removed, publish scripts are added, `publishConfig.registry` is configured, release metadata allows publication, package contents contain secret-like values, or package contents include tests, scripts, environment files, or other non-consumer files.

CI never publishes the SDK, uploads it to a registry, creates tags, or creates GitHub releases.

## Commands

Generate reviewed artifacts:

```bash
./gradlew generateTypeScriptApiClientExportManifest
cd sdk/typescript && npm run generate:release-metadata
./gradlew generateTypeScriptApiClientReleaseNotes
```

Verify release readiness:

```bash
./gradlew verifyTypeScriptApiClientVersion
./gradlew verifyTypeScriptApiClientExportManifest
./gradlew verifyTypeScriptApiClientReleaseNotes
./gradlew packTypeScriptApiClient
./gradlew verifyPackedTypeScriptApiClient
./gradlew verifyTypeScriptApiClientReleaseReadiness
```

## Future API v2

When API v2 is introduced, it should receive a separate reviewed OpenAPI snapshot and an explicit SDK major-version plan. The v1 compatibility policy still applies to v1 while v2 coexists.
