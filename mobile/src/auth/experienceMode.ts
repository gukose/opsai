import { CurrentUserSnapshot } from "../session/sessionTypes";
import { getCurrentUserPermissionCodes } from "./currentUserHelpers";

export type UserExperienceMode = "FRONTLINE_SIMPLE" | "SUPERVISOR" | "MANAGER";

// These are presentation capabilities. The backend remains authoritative for every action.
const MANAGER_CAPABILITIES = new Set([
  "PLATFORM_HOTEL_MANAGE", "MANAGER_REPORTING", "DASHBOARD_READ", "REPORT_READ"
]);
const SUPERVISOR_CAPABILITIES = new Set([
  "TASK_ASSIGN", "HOUSEKEEPING_INSPECTION", "TASK_TEAM_VIEW", "TASK_MANAGE",
  "TEAM_TASK_VIEW", "INSPECTION_VIEW"
]);

function hasAny(codes: Set<string>, capabilities: Set<string>): boolean {
  for (const capability of capabilities) {
    if (codes.has(capability)) return true;
  }
  return false;
}

export function resolveExperienceMode(currentUser: CurrentUserSnapshot | null): UserExperienceMode {
  const codes = new Set(getCurrentUserPermissionCodes(currentUser).map((code) => code.trim().toUpperCase()));
  if (hasAny(codes, MANAGER_CAPABILITIES)) return "MANAGER";
  if (hasAny(codes, SUPERVISOR_CAPABILITIES)) return "SUPERVISOR";
  return "FRONTLINE_SIMPLE";
}

export function isSupervisorMode(mode: UserExperienceMode): boolean {
  return mode === "SUPERVISOR" || mode === "MANAGER";
}
