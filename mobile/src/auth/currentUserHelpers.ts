import { CurrentUserSnapshot } from "../session/sessionTypes";

function trimOrNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function getCurrentUserDisplayName(currentUser: CurrentUserSnapshot | null): string {
  return (
    trimOrNull(currentUser?.displayName) ||
    trimOrNull(currentUser?.email?.split("@")[0]?.replace(/[._-]+/g, " ")) ||
    "Signed-in user"
  );
}

export function getCurrentUserHotelLabel(currentUser: CurrentUserSnapshot | null): string {
  return trimOrNull(currentUser?.hotelName) || trimOrNull(currentUser?.hotelId) || "Assigned hotel";
}

export function getCurrentUserRoleCodes(currentUser: CurrentUserSnapshot | null): string[] {
  return (
    currentUser?.roles
      ?.map((role) => trimOrNull(role.code) || trimOrNull(role.name))
      .filter((value): value is string => Boolean(value)) ?? []
  );
}

export function getCurrentUserPermissionCodes(currentUser: CurrentUserSnapshot | null): string[] {
  return (
    currentUser?.permissions
      ?.map((permission) => trimOrNull(permission.code) || trimOrNull(permission.name))
      .filter((value): value is string => Boolean(value)) ?? []
  );
}

export function getCurrentUserPermissionCount(currentUser: CurrentUserSnapshot | null): number {
  return getCurrentUserPermissionCodes(currentUser).length;
}

export function hasPermission(currentUser: CurrentUserSnapshot | null, permissionCode: string): boolean {
  const normalizedPermission = permissionCode.trim().toUpperCase();
  if (!normalizedPermission) {
    return false;
  }

  return getCurrentUserPermissionCodes(currentUser).some((permission) => permission.toUpperCase() === normalizedPermission);
}
