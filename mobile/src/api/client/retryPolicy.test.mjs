import assert from "node:assert/strict";
import test from "node:test";

import { AppApiError } from "./AppApiError.ts";
import { retryDelayMs, shouldRetryRequest } from "./retryPolicy.ts";

const policy = { maxRetries: 2, delaysMs: [300, 900] };

test("GET transient failures retry within the configured limit", () => {
  const error = new AppApiError("Network request failed", { kind: "network" });

  assert.equal(shouldRetryRequest({ method: "GET", error, attempt: 1, policy }), true);
  assert.equal(shouldRetryRequest({ method: "GET", error, attempt: 2, policy }), true);
  assert.equal(shouldRetryRequest({ method: "GET", error, attempt: 3, policy }), false);
  assert.equal(retryDelayMs(1, policy), 300);
  assert.equal(retryDelayMs(2, policy), 900);
});

test("non GET methods and deterministic statuses are not retried", () => {
  const network = new AppApiError("Network request failed", { kind: "network" });
  const statuses = [400, 401, 403, 404, 409, 422, 429];

  for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    assert.equal(shouldRetryRequest({ method, error: network, attempt: 1, policy }), false);
  }

  for (const status of statuses) {
    const error = new AppApiError(`status ${status}`, { kind: "problem-details", status });
    assert.equal(shouldRetryRequest({ method: "GET", error, attempt: 1, policy }), false);
  }
});

test("zero retry policy disables automatic retry", () => {
  const error = new AppApiError("Request timed out", { kind: "timeout" });

  assert.equal(
    shouldRetryRequest({ method: "GET", error, attempt: 1, policy: { maxRetries: 0, delaysMs: [] } }),
    false
  );
});
