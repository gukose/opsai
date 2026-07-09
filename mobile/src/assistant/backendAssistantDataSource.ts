import { CurrentUserSnapshot } from "../session/sessionTypes";
import { HttpAssistantApi } from "../api/assistant/AssistantApi";
import { ApiClient } from "../api/client/ApiClient";
import { mapAssistantConversationResponseToHomeState } from "../api/assistant/assistantMapper";
import { AssistantDataSource } from "./assistantDataSource";
import { AssistantHomeState } from "./homeState";

export class BackendAssistantDataSource implements AssistantDataSource {
  private readonly api: HttpAssistantApi;
  private readonly currentUserProvider: () => CurrentUserSnapshot | null;

  constructor(client: ApiClient, currentUserProvider: () => CurrentUserSnapshot | null) {
    this.api = new HttpAssistantApi(client);
    this.currentUserProvider = currentUserProvider;
  }

  async loadHomeState(): Promise<AssistantHomeState> {
    const currentUser = this.currentUserProvider();
    if (!currentUser?.hotelId || !currentUser.userId) {
      throw new Error("Current user session is missing hotel or user context.");
    }

    const response = await this.api.startConversation({
      hotelId: currentUser.hotelId,
      userId: currentUser.userId
    });

    return mapAssistantConversationResponseToHomeState(response);
  }

  async sendTextMessage(conversationId: string, text: string): Promise<AssistantHomeState> {
    const response = await this.api.sendMessage(conversationId, {
      text,
      inputType: "TEXT",
      attachmentIds: []
    });
    return mapAssistantConversationResponseToHomeState(response);
  }

  async sendVoiceMessage(
    conversationId: string,
    transcript: string,
    audioMetadata?: {
      originalFileName?: string;
      mimeType?: string;
      durationMs?: number;
      sizeBytes?: number;
    }
  ): Promise<AssistantHomeState> {
    const response = await this.api.sendMessage(conversationId, {
      text: transcript,
      transcript,
      audioMetadata,
      inputType: "VOICE",
      attachmentIds: []
    });
    return mapAssistantConversationResponseToHomeState(response);
  }

  async confirmTask(
    conversationId: string,
    idempotencyKey: string
  ): Promise<AssistantHomeState> {
    const response = await this.api.confirmTask(conversationId, { idempotencyKey });

    return mapAssistantConversationResponseToHomeState(response);
  }

  async resetConversation(conversationId: string): Promise<AssistantHomeState> {
    const response = await this.api.resetConversation(conversationId);

    return mapAssistantConversationResponseToHomeState(response);
  }
}
