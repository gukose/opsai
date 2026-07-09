export type RoleSnapshot = {
  roleId: string;
  code: string;
  name: string;
};

export type PermissionSnapshot = {
  permissionId: string;
  code: string;
  name: string;
};

export type CurrentUserSnapshot = {
  userId: string;
  hotelId: string;
  employeeId?: string | null;
  email?: string | null;
  displayName?: string | null;
  hotelName?: string | null;
  roles?: RoleSnapshot[];
  permissions?: PermissionSnapshot[];
};

export type AppSessionSnapshot = {
  accessToken: string | null;
  accessTokenExpiresAt?: string | null;
  refreshToken: string | null;
  refreshTokenExpiresAt?: string | null;
  tokenType?: string | null;
  currentUser?: CurrentUserSnapshot | null;
};
