import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

registerHooks({
  resolve(specifier, context, nextResolve) {
    try {
      return nextResolve(specifier, context);
    } catch (error) {
      if (specifier.startsWith(".") && !specifier.match(/\.[cm]?[jt]sx?$/)) {
        return nextResolve(`${specifier}.ts`, context);
      }
      throw error;
    }
  }
});

const { DashboardController_summary } = await import("@hotelopai/api-client");
const { AppApiError } = await import("./client/AppApiError.ts");
const { MobileHotelOpAiClient } = await import("./hotelOpAiClient.ts");

test("GET 401 refreshes once and retries with the returned access token", async () => {
  const requests = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (_url, init) => {
    const headers = new Headers(init?.headers);
    requests.push({
      authorization: headers.get("Authorization"),
      correlationId: headers.get("X-Correlation-Id")
    });
    if (requests.length === 1) {
      return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
    }
    return new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { "Content-Type": "application/json", "X-API-Version": "v1" }
    });
  };

  try {
    let refreshCalls = 0;
    const client = new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "expired-token",
      refreshAccessToken: async () => {
        refreshCalls += 1;
        return "fresh-token";
      },
      correlationIdProvider: () => "test-correlation",
      delay: async () => undefined
    });

    const response = await client.call("GET", (sdk, signal) =>
      DashboardController_summary(sdk, { query: { range: "today" }, signal })
    );

    assert.deepEqual(response, { ok: true });
    assert.equal(refreshCalls, 1);
    assert.equal(requests.length, 2);
    assert.equal(requests[0].authorization, "Bearer expired-token");
    assert.equal(requests[1].authorization, "Bearer fresh-token");
    assert.equal(requests[0].correlationId, "test-correlation");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("GET 401 does not enter a refresh loop when refresh fails", async () => {
  const originalFetch = globalThis.fetch;
  let requestCount = 0;
  globalThis.fetch = async () => {
    requestCount += 1;
    return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
  };

  try {
    let refreshCalls = 0;
    const client = new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "expired-token",
      refreshAccessToken: async () => {
        refreshCalls += 1;
        return null;
      },
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.call("GET", (sdk, signal) =>
        DashboardController_summary(sdk, { query: { range: "today" }, signal })
      ),
      (error) => error instanceof AppApiError && error.status === 401
    );

    assert.equal(refreshCalls, 1);
    assert.equal(requestCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("ProblemDetail responses remain structured with API version metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        type: "https://example.test/problems/forbidden",
        title: "Forbidden",
        status: 403,
        detail: "Missing permission",
        instance: "/api/v1/dashboard/summary"
      }),
      {
        status: 403,
        headers: {
          "Content-Type": "application/problem+json",
          "X-API-Version": "v1"
        }
      }
    );

  try {
    const client = new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "token",
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.call("GET", (sdk, signal) =>
        DashboardController_summary(sdk, { query: { range: "today" }, signal })
      ),
      (error) =>
        error instanceof AppApiError &&
        error.status === 403 &&
        error.problem?.title === "Forbidden" &&
        error.problem?.detail === "Missing permission" &&
        error.apiVersion === "v1"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("connection refusal is classified as network transport failure", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("Failed to fetch");
  };

  try {
    const client = new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "token",
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.call(
        "GET",
        (sdk, signal) => DashboardController_summary(sdk, { query: { range: "today" }, signal }),
        { retry: { maxRetries: 0, delaysMs: [] } }
      ),
      (error) => error instanceof AppApiError && error.kind === "network" && error.status === undefined
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
