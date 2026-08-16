import assert from "node:assert/strict";
import test from "node:test";

const { createNativeSessionStore } = await import("./createSessionStore.ts");

test("native session storage keeps tokens in bounded SecureStore entries", async () => {
  const values = new Map();
  const maximumValueLength = 2_048;
  const secureStore = {
    async getItemAsync(key) {
      return values.get(key) ?? null;
    },
    async setItemAsync(key, value) {
      if (value.length > maximumValueLength) {
        throw new Error("SecureStore value exceeds test limit");
      }
      values.set(key, value);
    },
    async deleteItemAsync(key) {
      values.delete(key);
    }
  };
  const store = createNativeSessionStore(async () => secureStore);

  await store.save({
    accessToken: "a".repeat(900),
    accessTokenExpiresAt: "2026-08-16T12:00:00Z",
    refreshToken: "r".repeat(900),
    refreshTokenExpiresAt: "2026-09-15T12:00:00Z",
    tokenType: "Bearer",
    currentUser: {
      userId: "user-1",
      hotelId: "hotel-1",
      employeeId: null,
      email: "admin@hotelopai.local",
      displayName: "Demo Admin",
      hotelName: "Hotel OpAI Demo",
      roles: [],
      permissions: Array.from({ length: 100 }, (_, index) => ({
        permissionId: `permission-${index}`,
        code: `PERMISSION_${index}`,
        name: `Permission ${index}`
      }))
    }
  });

  assert.equal([...values.values()].every((value) => value.length <= maximumValueLength), true);
  const restored = await store.load();
  assert.equal(restored?.accessToken?.length, 900);
  assert.equal(restored?.refreshToken?.length, 900);
  assert.equal(restored?.currentUser, null);

  await store.clear();
  assert.equal(values.size, 0);
});
