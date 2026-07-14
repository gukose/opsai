import assert from "node:assert/strict";
import test from "node:test";

import { OfflineCache, assistantDraftCacheKey, dashboardCacheKey, taskListCacheKey } from "./offlineCache.ts";

class FakeStorage {
  constructor() {
    this.values = new Map();
    this.failRead = false;
    this.failWrite = false;
    this.failRemove = false;
  }

  async getItem(key) {
    if (this.failRead) throw new Error("read failed");
    return this.values.get(key) ?? null;
  }

  async setItem(key, value) {
    if (this.failWrite) throw new Error("write failed");
    this.values.set(key, value);
  }

  async removeItem(key) {
    if (this.failRemove) throw new Error("remove failed");
    this.values.delete(key);
  }

  async keys() {
    return Array.from(this.values.keys());
  }
}

const scope = { hotelId: "hotel-a", userId: "user-a" };

test("task cache keys include scope and distinguish filters deterministically", () => {
  const unfiltered = taskListCacheKey(scope);
  const filteredA = taskListCacheKey(scope, {
    q: "sink",
    status: ["ASSIGNED", "CREATED"],
    priority: ["URGENT", "HIGH"],
    assignment: "mine",
    createdFrom: "2026-07-01T00:00:00Z",
    createdTo: "2026-07-02T00:00:00Z",
    page: 0,
    size: 20
  });
  const filteredB = taskListCacheKey(scope, {
    q: "sink",
    status: ["CREATED", "ASSIGNED"],
    priority: ["HIGH", "URGENT"],
    assignment: "mine",
    createdFrom: "2026-07-01T00:00:00Z",
    createdTo: "2026-07-02T00:00:00Z",
    page: 0,
    size: 20
  });

  assert.match(unfiltered, /v1:hotel-a:user-a:tasks/);
  assert.notEqual(unfiltered, filteredA);
  assert.equal(filteredA, filteredB);
  assert.notEqual(filteredA, taskListCacheKey({ hotelId: "hotel-b", userId: "user-a" }));
  assert.notEqual(filteredA, taskListCacheKey({ hotelId: "hotel-a", userId: "user-b" }));
});

test("dashboard and draft cache keys distinguish range and conversation", () => {
  assert.notEqual(dashboardCacheKey(scope, "today"), dashboardCacheKey(scope, "shift"));
  assert.notEqual(assistantDraftCacheKey(scope, "conversation-a"), assistantDraftCacheKey(scope, "conversation-b"));
});

test("offline cache round-trips data and ignores malformed or unsupported data safely", async () => {
  const storage = new FakeStorage();
  const cache = new OfflineCache(storage);
  const key = taskListCacheKey(scope);

  await cache.save(key, [{ id: "task-1" }], new Date("2026-07-14T10:00:00Z"));
  assert.deepEqual(await cache.load(key), {
    data: [{ id: "task-1" }],
    cachedAt: "2026-07-14T10:00:00.000Z"
  });

  await storage.setItem("bad-json", "{");
  assert.equal(await cache.load("bad-json"), null);

  await storage.setItem("old-version", JSON.stringify({ version: "v0", cachedAt: "x", data: {} }));
  assert.equal(await cache.load("old-version"), null);
});

test("storage failures do not crash cache operations and clearScope removes only matching scope", async () => {
  const storage = new FakeStorage();
  const cache = new OfflineCache(storage);
  const keyA = taskListCacheKey(scope);
  const keyB = taskListCacheKey({ hotelId: "hotel-b", userId: "user-a" });
  await cache.save(keyA, [{ id: "a" }]);
  await cache.save(keyB, [{ id: "b" }]);

  await cache.clearScope(scope);
  assert.equal(await cache.load(keyA), null);
  assert.deepEqual((await cache.load(keyB))?.data, [{ id: "b" }]);

  storage.failRead = true;
  assert.equal(await cache.load(keyB), null);
  storage.failRead = false;
  storage.failWrite = true;
  await cache.save(keyA, [{ id: "a" }]);
  storage.failWrite = false;
  storage.failRemove = true;
  await cache.remove(keyB);
});
