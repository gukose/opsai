import type { JsonStorage } from "../storage/scopedAppStorage";
import type { CachedEnvelope, CacheRestoreResult, OfflineScope } from "./offlineTypes";
import type { TaskFilterState } from "../tasks/types";

const PREFIX = "hotel-opai.offline";
export const OFFLINE_CACHE_VERSION = "v1";

export class OfflineCache {
  private readonly storage: JsonStorage;

  constructor(storage: JsonStorage = createDefaultStorage()) {
    this.storage = storage;
  }

  async load<T>(key: string): Promise<CacheRestoreResult<T> | null> {
    try {
      const raw = await this.storage.getItem(key);
      if (!raw) {
        return null;
      }

      const parsed = JSON.parse(raw) as CachedEnvelope<T>;
      if (!parsed || parsed.version !== OFFLINE_CACHE_VERSION || typeof parsed.cachedAt !== "string") {
        return null;
      }

      return { data: parsed.data, cachedAt: parsed.cachedAt };
    } catch {
      return null;
    }
  }

  async save<T>(key: string, data: T, now: Date = new Date()): Promise<void> {
    try {
      const envelope: CachedEnvelope<T> = {
        version: OFFLINE_CACHE_VERSION,
        cachedAt: now.toISOString(),
        data
      };
      await this.storage.setItem(key, JSON.stringify(envelope));
    } catch {
      // Offline cache is best effort.
    }
  }

  async remove(key: string): Promise<void> {
    try {
      await this.storage.removeItem(key);
    } catch {
      // Offline cache is best effort.
    }
  }

  async clearScope(scope: OfflineScope): Promise<void> {
    try {
      const prefix = `${PREFIX}:${OFFLINE_CACHE_VERSION}:${scope.hotelId}:${scope.userId}:`;
      const keys = await this.storage.keys();
      await Promise.all(keys.filter((key) => key.startsWith(prefix)).map((key) => this.remove(key)));
    } catch {
      // Offline cache is best effort.
    }
  }

  async removeByPrefix(prefix: string): Promise<void> {
    try {
      const keys = await this.storage.keys();
      await Promise.all(keys.filter((key) => key.startsWith(prefix)).map((key) => this.remove(key)));
    } catch {
      // Offline cache is best effort.
    }
  }
}

const memory = new Map<string, string>();

function createDefaultStorage(): JsonStorage {
  if (typeof globalThis.localStorage !== "undefined") {
    return {
      async getItem(key: string) {
        return globalThis.localStorage?.getItem(key) ?? null;
      },
      async setItem(key: string, value: string) {
        globalThis.localStorage?.setItem(key, value);
      },
      async removeItem(key: string) {
        globalThis.localStorage?.removeItem(key);
      },
      async keys() {
        const storage = globalThis.localStorage;
        if (!storage) {
          return [];
        }
        return Array.from({ length: storage.length })
          .map((_, index) => storage.key(index))
          .filter((key): key is string => typeof key === "string");
      }
    };
  }

  return {
    async getItem(key: string) {
      return memory.get(key) ?? null;
    },
    async setItem(key: string, value: string) {
      memory.set(key, value);
    },
    async removeItem(key: string) {
      memory.delete(key);
    },
    async keys() {
      return Array.from(memory.keys());
    }
  };
}

export const defaultOfflineCache = new OfflineCache();

export function taskListCacheKey(scope: OfflineScope, filters?: TaskFilterState): string {
  return scopedKey(scope, "tasks", stableStringify(normalizedTaskFilters(filters)));
}

export function dashboardCacheKey(scope: OfflineScope, range: "today" | "shift" | "7d" = "today"): string {
  return scopedKey(scope, "dashboard", range);
}

export function assistantDraftCacheKey(scope: OfflineScope, conversationId?: string | null): string {
  return scopedKey(scope, "assistant-draft", conversationId?.trim() || "new");
}

export function assistantDraftCacheKeyPrefix(scope: OfflineScope): string {
  return `${PREFIX}:${OFFLINE_CACHE_VERSION}:${scope.hotelId}:${scope.userId}:assistant-draft:`;
}

function scopedKey(scope: OfflineScope, kind: string, identity: string): string {
  return `${PREFIX}:${OFFLINE_CACHE_VERSION}:${scope.hotelId}:${scope.userId}:${kind}:${identity}`;
}

function normalizedTaskFilters(filters?: TaskFilterState) {
  return {
    q: filters?.q?.trim() || "",
    status: [...(filters?.status ?? [])].sort(),
    priority: [...(filters?.priority ?? [])].sort(),
    assignment: filters?.assignment ?? null,
    createdFrom: filters?.createdFrom ?? null,
    createdTo: filters?.createdTo ?? null,
    page: filters?.page ?? null,
    size: filters?.size ?? null
  };
}

function stableStringify(value: unknown): string {
  return JSON.stringify(value);
}
