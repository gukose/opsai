import { AppSessionSnapshot, CurrentUserSnapshot } from "../session/sessionTypes";

export type AppLaunchState = "loading" | "unauthenticated" | "authenticated";

export type AppStateSnapshot = {
  status: AppLaunchState;
  session: AppSessionSnapshot | null;
  currentUser: CurrentUserSnapshot | null;
};
