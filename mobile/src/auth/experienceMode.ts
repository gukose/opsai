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
  const supervisor = hasAny(codes, SUPERVISOR_CAPABILITIES);
  // Housekeeping supervisors receive DASHBOARD_READ for operational summaries,
  // but that capability alone must not route them into the manager landing UI.
  // Explicit hotel-wide/reporting capabilities still take precedence when roles overlap.
  const manager = hasAny(codes, MANAGER_CAPABILITIES);
  const hotelWideManager = codes.has("PLATFORM_HOTEL_MANAGE") || codes.has("MANAGER_REPORTING") || codes.has("REPORT_READ");
  if (supervisor && !hotelWideManager) return "SUPERVISOR";
  if (manager) return "MANAGER";
  if (supervisor) return "SUPERVISOR";
  return "FRONTLINE_SIMPLE";
}

export function isSupervisorMode(mode: UserExperienceMode): boolean {
  return mode === "SUPERVISOR" || mode === "MANAGER";
}
