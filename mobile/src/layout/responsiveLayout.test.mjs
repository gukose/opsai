import test from "node:test";
import assert from "node:assert/strict";

import { resolveResponsiveLayout } from "./responsiveLayout.ts";

test("required acceptance widths resolve to phone tablet and desktop shells", () => {
  for (const width of [375, 390, 430]) {
    assert.deepEqual(resolveResponsiveLayout(width), { mode: "phone", shellMaxWidth: width, contentGutter: 0 });
  }
  for (const width of [768, 1024]) {
    assert.deepEqual(resolveResponsiveLayout(width), { mode: "tablet", shellMaxWidth: width, contentGutter: 12 });
  }
  assert.deepEqual(resolveResponsiveLayout(1440), { mode: "desktop", shellMaxWidth: 1360, contentGutter: 24 });
});

test("breakpoints have no unclassified width", () => {
  assert.equal(resolveResponsiveLayout(599).mode, "phone");
  assert.equal(resolveResponsiveLayout(600).mode, "tablet");
  assert.equal(resolveResponsiveLayout(1024).mode, "tablet");
  assert.equal(resolveResponsiveLayout(1025).mode, "desktop");
});
