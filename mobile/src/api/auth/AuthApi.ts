import {
  AuthController_login,
  AuthController_logout,
  AuthController_me,
  AuthController_refresh
} from "@hotelopai/api-client";
import { MobileHotelOpAiClient } from "../hotelOpAiClient";
import type {
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
  private readonly client: MobileHotelOpAiClient;

  constructor(client: MobileHotelOpAiClient) {
    this.client = client;
  }

  login(request: LoginRequestDto): Promise<AuthSessionResponseDto> {
    return this.client.call(
      "POST",
      (sdk, signal) => AuthController_login(sdk, { body: request, signal }),
      { authenticated: false }
    );
  }

  refresh(request: RefreshRequestDto): Promise<AuthSessionResponseDto> {
    return this.client.call(
      "POST",
      (sdk, signal) => AuthController_refresh(sdk, { body: request, signal }),
      { authenticated: false }
    );
  }

  logout(): Promise<LogoutResponseDto> {
    return this.client.call("POST", (sdk, signal) => AuthController_logout(sdk, { signal }));
  }

  me(): Promise<CurrentUserResponseDto> {
    return this.client.call("GET", (sdk, signal) => AuthController_me(sdk, { signal }));
  }
}
