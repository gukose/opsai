import { AssistantDataSource } from "./assistantDataSource";
import { AssistantHomeState } from "./homeState";
import { mockAssistantHomeState } from "./mockAssistantHomeState";

export class StaticMockAssistantDataSource implements AssistantDataSource {
  async loadHomeState(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }

  async sendTextMessage(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }

  async sendVoiceMessage(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }

  async confirmTask(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }

  async resetConversation(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }
}
