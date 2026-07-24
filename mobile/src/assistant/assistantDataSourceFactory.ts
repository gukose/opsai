import {
  assistantDataSourceMode,
  assistantStaticMockEnabled
} from "../config/assistantConfig";
import { appApiBaseUrl } from "../config/appConfig";
import { MobileHotelOpAiClient } from "../api/hotelOpAiClient";
import { CurrentUserSnapshot } from "../session/sessionTypes";
import { AssistantDataSource } from "./assistantDataSource";
import { BackendAssistantDataSource } from "./backendAssistantDataSource";
import { LocalInteractiveAssistantDataSource } from "./localInteractiveAssistantDataSource";
import { StaticMockAssistantDataSource } from "./staticMockAssistantDataSource";

type AssistantHomeDataSourceOptions = {
  accessTokenProvider: () => string | null;
  currentUserProvider: () => CurrentUserSnapshot | null;
};

export function createAssistantHomeDataSource(
  options: AssistantHomeDataSourceOptions
): AssistantDataSource {
  if (assistantDataSourceMode === "backend") {
    return new BackendAssistantDataSource(
      new MobileHotelOpAiClient({
        baseUrl: appApiBaseUrl,
        accessTokenProvider: options.accessTokenProvider
      }),
      options.currentUserProvider
    );
  }

  if (assistantStaticMockEnabled) {
    return new StaticMockAssistantDataSource();
  }

  return new LocalInteractiveAssistantDataSource();
}
