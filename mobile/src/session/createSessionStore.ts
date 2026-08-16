import { Platform } from "react-native";

import type { AppSessionSnapshot } from "./sessionTypes";
import type { SessionStore } from "./SessionStore";

const SESSION_STORAGE_KEY = "hotel-opai.session.v1";
const ACCESS_TOKEN_STORAGE_KEY = "hotel-opai.session.access-token.v1";
const REFRESH_TOKEN_STORAGE_KEY = "hotel-opai.session.refresh-token.v1";
const SESSION_METADATA_STORAGE_KEY = "hotel-opai.session.metadata.v1";

type SecureStoreModule = {
  getItemAsync(key: string): Promise<string | null>;
  setItemAsync(key: string, value: string): Promise<void>;
  deleteItemAsync(key: string): Promise<void>;
};

class WebSessionStore implements SessionStore {
  async load(): Promise<AppSessionSnapshot | null> {
    const raw = globalThis.localStorage?.getItem(SESSION_STORAGE_KEY);
    return raw ? parseSnapshot(raw) : null;
  }

  async save(session: AppSessionSnapshot): Promise<void> {
    globalThis.localStorage?.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  }

  async clear(): Promise<void> {
    globalThis.localStorage?.removeItem(SESSION_STORAGE_KEY);
  }
}

class NativeSessionStore implements SessionStore {
  private readonly loadSecureStore: () => Promise<SecureStoreModule>;

  constructor(loadSecureStore: () => Promise<SecureStoreModule>) {
    this.loadSecureStore = loadSecureStore;
  }

  async load(): Promise<AppSessionSnapshot | null> {
    const secureStore = await this.loadSecureStore();
    const [accessToken, refreshToken, metadataRaw] = await Promise.all([
      secureStore.getItemAsync(ACCESS_TOKEN_STORAGE_KEY),
      secureStore.getItemAsync(REFRESH_TOKEN_STORAGE_KEY),
      secureStore.getItemAsync(SESSION_METADATA_STORAGE_KEY)
    ]);

    if (accessToken || refreshToken) {
      const metadata = parseSessionMetadata(metadataRaw);
      return {
        accessToken,
        accessTokenExpiresAt: metadata.accessTokenExpiresAt,
        refreshToken,
        refreshTokenExpiresAt: metadata.refreshTokenExpiresAt,
        tokenType: metadata.tokenType,
        // User details are server-derived and refreshed through /auth/me.
        currentUser: null
      };
    }

    const legacyRaw = await secureStore.getItemAsync(SESSION_STORAGE_KEY);
    return legacyRaw ? parseSnapshot(legacyRaw) : null;
  }

  async save(session: AppSessionSnapshot): Promise<void> {
    const secureStore = await this.loadSecureStore();
    const metadata = JSON.stringify({
      accessTokenExpiresAt: session.accessTokenExpiresAt ?? null,
      refreshTokenExpiresAt: session.refreshTokenExpiresAt ?? null,
      tokenType: session.tokenType ?? null
    });

    await Promise.all([
      writeOptionalSecureValue(secureStore, ACCESS_TOKEN_STORAGE_KEY, session.accessToken),
      writeOptionalSecureValue(secureStore, REFRESH_TOKEN_STORAGE_KEY, session.refreshToken),
      secureStore.setItemAsync(SESSION_METADATA_STORAGE_KEY, metadata)
    ]);
    await secureStore.deleteItemAsync(SESSION_STORAGE_KEY);
  }

  async clear(): Promise<void> {
    const secureStore = await this.loadSecureStore();
    await Promise.all([
      secureStore.deleteItemAsync(ACCESS_TOKEN_STORAGE_KEY),
      secureStore.deleteItemAsync(REFRESH_TOKEN_STORAGE_KEY),
      secureStore.deleteItemAsync(SESSION_METADATA_STORAGE_KEY),
      secureStore.deleteItemAsync(SESSION_STORAGE_KEY)
    ]);
  }
}

export function createSessionStore(): SessionStore {
  if (Platform.OS === "web") {
    return new WebSessionStore();
  }

  return createNativeSessionStore();
}

export function createNativeSessionStore(
  loadSecureStore: () => Promise<SecureStoreModule> = () => import("expo-secure-store")
): SessionStore {
  return new NativeSessionStore(loadSecureStore);
}

async function writeOptionalSecureValue(
  secureStore: SecureStoreModule,
  key: string,
  value: string | null
): Promise<void> {
  if (value) {
    await secureStore.setItemAsync(key, value);
    return;
  }
  await secureStore.deleteItemAsync(key);
}

function parseSessionMetadata(raw: string | null): Pick<
  AppSessionSnapshot,
  "accessTokenExpiresAt" | "refreshTokenExpiresAt" | "tokenType"
> {
  try {
    const parsed = raw ? JSON.parse(raw) as Record<string, unknown> : {};
    return {
      accessTokenExpiresAt:
        typeof parsed.accessTokenExpiresAt === "string" ? parsed.accessTokenExpiresAt : null,
      refreshTokenExpiresAt:
        typeof parsed.refreshTokenExpiresAt === "string" ? parsed.refreshTokenExpiresAt : null,
      tokenType: typeof parsed.tokenType === "string" ? parsed.tokenType : null
    };
  } catch {
    return { accessTokenExpiresAt: null, refreshTokenExpiresAt: null, tokenType: null };
  }
}

function parseSnapshot(raw: string): AppSessionSnapshot | null {
  try {
    const parsed = JSON.parse(raw) as AppSessionSnapshot;
    if (typeof parsed !== "object" || parsed === null) {
      return null;
    }

    const currentUser = parsed.currentUser;

    return {
      accessToken: typeof parsed.accessToken === "string" ? parsed.accessToken : null,
      accessTokenExpiresAt:
        typeof parsed.accessTokenExpiresAt === "string" ? parsed.accessTokenExpiresAt : null,
      refreshToken: typeof parsed.refreshToken === "string" ? parsed.refreshToken : null,
      refreshTokenExpiresAt:
        typeof parsed.refreshTokenExpiresAt === "string" ? parsed.refreshTokenExpiresAt : null,
      tokenType: typeof parsed.tokenType === "string" ? parsed.tokenType : null,
      currentUser:
        currentUser && typeof currentUser === "object"
          ? {
              userId:
                typeof currentUser.userId === "string" ? currentUser.userId : "",
              hotelId:
                typeof currentUser.hotelId === "string" ? currentUser.hotelId : "",
              employeeId:
                typeof currentUser.employeeId === "string" || currentUser.employeeId === null
                  ? currentUser.employeeId
                  : null,
              email:
                typeof currentUser.email === "string" ? currentUser.email : null,
              displayName:
                typeof currentUser.displayName === "string"
                  ? currentUser.displayName
                  : null,
              hotelName:
                typeof currentUser.hotelName === "string" ? currentUser.hotelName : null,
              roles: Array.isArray(currentUser.roles)
                ? currentUser.roles
                    .filter((role) => role && typeof role === "object")
                    .map((role) => ({
                      roleId: typeof role.roleId === "string" ? role.roleId : "",
                      code: typeof role.code === "string" ? role.code : "",
                      name: typeof role.name === "string" ? role.name : ""
                    }))
                : [],
              permissions: Array.isArray(currentUser.permissions)
                ? currentUser.permissions
                    .filter((permission) => permission && typeof permission === "object")
                    .map((permission) => ({
                      permissionId:
                        typeof permission.permissionId === "string"
                          ? permission.permissionId
                          : "",
                      code: typeof permission.code === "string" ? permission.code : "",
                      name: typeof permission.name === "string" ? permission.name : ""
                    }))
                : []
            }
          : null
    };
  } catch {
    return null;
  }
}
