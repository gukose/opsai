import assert from "node:assert/strict";
import { test } from "node:test";
import {
  apiChangelogImpact,
  baselineRefFromEnv,
  combineVersionImpacts,
  createExportManifest,
  createReleaseMetadata,
  exportManifestImpact,
  inspectPackageFiles,
  normalizedOpenApiHash,
  parseSemver,
  renderReleaseNotes,
  stableStringify,
  validatePackageReleaseState,
  versionImpactSatisfied
} from "../scripts/release-governance.mjs";

test("semantic version parser rejects malformed versions and rollback fails", () => {
  assert.deepEqual(parseSemver("1.2.3"), { major: 1, minor: 2, patch: 3 });
  assert.throws(() => parseSemver("1.2"), /Invalid semantic version/);
  assert.equal(versionImpactSatisfied("1.2.3", "1.2.2", "none"), false);
});

test("version impact rules map patch minor and major requirements", () => {
  assert.equal(versionImpactSatisfied("1.2.3", "1.2.3", "none"), true);
  assert.equal(versionImpactSatisfied("1.2.3", "1.2.4", "patch"), true);
  assert.equal(versionImpactSatisfied("1.2.3", "1.3.0", "minor"), true);
  assert.equal(versionImpactSatisfied("1.2.3", "2.0.0", "major"), true);
  assert.equal(versionImpactSatisfied("1.2.3", "1.2.4", "minor"), false);
});

test("API changelog compatibility maps to SDK version impact conservatively", () => {
  assert.equal(apiChangelogImpact("No contract changes detected."), "none");
  assert.equal(apiChangelogImpact("DOCUMENTATION_ONLY description changed"), "patch");
  assert.equal(apiChangelogImpact("BACKWARD_COMPATIBLE added endpoint"), "minor");
  assert.equal(apiChangelogImpact("POTENTIALLY_BREAKING schema changed"), "major");
  assert.equal(combineVersionImpacts("patch", "minor", "none"), "minor");
});

test("export manifest detects added and removed public exports", () => {
  const current = manifest(["createHotelOpAiClient"], ["ApiResponse"], ["TaskController_listTasks"]);
  const added = manifest(["createHotelOpAiClient", "newHelper"], ["ApiResponse"], ["TaskController_listTasks"]);
  const removed = manifest([], ["ApiResponse"], ["TaskController_listTasks"]);

  assert.deepEqual(exportManifestImpact(current, current), { impact: "none", removed: [], added: [] });
  assert.equal(exportManifestImpact(current, added).impact, "minor");
  assert.equal(exportManifestImpact(current, removed).impact, "major");
});

test("export manifest generation is deterministic and rejects duplicate exports", () => {
  const packageJson = { name: "pkg", version: "1.0.0" };
  const input = {
    packageJson,
    indexSource: 'export { ApiError, type ApiResponse } from "./client.js";\nexport type { components } from "./generated/schema.js";\nexport * from "./generated/operations.js";',
    operationSource: "export type TaskResponse = unknown;\nexport function Task(client, options) { return client; }\n"
  };
  const first = createExportManifest(input);
  const second = createExportManifest(input);
  assert.equal(stableStringify(first), stableStringify(second));
  assert.throws(
    () => createExportManifest({ ...input, operationSource: "export function ApiError() {}\n" }),
    /Duplicate public SDK exports/
  );
});

test("release metadata validates package, lock, generator, hash, and privacy", () => {
  const packageJson = basePackage();
  const exportManifest = manifest(["createHotelOpAiClient"], ["ApiResponse"], []);
  const contractHash = normalizedOpenApiHash("openapi: 3.1.0\ninfo:\n  title: Test\n  version: v1\npaths: {}\n");
  const releaseMetadata = createReleaseMetadata({
    packageJson,
    contractHash,
    requiredVersionImpact: "none",
    exportManifest
  });
  const result = validatePackageReleaseState({
    packageJson,
    packageLock: { packages: { "": { version: "1.0.0" } } },
    releaseMetadata,
    currentContractHash: contractHash,
    currentExportManifest: exportManifest,
    baselinePackageJson: { version: "1.0.0" },
    baselineExportManifest: exportManifest,
    apiChangelogText: "No contract changes detected."
  });
  assert.equal(result.ok, true);
});

test("version validation fails on mismatches and unreviewed required bump", () => {
  const packageJson = basePackage("1.0.0");
  const exportManifest = manifest(["createHotelOpAiClient"], ["ApiResponse"], []);
  const metadata = createReleaseMetadata({
    packageJson,
    contractHash: "hash",
    requiredVersionImpact: "none",
    exportManifest
  });
  const result = validatePackageReleaseState({
    packageJson,
    packageLock: { packages: { "": { version: "0.9.0" } } },
    releaseMetadata: metadata,
    currentContractHash: "other-hash",
    currentExportManifest: exportManifest,
    baselinePackageJson: { version: "1.0.0" },
    baselineExportManifest: manifest(["createHotelOpAiClient", "removed"], ["ApiResponse"], []),
    apiChangelogText: "BACKWARD_COMPATIBLE added endpoint"
  });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((error) => error.includes("package-lock")));
  assert.ok(result.errors.some((error) => error.includes("contract hash")));
  assert.ok(result.errors.some((error) => error.includes("requiredVersionImpact")));
});

test("release notes are deterministic and summarize consumer impact", () => {
  const packageJson = basePackage();
  const releaseMetadata = {
    apiVersion: "v1",
    requiredVersionImpact: "minor",
    contract: { normalizedSha256: "abc" }
  };
  const first = renderReleaseNotes({ packageJson, releaseMetadata });
  const second = renderReleaseNotes({ packageJson, releaseMetadata });
  assert.equal(first, second);
  assert.match(first, /Minimum semantic-version impact: minor/);
});

test("package content inspection allows only consumer files", () => {
  assert.deepEqual(inspectPackageFiles([
    "package/package.json",
    "package/README.md",
    "package/CHANGELOG.md",
    "package/dist/index.js",
    "package/dist/index.d.ts"
  ]), []);
  assert.ok(inspectPackageFiles(["package/tests/client.test.mjs"]).some((error) => error.includes("Unexpected package file")));
  assert.ok(inspectPackageFiles(["package/.env"]).some((error) => error.includes("Forbidden package file")));
});

test("baseline ref resolution supports PR, local override, and default bootstrap", () => {
  assert.deepEqual(baselineRefFromEnv({ OPENAPI_BASELINE_REF: "origin/main" }), {
    ref: "origin/main",
    explicit: true
  });
  assert.deepEqual(baselineRefFromEnv({ GITHUB_BASE_REF: "main" }), {
    ref: "main",
    explicit: true
  });
  assert.deepEqual(baselineRefFromEnv({}), {
    ref: "main",
    explicit: false
  });
});

function basePackage(version = "1.0.0") {
  return {
    name: "@hotelopai/api-client",
    version,
    private: true,
    devDependencies: { "openapi-typescript": "7.10.1" }
  };
}

function manifest(runtime, types, operations) {
  return {
    rootRuntimeExports: runtime,
    rootTypeExports: types,
    generatedOperationExports: operations,
    generatedOperationTypeExports: []
  };
}
