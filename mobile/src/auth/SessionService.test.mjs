import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === "react-native") {
      return {
        url: "data:text/javascript,export const Platform = { OS: 'web' };",
        shortCircuit: true
      };
    }
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

const { SessionService } = await import("./SessionService.ts");

test("session restore keeps stored session on backend transport failure", async () => {
  const originalFetch = globalThis.fetch;
  const store = createMemorySessionStore(sessionSnapshot());
  globalThis.fetch = async () => {
    throw new TypeError("Failed to fetch");
  };

  try {
    const service = new SessionService(store);
    const restored = await service.restoreSession();

    assert.deepEqual(restored, sessionSnapshot());
    assert.equal(store.cleared, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("session restore clears stale stored session when auth and refresh are rejected", async () => {
  const originalFetch = globalThis.fetch;
  const store = createMemorySessionStore(sessionSnapshot());
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({
      url: String(url),
      authorization: new Headers(init?.headers).get("Authorization")
    });
    return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
  };

  try {
    const service = new SessionService(store);
    const restored = await service.restoreSession();

    assert.equal(restored, null);
    assert.equal(store.cleared, true);
    assert.equal(calls.some((call) => call.url.endsWith("/api/v1/auth/me")), true);
    assert.equal(calls.some((call) => call.url.endsWith("/api/v1/auth/refresh")), true);
    assert.equal(
      calls.find((call) => call.url.endsWith("/api/v1/auth/me"))?.authorization,
      "Bearer stored-access-token"
    );
    assert.equal(
      calls.find((call) => call.url.endsWith("/api/v1/auth/refresh"))?.authorization,
      null
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("valid login persists tokens, authenticates me, survives reload, and logout clears session", async () => {
  const originalFetch = globalThis.fetch;
  const store = createMemorySessionStore(null);
  const calls = [];
  globalThis.fetch = async (url, init) => {
    const path = new URL(String(url)).pathname;
    const authorization = new Headers(init?.headers).get("Authorization");
    calls.push({ path, authorization });

    if (path === "/api/v1/auth/login") {
      return jsonResponse(authSessionResponse());
    }
    if (path === "/api/v1/auth/me") {
      return authorization === "Bearer access-token"
        ? jsonResponse(currentUserResponse())
        : jsonResponse({ title: "Unauthorized", status: 401 }, 401);
    }
    if (path === "/api/v1/auth/logout") {
      return authorization === "Bearer access-token"
        ? jsonResponse({ status: "logged_out" })
        : jsonResponse({ title: "Unauthorized", status: 401 }, 401);
    }
    throw new Error(`Unexpected request: ${path}`);
  };

  try {
    const service = new SessionService(store);
    const loggedIn = await service.login({
      hotelCode: "hotel-opai-demo",
      email: "admin@hotelopai.local",
      password: "valid-password"
    });

    assert.equal(loggedIn.accessToken, "access-token");
    assert.equal(loggedIn.refreshToken, "refresh-token");
    assert.equal(loggedIn.currentUser?.userId, "user-1");
    assert.equal(
      calls.find((call) => call.path === "/api/v1/auth/me")?.authorization,
      "Bearer access-token"
    );

    const restored = await new SessionService(store).restoreSession();
    assert.equal(restored?.accessToken, "access-token");
    assert.equal(restored?.currentUser?.userId, "user-1");

    await service.logout();
    assert.equal(store.value, null);
    assert.equal(store.cleared, true);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("invalid login is rejected without persisting a session", async () => {
  const originalFetch = globalThis.fetch;
  const store = createMemorySessionStore(null);
  globalThis.fetch = async () =>
    jsonResponse({ title: "Invalid credentials", status: 401 }, 401);

  try {
    const service = new SessionService(store);
    await assert.rejects(() => service.login({
      hotelCode: "hotel-opai-demo",
      email: "admin@hotelopai.local",
      password: "invalid-password"
    }));
    assert.equal(service.getSession(), null);
    assert.equal(store.value, null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

function createMemorySessionStore(initialSession) {
  return {
    value: initialSession,
    cleared: false,
    async load() {
      return this.value;
    },
    async save(session) {
      this.value = session;
      this.cleared = false;
    },
    async clear() {
      this.value = null;
      this.cleared = true;
    }
  };
}

function sessionSnapshot() {
  return {
    accessToken: "stored-access-token",
    accessTokenExpiresAt: "2026-07-15T12:00:00Z",
    refreshToken: "stored-refresh-token",
    refreshTokenExpiresAt: "2026-08-14T12:00:00Z",
    tokenType: "Bearer",
    currentUser: {
      userId: "user-1",
      hotelId: "hotel-1",
      displayName: "Stored User"
    }
  };
}

function authSessionResponse() {
  return {
    tokenType: "Bearer",
    accessToken: "access-token",
    accessTokenExpiresAt: "2026-08-16T12:00:00Z",
    refreshToken: "refresh-token",
    refreshTokenExpiresAt: "2026-09-15T12:00:00Z",
    user: currentUserResponse()
  };
}

function currentUserResponse() {
  return {
    userId: "user-1",
    hotelId: "hotel-1",
    employeeId: null,
    email: "admin@hotelopai.local",
    displayName: "Demo Admin",
    hotelName: "Hotel OpAI Demo",
    roles: [],
    permissions: []
  };
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "X-API-Version": "v1" }
  });
}
