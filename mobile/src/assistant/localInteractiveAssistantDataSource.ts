import { AssistantDataSource } from "./assistantDataSource";
import { AssistantHomeState } from "./homeState";
import { LocalConversationEngine } from "./localConversationEngine";
import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";

export class LocalInteractiveAssistantDataSource implements AssistantDataSource {
  private readonly engine = new LocalConversationEngine();

  async loadHomeState(): Promise<AssistantHomeState> {
    return this.engine.loadHomeState();
  }

  async sendTextMessage(
    conversationId: string,
    text: string,
    attachments: LocalAttachmentMetadata[] = [],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations: LocalImageObservationMetadata[] = []
  ): Promise<AssistantHomeState> {
    return this.engine.sendTextMessage(conversationId, text, attachments, voiceTranscript, imageObservations);
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
    return this.engine.sendTextMessage(conversationId, transcript);
  }

  async confirmTask(
    conversationId: string,
    idempotencyKey: string
  ): Promise<AssistantHomeState> {
    return this.engine.confirmTask(conversationId, idempotencyKey);
  }

  async resetConversation(conversationId: string): Promise<AssistantHomeState> {
    return this.engine.resetConversation(conversationId);
  }
}
