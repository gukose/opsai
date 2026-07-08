import {
  assistantApiBaseUrl,
  assistantDataSourceMode,
  assistantStaticMockEnabled
} from "../config/assistantConfig";
import { FetchApiClient } from "../api/client/FetchApiClient";
import { AssistantDataSource } from "./assistantDataSource";
import { BackendAssistantDataSource } from "./backendAssistantDataSource";
import { LocalInteractiveAssistantDataSource } from "./localInteractiveAssistantDataSource";
import { StaticMockAssistantDataSource } from "./staticMockAssistantDataSource";

export function createAssistantHomeDataSource(): AssistantDataSource {
  if (assistantDataSourceMode === "backend") {
    return new BackendAssistantDataSource(new FetchApiClient(assistantApiBaseUrl));
  }

  if (assistantStaticMockEnabled) {
    return new StaticMockAssistantDataSource();
  }

  return new LocalInteractiveAssistantDataSource();
}
