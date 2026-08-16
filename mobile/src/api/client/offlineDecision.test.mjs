import assert from "node:assert/strict";
import test from "node:test";
import { AppApiError, isConnectivityFailure } from "./AppApiError.ts";

test("HTTP 500 is not treated as offline", () => {
  assert.equal(isConnectivityFailure(new AppApiError("server error", { kind: "problem-details", status: 500 })), false);
});

test("HTTP 401 is not treated as offline", () => {
  assert.equal(isConnectivityFailure(new AppApiError("unauthorized", { kind: "problem-details", status: 401 })), false);
});

test("transport failures are treated as connectivity failures", () => {
  assert.equal(isConnectivityFailure(new AppApiError("unreachable", { kind: "network" })), true);
  assert.equal(isConnectivityFailure(new AppApiError("timed out", { kind: "timeout" })), true);
});
