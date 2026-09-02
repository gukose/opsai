import assert from "node:assert/strict";
import test from "node:test";
import { resolveExperienceMode } from "./experienceMode.ts";

const user = (permissions) => ({ userId: "u", hotelId: "h", permissions: permissions.map((code) => ({ permissionId: code, code, name: code })) });

test("frontline capabilities resolve to simple frontline mode", () => {
  assert.equal(resolveExperienceMode(user(["TASK_READ"])), "FRONTLINE_SIMPLE");
  assert.equal(resolveExperienceMode(user(["ROOM_CLEANING", "TASK_READ"])), "FRONTLINE_SIMPLE");
});

test("team assignment or inspection capabilities resolve to supervisor mode", () => {
  assert.equal(resolveExperienceMode(user(["TASK_ASSIGN"])), "SUPERVISOR");
  assert.equal(resolveExperienceMode(user(["HOUSEKEEPING_INSPECTION"])), "SUPERVISOR");
});

test("operational supervisor is not promoted to manager", () => {
  assert.equal(resolveExperienceMode(user(["TASK_ASSIGN", "HOUSEKEEPING_INSPECTION", "HOUSEKEEPING_OPERATIONS"])), "SUPERVISOR");
});

test("hotel-wide capability wins when capabilities overlap", () => {
  assert.equal(resolveExperienceMode(user(["TASK_ASSIGN", "MANAGER_REPORTING"])), "MANAGER");
  assert.equal(resolveExperienceMode(user(["DASHBOARD_READ", "REPORT_READ", "TASK_ASSIGN"])), "MANAGER");
});
