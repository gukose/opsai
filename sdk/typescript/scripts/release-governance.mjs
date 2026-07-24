import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync
} from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(packageRoot, "../..");
const packageJsonPath = path.join(packageRoot, "package.json");
const packageLockPath = path.join(packageRoot, "package-lock.json");
const releaseMetadataPath = path.join(packageRoot, "release.yaml");
const exportManifestPath = path.join(packageRoot, "public-api-manifest.json");
const releaseNotesPath = path.join(packageRoot, "CHANGELOG.md");
const apiChangelogPath = path.join(repoRoot, "docs/api/CHANGELOG.md");
const openApiSnapshotPath = path.join(repoRoot, "docs/api/openapi-v1.yaml");
const buildDir = path.join(packageRoot, "build");
const packageBuildDir = path.join(buildDir, "package");
const npmCacheDir = path.join(buildDir, "npm-cache");

const VERSION_IMPACT_ORDER = ["none", "patch", "minor", "major"];
const SECRET_PATTERNS = [
  { name: "bearer token", pattern: /Bearer\s+[A-Za-z0-9._~+/-]+=*/i },
  { name: "OpenAI-style key", pattern: /sk-[A-Za-z0-9]{20,}/ },
  { name: "password assignment", pattern: /password\s*=\s*[^,\s]+/i },
  { name: "npm token", pattern: /_authToken\s*=/i }
];

export function stableStringify(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

export function normalizedOpenApiHash(openApiYaml) {
  const parsed = YAML.parse(openApiYaml);
  return sha256(stableStringify(parsed));
}

export function parseSemver(version) {
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.exec(version);
  if (!match) {
    throw new Error(`Invalid semantic version: ${version}`);
  }
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3])
  };
}

export function compareSemver(left, right) {
  const a = parseSemver(left);
  const b = parseSemver(right);
  return a.major - b.major || a.minor - b.minor || a.patch - b.patch;
}

export function versionImpactSatisfied(baselineVersion, currentVersion, requiredImpact) {
  if (compareSemver(currentVersion, baselineVersion) < 0) {
    return false;
  }
  const baseline = parseSemver(baselineVersion);
  const current = parseSemver(currentVersion);
  switch (requiredImpact) {
    case "none":
      return true;
    case "patch":
      return current.major > baseline.major ||
        current.minor > baseline.minor ||
        (current.minor === baseline.minor && current.patch > baseline.patch);
    case "minor":
      return current.major > baseline.major || current.minor > baseline.minor;
    case "major":
      return current.major > baseline.major;
    default:
      throw new Error(`Unknown required version impact: ${requiredImpact}`);
  }
}

export function combineVersionImpacts(...impacts) {
  return impacts.reduce((highest, impact) =>
    VERSION_IMPACT_ORDER.indexOf(impact) > VERSION_IMPACT_ORDER.indexOf(highest) ? impact : highest,
  "none");
}

export function apiChangelogImpact(changelogText) {
  const text = changelogText.toLowerCase();
  if (text.includes("potentially_breaking") || text.includes("potentially breaking")) {
    return "major";
  }
  if (text.includes("breaking")) {
    return "major";
  }
  if (text.includes("backward_compatible") || text.includes("backward compatible") || text.includes("added endpoint")) {
    return "minor";
  }
  if (text.includes("documentation_only") || text.includes("documentation only")) {
    return "patch";
  }
  if (text.includes("no contract changes detected")) {
    return "none";
  }
  return "none";
}

export function exportManifestImpact(baselineManifest, currentManifest) {
  if (!baselineManifest) {
    return "none";
  }
  const baselineExports = manifestExportSet(baselineManifest);
  const currentExports = manifestExportSet(currentManifest);
  const removed = [...baselineExports].filter((name) => !currentExports.has(name));
  const added = [...currentExports].filter((name) => !baselineExports.has(name));
  return {
    impact: removed.length > 0 ? "major" : added.length > 0 ? "minor" : "none",
    removed,
    added
  };
}

export function createExportManifest(input) {
  const packageJson = input.packageJson;
  const indexSource = input.indexSource;
  const operationSource = input.operationSource;
  const rootExports = collectRootExports(indexSource);
  const operationFunctions = [...operationSource.matchAll(/^export function ([A-Za-z_$][A-Za-z0-9_$]*)/gm)].map((match) => match[1]).sort();
  const operationTypes = [...operationSource.matchAll(/^export type ([A-Za-z_$][A-Za-z0-9_$]*)/gm)].map((match) => match[1]).sort();
  const allExports = [...rootExports.runtime, ...rootExports.types, ...operationFunctions, ...operationTypes].sort();
  const duplicates = allExports.filter((name, index) => allExports.indexOf(name) !== index);
  if (duplicates.length > 0) {
    throw new Error(`Duplicate public SDK exports: ${[...new Set(duplicates)].join(", ")}`);
  }
  return {
    packageName: packageJson.name,
    version: packageJson.version,
    entryPoints: {
      ".": {
        import: "./dist/index.js",
        types: "./dist/index.d.ts"
      }
    },
    moduleFormat: "esm",
    runtimeAssumptions: {
      fetch: "global fetch API",
      node: packageJson.engines?.node ?? ">=20"
    },
    rootRuntimeExports: rootExports.runtime,
    rootTypeExports: rootExports.types,
    generatedOperationExports: operationFunctions,
    generatedOperationTypeExports: operationTypes
  };
}

export function createReleaseMetadata(input) {
  return {
    packageName: input.packageJson.name,
    version: input.packageJson.version,
    apiVersion: "v1",
    releaseStatus: "internal",
    publicationAllowed: false,
    requiredVersionImpact: input.requiredVersionImpact,
    contract: {
      snapshot: "docs/api/openapi-v1.yaml",
      normalizedSha256: input.contractHash
    },
    generator: {
      name: "openapi-typescript",
      version: input.packageJson.devDependencies["openapi-typescript"]
    },
    exportManifest: {
      path: "sdk/typescript/public-api-manifest.json",
      sha256: sha256(stableStringify(input.exportManifest))
    },
    packageArchive: {
      publish: false,
      outputDirectory: "sdk/typescript/build/package"
    }
  };
}

export function renderReleaseNotes(input) {
  const lines = [
    "# TypeScript SDK Changelog",
    "",
    "Generated from SDK release metadata, API governance output, and the public export manifest.",
    "Do not edit generated release entries by hand.",
    "",
    `## ${input.packageJson.version}`,
    "",
    `- API version: ${input.releaseMetadata.apiVersion}`,
    `- Required version impact: ${input.releaseMetadata.requiredVersionImpact}`,
    `- Contract hash: ${input.releaseMetadata.contract.normalizedSha256}`,
    "",
    "### Consumer Impact",
    ""
  ];
  if (input.releaseMetadata.requiredVersionImpact === "none") {
    lines.push("- No contract-driven SDK version bump is required for the current reviewed snapshot.");
  } else {
    lines.push(`- Minimum semantic-version impact: ${input.releaseMetadata.requiredVersionImpact}.`);
  }
  lines.push("- Public package exports are governed by `public-api-manifest.json`.");
  lines.push("- Package publication remains disabled; release readiness creates only local archives.");
  lines.push("");
  return `${lines.join("\n")}\n`;
}

export function validatePackageReleaseState(input) {
  const errors = [];
  const { packageJson, packageLock, releaseMetadata, currentContractHash, currentExportManifest, baselinePackageJson, baselineExportManifest, apiChangelogText } = input;
  try {
    parseSemver(packageJson.version);
  } catch (error) {
    errors.push(error.message);
  }
  if (packageJson.private !== true) errors.push("package.json must keep private=true.");
  if (packageJson.publishConfig?.registry) errors.push("publishConfig.registry is not allowed for the private SDK.");
  if (packageJson.scripts && Object.entries(packageJson.scripts).some(([name, value]) => name.includes("publish") || String(value).includes("npm publish"))) {
    errors.push("SDK package scripts must not publish.");
  }
  if (packageLock.packages?.[""]?.version !== packageJson.version) {
    errors.push(`package-lock root version ${packageLock.packages?.[""]?.version} does not match package.json ${packageJson.version}.`);
  }
  if (releaseMetadata.packageName !== packageJson.name) errors.push("release metadata packageName does not match package.json.");
  if (releaseMetadata.version !== packageJson.version) errors.push("release metadata version does not match package.json.");
  if (releaseMetadata.apiVersion !== "v1") errors.push("release metadata apiVersion must remain v1.");
  if (releaseMetadata.publicationAllowed !== false) errors.push("release metadata must keep publicationAllowed=false.");
  if (releaseMetadata.generator?.version !== packageJson.devDependencies?.["openapi-typescript"]) {
    errors.push("release metadata generator version does not match package.json.");
  }
  if (releaseMetadata.contract?.normalizedSha256 !== currentContractHash) {
    errors.push("release metadata contract hash is stale. Run npm run generate:release-metadata.");
  }
  if (releaseMetadata.exportManifest?.sha256 !== sha256(stableStringify(currentExportManifest))) {
    errors.push("release metadata export manifest hash is stale. Run npm run generate:release-metadata.");
  }

  const exportImpact = exportManifestImpact(baselineExportManifest, currentExportManifest);
  const requiredImpact = combineVersionImpacts(apiChangelogImpact(apiChangelogText), exportImpact.impact);
  if (releaseMetadata.requiredVersionImpact !== requiredImpact) {
    errors.push(`release metadata requiredVersionImpact=${releaseMetadata.requiredVersionImpact} but calculated ${requiredImpact}.`);
  }
  if (baselinePackageJson && !versionImpactSatisfied(baselinePackageJson.version, packageJson.version, requiredImpact)) {
    errors.push(`SDK version ${packageJson.version} does not satisfy required ${requiredImpact} bump from baseline ${baselinePackageJson.version}.`);
  }
  return { ok: errors.length === 0, errors, requiredImpact, exportImpact };
}

export function packageFileAllowed(relativePath) {
  if (relativePath === "package/package.json") return true;
  if (relativePath === "package/README.md") return true;
  if (relativePath === "package/CHANGELOG.md") return true;
  if (relativePath.startsWith("package/dist/")) return true;
  return false;
}

export function inspectPackageFiles(files) {
  const errors = [];
  for (const file of files) {
    if (!packageFileAllowed(file)) errors.push(`Unexpected package file: ${file}`);
    if (file.includes("node_modules/") || file.includes("/tests/") || file.includes("/scripts/") || file.includes(".env")) {
      errors.push(`Forbidden package file: ${file}`);
    }
  }
  return errors;
}

export function baselineRefFromEnv(env) {
  return {
    ref: env.OPENAPI_BASELINE_REF || env.GITHUB_BASE_REF || "main",
    explicit: Boolean(env.OPENAPI_BASELINE_REF || env.GITHUB_BASE_REF)
  };
}

export function readCurrentInputs() {
  const packageJson = readJson(packageJsonPath);
  return {
    packageJson,
    packageLock: readJson(packageLockPath),
    releaseMetadata: existsSync(releaseMetadataPath) ? YAML.parse(readFileSync(releaseMetadataPath, "utf8")) : null,
    exportManifest: existsSync(exportManifestPath) ? readJson(exportManifestPath) : null,
    apiChangelogText: readFileSync(apiChangelogPath, "utf8"),
    openApiYaml: readFileSync(openApiSnapshotPath, "utf8"),
    indexSource: readFileSync(path.join(packageRoot, "src/index.ts"), "utf8"),
    operationSource: readFileSync(path.join(packageRoot, "src/generated/operations.ts"), "utf8")
  };
}

function commandGenerateExportManifest() {
  const input = readCurrentInputs();
  const manifest = createExportManifest(input);
  writeJson(exportManifestPath, manifest);
}

function commandVerifyExportManifest() {
  const input = readCurrentInputs();
  const expected = createExportManifest(input);
  const actual = input.exportManifest;
  if (!actual || stableStringify(actual) !== stableStringify(expected)) {
    throw new Error("Public SDK export manifest is stale. Run npm run generate:exports.");
  }
}

function commandGenerateReleaseMetadata() {
  const input = readCurrentInputs();
  const exportManifest = input.exportManifest ?? createExportManifest(input);
  const metadata = createReleaseMetadata({
    packageJson: input.packageJson,
    contractHash: normalizedOpenApiHash(input.openApiYaml),
    requiredVersionImpact: combineVersionImpacts(apiChangelogImpact(input.apiChangelogText), exportManifestImpact(readBaselineExportManifest(), exportManifest).impact),
    exportManifest
  });
  writeFileSync(releaseMetadataPath, YAML.stringify(metadata, { sortMapEntries: true }));
}

function commandVerifyVersion() {
  const input = readCurrentInputs();
  if (!input.releaseMetadata) throw new Error("Missing sdk/typescript/release.yaml. Run npm run generate:release-metadata.");
  const currentExportManifest = createExportManifest(input);
  const result = validatePackageReleaseState({
    packageJson: input.packageJson,
    packageLock: input.packageLock,
    releaseMetadata: input.releaseMetadata,
    currentContractHash: normalizedOpenApiHash(input.openApiYaml),
    currentExportManifest,
    baselinePackageJson: readBaselinePackageJson(),
    baselineExportManifest: readBaselineExportManifest(),
    apiChangelogText: input.apiChangelogText
  });
  if (!result.ok) {
    throw new Error(`SDK version/release metadata verification failed:\n${result.errors.join("\n")}`);
  }
  console.log(`SDK version ${input.packageJson.version}; required impact ${result.requiredImpact}.`);
}

function commandGenerateReleaseNotes() {
  const input = readCurrentInputs();
  if (!input.releaseMetadata) {
    commandGenerateReleaseMetadata();
    input.releaseMetadata = YAML.parse(readFileSync(releaseMetadataPath, "utf8"));
  }
  writeFileSync(releaseNotesPath, renderReleaseNotes({
    packageJson: input.packageJson,
    releaseMetadata: input.releaseMetadata
  }));
}

function commandVerifyReleaseNotes() {
  const input = readCurrentInputs();
  if (!input.releaseMetadata) throw new Error("Missing release metadata. Run npm run generate:release-metadata.");
  const expected = renderReleaseNotes({ packageJson: input.packageJson, releaseMetadata: input.releaseMetadata });
  const actual = existsSync(releaseNotesPath) ? readFileSync(releaseNotesPath, "utf8") : "";
  if (actual !== expected) {
    throw new Error("SDK release notes are stale. Run npm run generate:release-notes.");
  }
}

function commandPack() {
  run("npm", ["run", "build"], packageRoot);
  rmSync(packageBuildDir, { recursive: true, force: true });
  mkdirSync(packageBuildDir, { recursive: true });
  const stagingDir = path.join(packageBuildDir, "staging");
  mkdirSync(stagingDir, { recursive: true });
  for (const file of ["package.json", "README.md", "CHANGELOG.md"]) {
    cpSync(path.join(packageRoot, file), path.join(stagingDir, file));
  }
  cpSync(path.join(packageRoot, "dist"), path.join(stagingDir, "dist"), { recursive: true });
  const pack = run("npm", ["pack", "--pack-destination", packageBuildDir, "--json"], stagingDir, {
    capture: true,
    env: localNpmEnv()
  });
  const output = JSON.parse(pack.stdout.trim())[0];
  const manifest = {
    archive: path.join("sdk/typescript/build/package", output.filename),
    filename: output.filename,
    size: output.size,
    unpackedSize: output.unpackedSize,
    shasum: output.shasum,
    integrity: output.integrity,
    files: output.files.map((file) => file.path).sort()
  };
  writeJson(path.join(packageBuildDir, "package-manifest.json"), manifest);
  console.log(`Packed SDK archive: ${manifest.archive}`);
}

function commandVerifyPacked() {
  if (!existsSync(packageBuildDir)) commandPack();
  const manifestPath = path.join(packageBuildDir, "package-manifest.json");
  if (!existsSync(manifestPath)) throw new Error("Missing package manifest. Run npm run pack:local.");
  const manifest = readJson(manifestPath);
  const archive = path.join(repoRoot, manifest.archive);
  if (!existsSync(archive)) throw new Error(`Missing packed archive: ${manifest.archive}`);
  const unpackDir = path.join(packageBuildDir, "unpacked");
  rmSync(unpackDir, { recursive: true, force: true });
  mkdirSync(unpackDir, { recursive: true });
  run("tar", ["-xzf", archive, "-C", unpackDir], packageRoot);
  const unpackedFiles = listFiles(unpackDir).sort();
  const contentErrors = inspectPackageFiles(unpackedFiles);
  if (contentErrors.length > 0) throw new Error(contentErrors.join("\n"));
  scanForSecrets(unpackDir);
  verifyPackedPackageMetadata(path.join(unpackDir, "package/package.json"));
  verifyIsolatedConsumer(archive);
  writeJson(path.join(packageBuildDir, "unpacked-file-hashes.json"), hashFiles(unpackDir));
}

function commandVerifyReleaseReadiness() {
  commandVerifyExportManifest();
  commandVerifyVersion();
  commandVerifyReleaseNotes();
  commandPack();
  commandVerifyPacked();
}

function verifyPackedPackageMetadata(packedPackageJsonPath) {
  const packed = readJson(packedPackageJsonPath);
  if (packed.private !== true) throw new Error("Packed package must remain private.");
  if (packed.publishConfig?.registry) throw new Error("Packed package must not declare publishConfig.registry.");
  if (packed.files?.some((file) => file !== "dist" && file !== "README.md" && file !== "CHANGELOG.md")) {
    throw new Error("Packed package files allowlist includes unexpected entries.");
  }
}

function verifyIsolatedConsumer(archive) {
  const tempRoot = mkdtempSync(path.join(os.tmpdir(), "hotelopai-sdk-consumer-"));
  try {
    writeJson(path.join(tempRoot, "package.json"), { private: true, type: "module", dependencies: { "@hotelopai/api-client": `file:${archive}` } });
    writeJson(path.join(tempRoot, "tsconfig.json"), {
      compilerOptions: {
        target: "ES2022",
        module: "NodeNext",
        moduleResolution: "NodeNext",
        strict: true,
        skipLibCheck: true,
        noEmit: true
      },
      include: ["consumer.ts"]
    });
    writeFileSync(path.join(tempRoot, "consumer.ts"), consumerExample());
    run("npm", ["install", "--ignore-scripts", "--package-lock=false"], tempRoot, { env: localNpmEnv() });
    run(path.join(packageRoot, "node_modules/.bin/tsc"), ["-p", "tsconfig.json"], tempRoot);
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
}

function consumerExample() {
  return `import {
  ApiError,
  AuthController_login,
  AuthController_me,
  TaskController_listTasks,
  createHotelOpAiClient,
  type ApiResponse,
  type TaskController_listTasksResponse
} from "@hotelopai/api-client";

const client = createHotelOpAiClient({
  baseUrl: "https://api.example.test",
  accessToken: () => "access-token",
  fetchImpl: async () => new Response(JSON.stringify({}), { headers: { "Content-Type": "application/json" } })
});

await AuthController_login(client, { body: { hotelCode: "demo", email: "admin@example.test", password: "secret" } });
await AuthController_me(client);
const tasks: ApiResponse<TaskController_listTasksResponse> = await TaskController_listTasks(client, { query: { page: 0, size: 20 } });
const maybePage = tasks.data;
if (!Array.isArray(maybePage) && maybePage.items) {
  maybePage.items.map((task) => task.id);
}
const error = new ApiError("failed", { status: 500, headers: new Headers() });
error.status.toFixed();
`;
}

function readBaselinePackageJson() {
  return readBaselineJson("sdk/typescript/package.json");
}

function readBaselineExportManifest() {
  return readBaselineJson("sdk/typescript/public-api-manifest.json");
}

function readBaselineJson(relativePath) {
  const { ref, explicit } = baselineRefFromEnv(process.env);
  const result = spawnSync("git", ["show", `${ref}:${relativePath}`], { cwd: repoRoot, encoding: "utf8" });
  if (result.status !== 0) {
    if (explicit) {
      throw new Error(`Unable to resolve SDK release baseline ${ref}:${relativePath}. Fetch the target branch or set OPENAPI_BASELINE_REF to a valid Git ref.`);
    }
    return null;
  }
  try {
    return JSON.parse(result.stdout);
  } catch {
    return null;
  }
}

function collectRootExports(source) {
  const runtime = [];
  const types = [];
  const exportBlockMatch = source.match(/export\s*\{([\s\S]*?)\}\s*from\s+"\.\/client\.js";/);
  if (exportBlockMatch) {
    for (const raw of exportBlockMatch[1].split(",")) {
      const item = raw.trim();
      if (!item) continue;
      if (item.startsWith("type ")) types.push(item.replace(/^type\s+/, "").trim());
      else runtime.push(item);
    }
  }
  const typeBlockMatch = source.match(/export type\s*\{([\s\S]*?)\}\s*from\s+"\.\/generated\/schema\.js";/);
  if (typeBlockMatch) {
    types.push(...typeBlockMatch[1].split(",").map((item) => item.trim()).filter(Boolean));
  }
  return { runtime: runtime.sort(), types: types.sort() };
}

function manifestExportSet(manifest) {
  return new Set([
    ...(manifest.rootRuntimeExports ?? []).map((name) => `runtime:${name}`),
    ...(manifest.rootTypeExports ?? []).map((name) => `type:${name}`),
    ...(manifest.generatedOperationExports ?? []).map((name) => `operation:${name}`),
    ...(manifest.generatedOperationTypeExports ?? []).map((name) => `operation-type:${name}`)
  ]);
}

function scanForSecrets(root) {
  const findings = [];
  for (const file of listFiles(root)) {
    if (!/\.(js|d\.ts|json|md)$/.test(file)) continue;
    const content = readFileSync(path.join(root, file), "utf8");
    for (const rule of SECRET_PATTERNS) {
      if (rule.pattern.test(content)) findings.push(`${rule.name} pattern in ${file}`);
    }
  }
  if (findings.length > 0) {
    throw new Error(`Packed SDK contains secret-like content:\n${findings.join("\n")}`);
  }
}

function hashFiles(root) {
  return Object.fromEntries(listFiles(root).sort().map((file) => [
    file,
    sha256(readFileSync(path.join(root, file)))
  ]));
}

function listFiles(root, prefix = "") {
  return readdirSync(path.join(root, prefix), { withFileTypes: true }).flatMap((entry) => {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory()) return listFiles(root, relative);
    return [relative];
  });
}

function readJson(file) {
  return JSON.parse(readFileSync(file, "utf8"));
}

function writeJson(file, value) {
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function localNpmEnv() {
  return {
    ...process.env,
    npm_config_cache: npmCacheDir,
    NPM_CONFIG_CACHE: npmCacheDir
  };
}

function run(command, args, cwd, options = {}) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: "utf8",
    env: options.env ?? process.env,
    stdio: options.capture ? ["ignore", "pipe", "pipe"] : "inherit"
  });
  if (result.status !== 0) {
    const stderr = result.stderr ? `\n${result.stderr}` : "";
    throw new Error(`${command} ${args.join(" ")} failed${stderr}`);
  }
  return result;
}

const commands = {
  "generate-export-manifest": commandGenerateExportManifest,
  "verify-export-manifest": commandVerifyExportManifest,
  "generate-release-metadata": commandGenerateReleaseMetadata,
  "verify-version": commandVerifyVersion,
  "generate-release-notes": commandGenerateReleaseNotes,
  "verify-release-notes": commandVerifyReleaseNotes,
  "pack": commandPack,
  "verify-packed": commandVerifyPacked,
  "verify-release-readiness": commandVerifyReleaseReadiness
};

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const command = process.argv[2];
  if (!commands[command]) {
    throw new Error(`Unknown release-governance command: ${command ?? "(missing)"}`);
  }
  commands[command]();
}
