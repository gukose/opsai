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
  globalThis.fetch = async (url) => {
    calls.push(String(url));
    return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
  };

  try {
    const service = new SessionService(store);
    const restored = await service.restoreSession();

    assert.equal(restored, null);
    assert.equal(store.cleared, true);
    assert.equal(calls.some((url) => url.endsWith("/api/v1/auth/me")), true);
    assert.equal(calls.some((url) => url.endsWith("/api/v1/auth/refresh")), true);
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
