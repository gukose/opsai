import { AssistantHomeState } from "./homeState";
import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";

export interface AssistantDataSource {
  loadHomeState(): Promise<AssistantHomeState>;

  sendTextMessage(
    conversationId: string,
    text: string,
    attachments?: LocalAttachmentMetadata[],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations?: LocalImageObservationMetadata[]
  ): Promise<AssistantHomeState>;

  sendVoiceMessage(
    conversationId: string,
    transcript: string,
    audioMetadata?: {
      originalFileName?: string;
      mimeType?: string;
      durationMs?: number;
      sizeBytes?: number;
    }
  ): Promise<AssistantHomeState>;

  confirmTask(conversationId: string, idempotencyKey: string): Promise<AssistantHomeState>;

  resetConversation(conversationId: string): Promise<AssistantHomeState>;
}
