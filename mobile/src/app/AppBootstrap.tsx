import {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";

import { SessionService, LoginCredentials } from "../auth/SessionService";
import { createSessionStore } from "../session/createSessionStore";
import { AppSessionSnapshot, CurrentUserSnapshot } from "../session/sessionTypes";
import { defaultOfflineCache } from "../offline/offlineCache";
import { AppLaunchState, AppStateSnapshot } from "./appState";

type AppBootstrapContextValue = AppStateSnapshot & {
  currentUser: CurrentUserSnapshot | null;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
  clearSession: () => Promise<void>;
};

const AppBootstrapContext = createContext<AppBootstrapContextValue | null>(null);

const sessionStore = createSessionStore();
const sessionService = new SessionService(sessionStore);

export function AppBootstrapProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AppLaunchState>("unauthenticated");
  const [session, setSession] = useState<AppSessionSnapshot | null>(null);

  useEffect(() => {
    let active = true;
    let restoreCompleted = false;
    const restoreTimeoutMs = 8_000;
    let restoreTimeout: ReturnType<typeof setTimeout> | null = null;

    if (__DEV__) {
      console.debug("[AppBootstrap] restoreSession start");
    }

    restoreTimeout = setTimeout(() => {
      if (!active || restoreCompleted) {
        return;
      }

      restoreCompleted = true;
      if (__DEV__) {
        console.warn("[AppBootstrap] restoreSession timed out; falling back to unauthenticated state");
      }

      void sessionService.clearInvalidSession().catch(() => undefined);
      setSession(null);
      setStatus("unauthenticated");
    }, restoreTimeoutMs);

    void sessionService
      .restoreSession()
      .then((restoredSession) => {
        if (!active || restoreCompleted) {
          return;
        }

        if (restoreTimeout) {
          clearTimeout(restoreTimeout);
        }

        restoreCompleted = true;
        setSession(restoredSession);
        setStatus(restoredSession?.accessToken ? "authenticated" : "unauthenticated");

        if (__DEV__) {
          console.debug(
            "[AppBootstrap] restoreSession finished",
            restoredSession?.accessToken ? "authenticated" : "unauthenticated"
          );
        }
      })
      .catch((error) => {
        if (!active || restoreCompleted) {
          return;
        }

        if (restoreTimeout) {
          clearTimeout(restoreTimeout);
        }

        restoreCompleted = true;
        if (__DEV__) {
          console.debug("[AppBootstrap] restoreSession failed; clearing session and showing login", error instanceof Error ? error.message : "unknown error");
        }
        void sessionService.clearInvalidSession().catch(() => undefined);
        setSession(null);
        setStatus("unauthenticated");
      });

    return () => {
      active = false;
      if (restoreTimeout) {
        clearTimeout(restoreTimeout);
      }
    };
  }, []);

  const value = useMemo<AppBootstrapContextValue>(
    () => ({
      status,
      session,
      currentUser: session?.currentUser ?? null,
      login: async (credentials: LoginCredentials) => {
        setStatus("loading");

        try {
          const nextSession = await sessionService.login(credentials);
          setSession(nextSession);
          setStatus("authenticated");
        } catch (error) {
          setSession(null);
          setStatus("unauthenticated");
          throw error;
        }
      },
      logout: async () => {
        setStatus("loading");
        const scope = session?.currentUser?.hotelId && session.currentUser.userId
          ? { hotelId: session.currentUser.hotelId, userId: session.currentUser.userId }
          : null;

        try {
          await sessionService.logout();
        } finally {
          if (scope) {
            await defaultOfflineCache.clearScope(scope);
          }
          setSession(null);
          setStatus("unauthenticated");
        }
      },
      refreshSession: async () => {
        try {
          const refreshedSession = await sessionService.refresh();
          if (refreshedSession) {
            setSession(refreshedSession);
            setStatus("authenticated");
            return;
          }
        } catch {
          // Fall through to unauthenticated state.
        }

        setSession(null);
        setStatus("unauthenticated");
      },
      clearSession: async () => {
        await sessionService.clearInvalidSession();
        setSession(null);
        setStatus("unauthenticated");
      }
    }),
    [session, status]
  );

  return (
    <AppBootstrapContext.Provider value={value}>
      {children}
    </AppBootstrapContext.Provider>
  );
}

export function useAppBootstrap(): AppBootstrapContextValue {
  const value = useContext(AppBootstrapContext);
  if (!value) {
    throw new Error("useAppBootstrap must be used within AppBootstrapProvider");
  }

  return value;
}
