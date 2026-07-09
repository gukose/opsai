import { AppSessionSnapshot } from "./sessionTypes";

export interface SessionStore {
  load(): Promise<AppSessionSnapshot | null>;
  save(session: AppSessionSnapshot): Promise<void>;
  clear(): Promise<void>;
}
