import type { AssistantMessageAttachmentDto, RegisterAssistantAttachmentRequestDto } from "../api/assistant/AssistantDtos";
import type { LocalAttachmentMetadata } from "./types";

export function buildAttachmentRegistrationRequest(
  attachment: LocalAttachmentMetadata
): RegisterAssistantAttachmentRequestDto {
  return {
    type: attachment.type,
    originalFileName: attachment.originalFileName,
    mimeType: attachment.mimeType,
    sizeBytes: attachment.sizeBytes,
    widthPx: attachment.widthPx ?? null,
    heightPx: attachment.heightPx ?? null
  };
}

export function splitAssistantMessageAttachments(attachments: LocalAttachmentMetadata[]): {
  registeredAttachmentIds: string[];
  localMetadataAttachments: AssistantMessageAttachmentDto[];
} {
  const registered = attachments.filter(
    (attachment) => attachment.storageStatus === "REGISTERED" && attachment.serverAttachmentId
  );
  const localMetadataOnly = attachments.filter(
    (attachment) => attachment.storageStatus !== "REGISTERED"
  );

  return {
    registeredAttachmentIds: registered.map((attachment) => attachment.serverAttachmentId ?? attachment.id),
    localMetadataAttachments: localMetadataOnly.map((attachment) => ({
      id: attachment.id,
      type: attachment.type,
      originalFileName: attachment.originalFileName,
      mimeType: attachment.mimeType,
      sizeBytes: attachment.sizeBytes,
      widthPx: attachment.widthPx ?? null,
      heightPx: attachment.heightPx ?? null,
      localReference: attachment.localReference ?? null,
      storageStatus: "LOCAL_METADATA_ONLY"
    }))
  };
}
