import type { components } from "@hotelopai/api-client";
import type { CurrentUserSnapshot } from "../../session/sessionTypes";

export type LoginRequestDto = components["schemas"]["LoginRequest"];
export type RefreshRequestDto = components["schemas"]["RefreshRequest"];
export type RoleResponseDto = components["schemas"]["RoleResponse"];
export type PermissionResponseDto = components["schemas"]["PermissionResponse"];
export type CurrentUserResponseDto = components["schemas"]["CurrentUserResponse"];
export type AuthSessionResponseDto = components["schemas"]["AuthSessionResponse"];
export type LogoutResponseDto = Record<string, string>;

export function mapCurrentUserResponseToSnapshot(
  user: CurrentUserResponseDto
): CurrentUserSnapshot {
  return {
    userId: user.userId,
    hotelId: user.hotelId,
    employeeId: user.employeeId ?? null,
    email: user.email,
    displayName: user.displayName,
    hotelName: user.hotelName,
    roles: user.roles.map((role) => ({
      roleId: role.roleId,
      code: role.code,
      name: role.name
    })),
    permissions: user.permissions.map((permission) => ({
      permissionId: permission.permissionId,
      code: permission.code,
      name: permission.name
    }))
  };
}
