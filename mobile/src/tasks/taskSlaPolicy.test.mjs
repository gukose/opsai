import assert from "node:assert/strict";
import test from "node:test";

import { calculateSlaRemainingSeconds, formatDurationShort } from "./taskSlaPolicy.ts";

test("SLA remaining is calculated in seconds and clamped", () => {
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 0)), "30:00");
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 1)), "29:59");
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 768)), "17:12");
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 1799)), "00:01");
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 1800)), "00:00");
  assert.equal(formatDurationShort(calculateSlaRemainingSeconds(1800, 2000)), "00:00");
});
