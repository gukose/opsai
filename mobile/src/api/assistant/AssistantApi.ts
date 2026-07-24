import {
  AssistantConversationController_confirmTask,
  AssistantConversationController_registerAttachment,
  AssistantConversationController_resetConversation,
  AssistantConversationController_sendMessage,
  AssistantConversationController_startConversation
} from "@hotelopai/api-client";
import { MobileHotelOpAiClient } from "../hotelOpAiClient";
import {
  AssistantConfirmTaskRequestDto,
  AssistantConversationResponseDto,
  RegisteredAssistantAttachmentResponseDto,
  RegisterAssistantAttachmentRequestDto,
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

  registerAttachment(
    conversationId: string,
    request: RegisterAssistantAttachmentRequestDto
  ): Promise<RegisteredAssistantAttachmentResponseDto>;

  confirmTask(
    conversationId: string,
    request: AssistantConfirmTaskRequestDto
  ): Promise<AssistantConversationResponseDto>;

  resetConversation(conversationId: string): Promise<AssistantConversationResponseDto>;
}

export class HttpAssistantApi implements AssistantApi {
  constructor(private readonly client: MobileHotelOpAiClient) {}

  startConversation(
    request: AssistantStartConversationRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.call("POST", (sdk, signal) =>
      AssistantConversationController_startConversation(sdk, { body: request, signal })
    );
  }

  sendMessage(
    conversationId: string,
    request: AssistantSendMessageRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.call("POST", (sdk, signal) =>
      AssistantConversationController_sendMessage(sdk, { pathParams: { conversationId }, body: request, signal })
    );
  }

  registerAttachment(
    conversationId: string,
    request: RegisterAssistantAttachmentRequestDto
  ): Promise<RegisteredAssistantAttachmentResponseDto> {
    return this.client.call("POST", (sdk, signal) =>
      AssistantConversationController_registerAttachment(sdk, { pathParams: { conversationId }, body: request, signal })
    ).then(toRegisteredAssistantAttachment);
  }

  confirmTask(
    conversationId: string,
    request: AssistantConfirmTaskRequestDto
  ): Promise<AssistantConversationResponseDto> {
    return this.client.call("POST", (sdk, signal) =>
      AssistantConversationController_confirmTask(sdk, { pathParams: { conversationId }, body: request, signal })
    );
  }

  resetConversation(conversationId: string): Promise<AssistantConversationResponseDto> {
    return this.client.call("POST", (sdk, signal) =>
      AssistantConversationController_resetConversation(sdk, { pathParams: { conversationId }, signal })
    );
  }
}

function toRegisteredAssistantAttachment(
  response: RegisteredAssistantAttachmentResponseDto | Omit<RegisteredAssistantAttachmentResponseDto, "storageStatus"> & { storageStatus: string }
): RegisteredAssistantAttachmentResponseDto {
  if (response.storageStatus !== "REGISTERED") {
    throw new Error("Attachment registration did not return REGISTERED metadata.");
  }
  return response as RegisteredAssistantAttachmentResponseDto;
}
