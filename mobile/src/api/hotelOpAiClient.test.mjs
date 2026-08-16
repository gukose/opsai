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

const { AuthController_login, DashboardController_summary } = await import("@hotelopai/api-client");
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

test("timeout maps to AppApiError when DOMException is unavailable", async () => {
  await withoutDomException(async () => {
    const client = new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      timeoutMs: 1,
      delay: async () => undefined
    });

    await assert.rejects(
      () => client.call(
        "GET",
        async (_sdk, signal) => new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () => {
            const error = new Error("Request aborted");
            error.name = "AbortError";
            reject(error);
          }, { once: true });
        }),
        { retry: { maxRetries: 0, delaysMs: [] } }
      ),
      (error) => error instanceof AppApiError && error.kind === "timeout"
    );
  });
});

test("login transport works in a React Native environment without DOMException", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    tokenType: "Bearer",
    accessToken: "access-token",
    accessTokenExpiresAt: "2026-08-16T12:00:00Z",
    refreshToken: "refresh-token",
    refreshTokenExpiresAt: "2026-09-15T12:00:00Z",
    user: {
      userId: "user-1",
      hotelId: "hotel-1",
      email: "admin@example.test",
      displayName: "Admin",
      hotelName: "Demo Hotel",
      roles: [],
      permissions: []
    }
  }), { status: 200, headers: { "Content-Type": "application/json" } });

  try {
    await withoutDomException(async () => {
      const client = new MobileHotelOpAiClient({ baseUrl: "http://localhost:8080" });
      const response = await client.call("POST", (sdk, signal) => AuthController_login(sdk, {
        body: { hotelCode: "demo", email: "admin@example.test", password: "valid-password" },
        signal
      }), { authenticated: false });

      assert.equal(response.accessToken, "access-token");
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

async function withoutDomException(operation) {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "DOMException");
  Object.defineProperty(globalThis, "DOMException", {
    configurable: true,
    writable: true,
    value: undefined
  });
  try {
    return await operation();
  } finally {
    if (descriptor) {
      Object.defineProperty(globalThis, "DOMException", descriptor);
    } else {
      delete globalThis.DOMException;
    }
  }
}
