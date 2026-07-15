import { mapCurrentUserResponseToSnapshot } from "../api/auth/AuthDtos";
import type { AuthSessionResponseDto } from "../api/auth/AuthDtos";
import type { AppSessionSnapshot } from "../session/sessionTypes";

export function mapAuthSessionResponseToSnapshot(
  response: AuthSessionResponseDto
): AppSessionSnapshot {
  return {
    tokenType: response.tokenType,
    accessToken: response.accessToken,
    accessTokenExpiresAt: response.accessTokenExpiresAt,
    refreshToken: response.refreshToken,
    refreshTokenExpiresAt: response.refreshTokenExpiresAt,
    currentUser: mapCurrentUserResponseToSnapshot(response.user)
  };
}
