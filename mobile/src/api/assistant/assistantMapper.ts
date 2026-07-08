import {
  ActionQuestion,
  ConversationItem,
  ConversationAttachment,
  TaskPreviewMessage
} from "../../assistant/types";
import {
  AssistantConversationResponseDto,
  AssistantConversationMessageDto,
  AssistantFollowUpQuestionDto,
  AssistantMessageAttachmentDto,
  AssistantTaskPreviewDto
} from "./AssistantDtos";
import { AssistantHomeState } from "../../assistant/homeState";

export function mapAssistantConversationResponseToHomeState(
  response: AssistantConversationResponseDto
): AssistantHomeState {
  return {
    conversationId: response.conversationId,
    conversationItems: mapConversationItems(response),
    nextAssignedTask: null,
    confirmationIdempotencyKey: response.taskCreationRequest?.idempotencyKey ?? null,
    createdTaskId: response.createdTaskId ?? null,
    source: "backend"
  };
}

function mapConversationItems(
  response: AssistantConversationResponseDto
): ConversationItem[] {
  const items: ConversationItem[] = [];

  response.messages.forEach((message) => {
    items.push(...mapConversationMessage(message));
  });

  if (response.intent && response.intent !== "UNKNOWN") {
    items.push({
      id: `intent-${response.conversationId}`,
      type: "intent",
      label: `${humanize(response.intent)} detected`,
      tone: intentTone(response.intent)
    });
  }

  if (response.messages.length > 0 && response.assistantMessage.trim().length > 0) {
    items.push({
      id: `assistant-message-${response.conversationId}`,
      type: "text",
      author: "assistant",
      text: response.assistantMessage
    });
  }

  if (response.followUpQuestion) {
    const question = mapFollowUpQuestion(response.followUpQuestion);
    if (question) {
      items.push(question);
    }
  }

  if (response.taskPreview) {
    items.push({
      id: `task-preview-${response.conversationId}`,
      type: "taskPreview",
      task: mapTaskPreview(response.taskPreview)
    });
  }

  return items;
}

function mapConversationMessage(message: AssistantConversationMessageDto): ConversationItem[] {
  if (message.inputType === "VOICE" || message.inputType === "VOICE_TRANSCRIPT") {
    return [
      {
        id: message.id,
        type: "voice",
        author: "user",
        transcript: message.voiceTranscript ?? message.text ?? "",
        audioMetadata: message.audioMetadata
          ? {
              originalFileName: message.audioMetadata.originalFileName ?? undefined,
              mimeType: message.audioMetadata.mimeType ?? undefined,
              durationMs: message.audioMetadata.durationMs ?? undefined,
              sizeBytes: message.audioMetadata.sizeBytes ?? undefined
            }
          : undefined,
        duration: message.audioMetadata?.durationMs
          ? formatDuration(message.audioMetadata.durationMs)
          : "0:00",
        timestamp: shortTime(message.createdAt)
      }
    ];
  }

  if (message.attachments.length > 0) {
    return message.attachments.map((attachment, index) => ({
      id: `${message.id}-${attachment.id || index}`,
      type: "attachment",
      author: message.role === "USER" ? "user" : "assistant",
      attachment: mapAttachment(attachment),
      timestamp: shortTime(message.createdAt)
    }));
  }

  return [
    {
      id: message.id,
      type: "text",
      author: message.role === "USER" ? "user" : "assistant",
      text: message.text ?? "",
      timestamp: shortTime(message.createdAt)
    }
  ];
}

function mapAttachment(attachment: AssistantMessageAttachmentDto): ConversationAttachment {
  return {
    id: attachment.id,
    filename: attachment.originalFileName ?? attachment.id,
    size: formatFileSize(attachment.sizeBytes),
    mimeType: attachment.mimeType ?? undefined,
    imageUri: undefined,
    widthPx: attachment.widthPx ?? undefined,
    heightPx: attachment.heightPx ?? undefined
  };
}

function mapFollowUpQuestion(
  question: AssistantFollowUpQuestionDto
): ActionQuestion | null {
  if (question.options.length === 0) {
    return {
      id: question.id,
      type: "question",
      actions: [
        { id: `${question.id}-confirm`, label: "Confirm", variant: "confirm" },
        { id: `${question.id}-other`, label: "Choose another room", variant: "secondary" }
      ]
    };
  }

  return {
    id: question.id,
    type: "question",
    actions: question.options.map((option, index) => ({
      id: option.id,
      label: option.label,
      variant: index === 0 ? "confirm" : "secondary"
    }))
  };
}

function mapTaskPreview(preview: AssistantTaskPreviewDto): TaskPreviewMessage["task"] {
  return {
    type: preview.title || humanize(preview.type),
    room:
      preview.roomNumber ||
      preview.publicAreaId ||
      preview.assetId ||
      "Pending",
    description: preview.description,
    assignedTo: preview.assignedTeam || "Unassigned",
    priority: preview.priority || "Medium",
    sla: preview.slaMinutes ? `${preview.slaMinutes} min` : "N/A"
  };
}

function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function intentTone(intent: string): "guest" | "maintenance" | "housekeeping" | "lostFound" {
  switch (intent) {
    case "MAINTENANCE":
      return "maintenance";
    case "HOUSEKEEPING":
      return "housekeeping";
    case "LOST_AND_FOUND":
      return "lostFound";
    default:
      return "guest";
  }
}

function shortTime(createdAt: string): string {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatDuration(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.round(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function formatFileSize(sizeBytes?: number | null): string {
  if (sizeBytes == null || Number.isNaN(sizeBytes) || sizeBytes <= 0) {
    return "Attachment";
  }

  const kilobytes = sizeBytes / 1024;
  if (kilobytes < 1024) {
    return `${Math.max(1, Math.round(kilobytes))} KB`;
  }

  return `${(kilobytes / 1024).toFixed(1)} MB`;
}
