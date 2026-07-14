export const OFFLINE_CACHE_VERSION = "v1";

export type OfflineScope = {
  hotelId: string;
  userId: string;
};

export type CachedEnvelope<T> = {
  version: typeof OFFLINE_CACHE_VERSION;
  cachedAt: string;
  data: T;
};

export type CacheRestoreResult<T> = {
  data: T;
  cachedAt: string;
};

export type StaleState = {
  isStale: boolean;
  staleReason: string | null;
  cachedAt: string | null;
};
