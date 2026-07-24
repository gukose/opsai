import { mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const expectedDir = path.join(packageRoot, "src/generated");
const tempRoot = mkdtempSync(path.join(os.tmpdir(), "hotelopai-sdk-"));
const actualDir = path.join(tempRoot, "generated");

try {
  const generation = spawnSync(process.execPath, [path.join(packageRoot, "scripts/generate-client.mjs"), `--out=${actualDir}`], {
    cwd: packageRoot,
    stdio: "inherit"
  });
  if (generation.status !== 0) {
    throw new Error("Temporary SDK generation failed");
  }
  const differences = diffDirectories(expectedDir, actualDir);
  if (differences.length > 0) {
    throw new Error(
      `Generated TypeScript API client is stale:\n${differences.join("\n")}\nRun ./gradlew generateTypeScriptApiClient.`
    );
  }
} finally {
  rmSync(tempRoot, { recursive: true, force: true });
}

function diffDirectories(expected, actual) {
  const expectedFiles = files(expected);
  const actualFiles = files(actual);
  const all = [...new Set([...expectedFiles, ...actualFiles])].sort();
  return all.flatMap((relative) => {
    if (!expectedFiles.includes(relative)) return [`Unexpected generated file: ${relative}`];
    if (!actualFiles.includes(relative)) return [`Missing generated file: ${relative}`];
    const expectedContent = readFileSync(path.join(expected, relative), "utf8");
    const actualContent = readFileSync(path.join(actual, relative), "utf8");
    return expectedContent === actualContent ? [] : [`Changed generated file: ${relative}`];
  });
}

function files(root, prefix = "") {
  return readdirSync(path.join(root, prefix), { withFileTypes: true }).flatMap((entry) => {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory()) return files(root, relative);
    return [relative];
  }).sort();
}
