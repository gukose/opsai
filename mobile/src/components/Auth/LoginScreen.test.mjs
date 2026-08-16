import assert from "node:assert/strict";
import test from "node:test";

import { loginDefaults } from "./loginDefaults.ts";

test("demo pre-fills the Railway reviewer account", () => {
  assert.deepEqual(loginDefaults("demo"), {
    email: "reviewer.admin@demo.hotelopai.app",
    password: "demo12345678"
  });
});

test("production never pre-fills credentials", () => {
  assert.deepEqual(loginDefaults("prod"), { email: "", password: "" });
});
