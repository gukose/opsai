import { ApiClient } from "../client/ApiClient";
import {
  AuthSessionResponseDto,
  CurrentUserResponseDto,
  LoginRequestDto,
  LogoutResponseDto,
  RefreshRequestDto
} from "./AuthDtos";

export interface AuthApi {
  login(request: LoginRequestDto): Promise<AuthSessionResponseDto>;

  refresh(request: RefreshRequestDto): Promise<AuthSessionResponseDto>;

  logout(): Promise<LogoutResponseDto>;

  me(): Promise<CurrentUserResponseDto>;
}

export class HttpAuthApi implements AuthApi {
  constructor(private readonly client: ApiClient) {}

  login(request: LoginRequestDto): Promise<AuthSessionResponseDto> {
    return this.client.post("/api/v1/auth/login", request, { skipAuth: true });
  }

  refresh(request: RefreshRequestDto): Promise<AuthSessionResponseDto> {
    return this.client.post("/api/v1/auth/refresh", request, { skipAuth: true });
  }

  logout(): Promise<LogoutResponseDto> {
    return this.client.post("/api/v1/auth/logout", {}, {});
  }

  me(): Promise<CurrentUserResponseDto> {
    return this.client.get("/api/v1/auth/me");
  }
}
