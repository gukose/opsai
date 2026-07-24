# Hotel OpAI TypeScript API Client

This package is generated and verified from `../../docs/api/openapi-v1.yaml`.

The mobile application consumes this SDK through a local file dependency and a mobile-owned adapter. The package remains private and is not published.

## Commands

```bash
npm ci
npm run generate
npm run verify
npm run build
npm test
npm run verify:release-readiness
```

Root Gradle equivalents:

```bash
./gradlew generateTypeScriptApiClient
./gradlew verifyTypeScriptApiClient
./gradlew buildTypeScriptApiClient
./gradlew testTypeScriptApiClient
./gradlew verifyTypeScriptApiClientReleaseReadiness
```

Generated files live under `src/generated/` and must not be edited manually. Handwritten transport and examples live directly under `src/`.

## Release Readiness

Sprint 10F adds internal release-readiness checks:

- `release.yaml` records package version, API version, normalized OpenAPI hash, generator version, required semantic-version impact, and publication status.
- `public-api-manifest.json` governs root exports and generated operation exports.
- `CHANGELOG.md` contains deterministic SDK release notes.
- `npm run pack:local` creates a local archive under `build/package/` without publishing.
- `npm run verify:packed` inspects archive contents and compiles an isolated TypeScript consumer from the tarball.

The package must remain `private: true`; publication scripts and registry configuration are forbidden.
