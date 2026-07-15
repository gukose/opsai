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

const { AppApiError } = await import("./AppApiError.ts");
const { FetchApiClient } = await import("./FetchApiClient.ts");

test("GET 401 refreshes once and retries with the returned access token", async () => {
  const requests = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (_url, init) => {
    requests.push(init?.headers ?? {});
    if (requests.length === 1) {
      return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  };

  try {
    let refreshCalls = 0;
    const client = new FetchApiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "expired-token",
      refreshAccessToken: async () => {
        refreshCalls += 1;
        return "fresh-token";
      },
      delay: async () => undefined
    });

    const response = await client.get("/api/v1/dashboard/summary?range=today");

    assert.deepEqual(response, { ok: true });
    assert.equal(refreshCalls, 1);
    assert.equal(requests.length, 2);
    assert.equal(requests[0].Authorization, "Bearer expired-token");
    assert.equal(requests[1].Authorization, "Bearer fresh-token");
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
    const client = new FetchApiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "expired-token",
      refreshAccessToken: async () => {
        refreshCalls += 1;
        return null;
      },
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.get("/api/v1/dashboard/summary?range=today"),
      (error) => error instanceof AppApiError && error.status === 401
    );

    assert.equal(refreshCalls, 1);
    assert.equal(requestCount, 1);
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
    const client = new FetchApiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "token",
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.get("/api/v1/dashboard/summary?range=today", { retry: { maxRetries: 0, delaysMs: [] } }),
      (error) => error instanceof AppApiError && error.kind === "network" && error.status === undefined
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
