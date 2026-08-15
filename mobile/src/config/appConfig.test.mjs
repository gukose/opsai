import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

import { resolveApiBaseUrlForEnvironment, shouldShowDemoIndicator } from "./appConfig.ts";

test("demo requires an explicit HTTPS backend and never falls back to localhost", () => {
  assert.throws(() => resolveApiBaseUrlForEnvironment("demo", undefined, "ios"), /EXPO_PUBLIC_API_BASE_URL/);
  assert.equal(resolveApiBaseUrlForEnvironment("demo", "https://hotel-opai.up.railway.app/", "android"), "https://hotel-opai.up.railway.app");
});

test("DEMO indicator is isolated from production", () => {
  assert.equal(shouldShowDemoIndicator("demo"), true);
  assert.equal(shouldShowDemoIndicator("prod"), false);
});

test("mobile configuration contains no backend provider secret", () => {
  const sources = ["src/config/appConfig.ts", "app.json", "eas.json"].map((path) => fs.readFileSync(path, "utf8")).join("\n");
  assert.doesNotMatch(sources, /OPENAI_API_KEY|SUPABASE_DB_PASSWORD|OPS_AI_AUTH_JWT_SECRET/);
});
