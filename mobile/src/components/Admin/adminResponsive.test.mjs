import assert from "node:assert/strict";
import test from "node:test";
import { adminLayoutForWidth } from "./adminResponsive.ts";

test("Administration uses a phone layout without desktop-width assumptions", () => {
  const layout = adminLayoutForWidth(320);
  assert.equal(layout.phone, true);
  assert.equal(layout.contentPadding, "compact");
  assert.equal(layout.horizontalSectionNavigation, true);
  assert.equal(layout.modalScrollable, true);
});

test("Administration preserves responsive navigation on tablet and web", () => {
  assert.equal(adminLayoutForWidth(768).phone, false);
  assert.equal(adminLayoutForWidth(1280).horizontalSectionNavigation, true);
});
