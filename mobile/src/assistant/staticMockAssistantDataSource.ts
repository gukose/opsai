import { AssistantDataSource } from "./assistantDataSource";
import { AssistantHomeState } from "./homeState";
import { mockAssistantHomeState } from "./mockAssistantHomeState";
import type { RegisteredAttachmentResponse } from "./attachmentMetadata";
import type { LocalAttachmentMetadata } from "./types";

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

  async registerAttachment(
    conversationId: string,
    attachment: LocalAttachmentMetadata
  ): Promise<RegisteredAttachmentResponse> {
    return {
      attachmentId: attachment.id,
      conversationId,
      type: attachment.type,
      originalFileName: attachment.originalFileName,
      mimeType: attachment.mimeType,
      sizeBytes: attachment.sizeBytes,
      widthPx: attachment.widthPx ?? null,
      heightPx: attachment.heightPx ?? null,
      storageStatus: "REGISTERED",
      storageReference: null,
      createdAt: new Date().toISOString()
    };
  }

  async confirmTask(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }

  async resetConversation(): Promise<AssistantHomeState> {
    return mockAssistantHomeState;
  }
}
