export type JsonStorage = {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
  keys(): Promise<string[]>;
};

const memory = new Map<string, string>();

class ScopedLocalStorage implements JsonStorage {
  async getItem(key: string): Promise<string | null> {
    return globalThis.localStorage?.getItem(key) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    globalThis.localStorage?.setItem(key, value);
  }

  async removeItem(key: string): Promise<void> {
    globalThis.localStorage?.removeItem(key);
  }

  async keys(): Promise<string[]> {
    const storage = globalThis.localStorage;
    if (!storage) {
      return [];
    }

    return Array.from({ length: storage.length })
      .map((_, index) => storage.key(index))
      .filter((key): key is string => typeof key === "string");
  }
}

class MemoryJsonStorage implements JsonStorage {
  async getItem(key: string): Promise<string | null> {
    return memory.get(key) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    memory.set(key, value);
  }

  async removeItem(key: string): Promise<void> {
    memory.delete(key);
  }

  async keys(): Promise<string[]> {
    return Array.from(memory.keys());
  }
}

export function createScopedAppStorage(): JsonStorage {
  if (typeof globalThis.localStorage !== "undefined") {
    return new ScopedLocalStorage();
  }

  return new MemoryJsonStorage();
}
