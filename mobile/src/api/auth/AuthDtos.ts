import type { CurrentUserSnapshot } from "../../session/sessionTypes";

export type LoginRequestDto = {
  hotelCode: string;
  email: string;
  password: string;
  deviceId?: string | null;
  deviceName?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
};

export type RefreshRequestDto = {
  refreshToken: string;
  deviceId?: string | null;
  deviceName?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
};

export type RoleResponseDto = {
  roleId: string;
  code: string;
  name: string;
};

export type PermissionResponseDto = {
  permissionId: string;
  code: string;
  name: string;
};

export type CurrentUserResponseDto = {
  userId: string;
  hotelId: string;
  employeeId?: string | null;
  email: string;
  displayName: string;
  hotelName: string;
  roles: RoleResponseDto[];
  permissions: PermissionResponseDto[];
};

export type AuthSessionResponseDto = {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: CurrentUserResponseDto;
};

export type LogoutResponseDto = {
  message?: string;
};

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
