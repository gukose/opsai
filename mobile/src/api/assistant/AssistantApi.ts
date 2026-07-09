import { ApiClient } from "../client/ApiClient";
import {
  AssistantConfirmTaskRequestDto,
  AssistantConversationResponseDto,
  AssistantSendMessageRequestDto,
  AssistantStartConversationRequestDto
} from "./AssistantDtos";

export interface AssistantApi {
  startConversation(
    request: AssistantStartConversationRequestDto
  ): Promise<AssistantConversationResponseDto>;

  sendMessage(
    conversationId: string,
    request: AssistantSendMessageRequestDto
  ): Promise<AssistantConversationResponseDto>;

  confirmTask(
    conversationId: string,
    request: AssistantConfirmTaskRequestDto
  ): Promise<AssistantConversationResponseDto>;

  resetConversation(conversationId: string): Promise<AssistantConversationResponseDto>;
}

export class HttpAssistantApi implements AssistantApi {
  constructor(private readonly client: ApiClient) {}

  startConversation(
    request: AssistantStartConversationRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.post("/api/v1/assistant/conversations", request);
  }

  sendMessage(
    conversationId: string,
    request: AssistantSendMessageRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.post(`/api/v1/assistant/conversations/${conversationId}/messages`, request);
  }

  confirmTask(
    conversationId: string,
    request: AssistantConfirmTaskRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.post(`/api/v1/assistant/conversations/${conversationId}/confirm`, request);
  }

  resetConversation(conversationId: string): Promise<AssistantConversationResponseDto> {
    return this.client.post(`/api/v1/assistant/conversations/${conversationId}/reset`, {});
  }
}
