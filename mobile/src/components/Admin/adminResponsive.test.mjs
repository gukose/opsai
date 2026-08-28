import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
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

test("Administration components retain narrow-screen overflow protections", () => {
  const screen = readFileSync(new URL("./AdministrationScreen.tsx", import.meta.url), "utf8");
  const ui = readFileSync(new URL("./AdminUi.tsx", import.meta.url), "utf8");
  const onboarding = readFileSync(new URL("./HotelOnboardingWizard.tsx", import.meta.url), "utf8");
  const employee = readFileSync(new URL("./EmployeeEditor.tsx", import.meta.url), "utf8");
  const role = readFileSync(new URL("./RolePermissionEditor.tsx", import.meta.url), "utf8");
  const csv = readFileSync(new URL("./RoomCsvImportPanel.tsx", import.meta.url), "utf8");

  assert.ok((screen.match(/horizontal/g) ?? []).length >= 2, "hotel and section selectors scroll horizontally");
  assert.match(ui, /width: "100%"/);
  assert.match(ui, /KeyboardAvoidingView/);
  assert.match(onboarding, /keyboardShouldPersistTaps="handled"/);
  assert.match(employee, /style=\{styles\.scroll\}/);
  assert.match(role, /style=\{styles\.scroll\}/);
  assert.match(csv, /maxHeight: 120/);
});

test("Administration hides database details behind a business error", () => {
  const screen = readFileSync(new URL("./AdministrationScreen.tsx", import.meta.url), "utf8");
  assert.match(screen, /This administration data could not be loaded/);
});
