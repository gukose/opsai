import { appApiBaseUrl } from "../config/appConfig";
import { AppApiError } from "../api/client/AppApiError";
import { HttpAuthApi } from "../api/auth/AuthApi";
import { mapCurrentUserResponseToSnapshot } from "../api/auth/AuthDtos";
import { MobileHotelOpAiClient } from "../api/hotelOpAiClient";
import type { CurrentUserResponseDto, LoginRequestDto } from "../api/auth/AuthDtos";
import type { AppSessionSnapshot } from "../session/sessionTypes";
import type { SessionStore } from "../session/SessionStore";
import { mapAuthSessionResponseToSnapshot } from "./authSessionMapper";

export type LoginCredentials = Pick<LoginRequestDto, "hotelCode" | "email" | "password">;

export class SessionService {
  private currentSession: AppSessionSnapshot | null = null;
  private readonly authApi: HttpAuthApi;
  private readonly sessionStore: SessionStore;

  constructor(sessionStore: SessionStore) {
    this.sessionStore = sessionStore;
    const apiClient = new MobileHotelOpAiClient({
      baseUrl: appApiBaseUrl,
      accessTokenProvider: () => this.currentSession?.accessToken ?? null
    });

    this.authApi = new HttpAuthApi(apiClient);
  }

  async restoreSession(): Promise<AppSessionSnapshot | null> {
    const storedSession = await this.sessionStore.load();
    if (!storedSession) {
      this.currentSession = null;
      return null;
    }

    this.currentSession = storedSession;

    try {
      const currentUser = await this.fetchCurrentUser();
      const nextSession = this.mergeCurrentUser(storedSession, currentUser);
      await this.saveSession(nextSession);
      return nextSession;
    } catch (error) {
      if (isTransportFailure(error)) {
        return storedSession;
      }

      if (!storedSession.refreshToken) {
        await this.clearInvalidSession();
        return null;
      }

      try {
        const refreshedSession = await this.refresh();
        if (!refreshedSession?.accessToken) {
          throw new Error("Refresh did not return an access token");
        }

        this.currentSession = refreshedSession;
        const refreshedCurrentUser = await this.fetchCurrentUser();
        const nextSession = this.mergeCurrentUser(refreshedSession, refreshedCurrentUser);
        await this.saveSession(nextSession);
        return nextSession;
      } catch (refreshError) {
        if (isTransportFailure(refreshError)) {
          return storedSession;
        }

        await this.clearInvalidSession();
        return null;
      }
    }
  }

  async login(credentials: LoginCredentials): Promise<AppSessionSnapshot> {
    const response = await this.authApi.login({
      ...credentials
    });

    let session = mapAuthSessionResponseToSnapshot(response);
    await this.saveSession(session);

    try {
      const currentUser = await this.fetchCurrentUser();
      session = this.mergeCurrentUser(session, currentUser);
      await this.saveSession(session);
    } catch {
      // Login may still succeed even if /me is temporarily unavailable.
    }

    return session;
  }

  async refresh(): Promise<AppSessionSnapshot | null> {
    const storedSession = this.currentSession ?? (await this.sessionStore.load());
    if (!storedSession?.refreshToken) {
      return null;
    }

    this.currentSession = storedSession;

    const response = await this.authApi.refresh({
      refreshToken: storedSession.refreshToken
    });

    const session = mapAuthSessionResponseToSnapshot(response);
    await this.saveSession(session);
    return session;
  }

  async logout(): Promise<void> {
    try {
      const hasSession = this.currentSession ?? (await this.sessionStore.load());
      if (hasSession) {
        this.currentSession = hasSession;
        await this.authApi.logout();
      }
    } finally {
      await this.clearInvalidSession();
    }
  }

  async getCurrentUser(): Promise<AppSessionSnapshot["currentUser"] | null> {
    return this.currentSession?.currentUser ?? (await this.sessionStore.load())?.currentUser ?? null;
  }

  async clearInvalidSession(): Promise<void> {
    this.currentSession = null;
    await this.sessionStore.clear();
  }

  getSession(): AppSessionSnapshot | null {
    return this.currentSession;
  }

  private async fetchCurrentUser(): Promise<CurrentUserResponseDto> {
    return this.authApi.me();
  }

  private mergeCurrentUser(
    session: AppSessionSnapshot,
    user: CurrentUserResponseDto
  ): AppSessionSnapshot {
    return {
      ...session,
      currentUser: mapCurrentUserResponseToSnapshot(user)
    };
  }

  private async saveSession(session: AppSessionSnapshot): Promise<void> {
    this.currentSession = session;
    await this.sessionStore.save(session);
  }
}

function isTransportFailure(error: unknown): boolean {
  return error instanceof AppApiError && (error.kind === "network" || error.kind === "timeout");
}
