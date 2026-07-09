import { Platform } from "react-native";

import { AppSessionSnapshot } from "./sessionTypes";
import { SessionStore } from "./SessionStore";

const SESSION_STORAGE_KEY = "hotel-opai.session.v1";

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
  private readonly fallback = new Map<string, string>();

  async load(): Promise<AppSessionSnapshot | null> {
    const raw = await this.readRaw();
    return raw ? parseSnapshot(raw) : null;
  }

  async save(session: AppSessionSnapshot): Promise<void> {
    const raw = JSON.stringify(session);
    await this.writeRaw(raw);
  }

  async clear(): Promise<void> {
    await this.deleteRaw();
  }

  private async readRaw(): Promise<string | null> {
    if (Platform.OS !== "web") {
      const SecureStore = await import("expo-secure-store");
      return SecureStore.getItemAsync(SESSION_STORAGE_KEY);
    }

    return this.fallback.get(SESSION_STORAGE_KEY) ?? null;
  }

  private async writeRaw(value: string): Promise<void> {
    if (Platform.OS !== "web") {
      const SecureStore = await import("expo-secure-store");
      await SecureStore.setItemAsync(SESSION_STORAGE_KEY, value);
      return;
    }

    this.fallback.set(SESSION_STORAGE_KEY, value);
  }

  private async deleteRaw(): Promise<void> {
    if (Platform.OS !== "web") {
      const SecureStore = await import("expo-secure-store");
      await SecureStore.deleteItemAsync(SESSION_STORAGE_KEY);
      return;
    }

    this.fallback.delete(SESSION_STORAGE_KEY);
  }
}

export function createSessionStore(): SessionStore {
  if (Platform.OS === "web") {
    return new WebSessionStore();
  }

  return new NativeSessionStore();
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
