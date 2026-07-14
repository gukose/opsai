import {
  AssignedTask,
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata,
  ConversationItem,
  TextMessage,
  TaskPreviewMessage
} from "./types";
import {
  buildFlowSelectionQuestion,
  localConversationFlows,
  LocalConversationFlow
} from "./localConversationFlows";
import { AssistantHomeState, createEmptyAssistantHomeState } from "./homeState";

type LocalConversationStage = "idle" | "collecting" | "preview" | "confirmed";

type LocalConversationSession = {
  conversationId: string;
  state: AssistantHomeState;
  stage: LocalConversationStage;
  activeFlow: LocalConversationFlow | null;
  fields: Record<string, string>;
  pendingFieldKey: string | null;
  pendingPreview: TaskPreviewMessage["task"] | null;
};

export class LocalConversationEngine {
  private session: LocalConversationSession | null = null;

  loadHomeState(): AssistantHomeState {
    return this.ensureSession().state;
  }

  sendTextMessage(
    conversationId: string,
    text: string,
    attachments: LocalAttachmentMetadata[] = [],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations: LocalImageObservationMetadata[] = []
  ): AssistantHomeState {
    const session = this.ensureSession(conversationId);
    const semanticText = [text.trim(), voiceTranscript?.transcript.trim(), ...imageObservations.map((item) => item.text.trim())]
      .filter(Boolean)
      .join("\n");

    if (!semanticText && attachments.length === 0) {
      return session.state;
    }

    const userItems: ConversationItem[] = [
      ...(text.trim() ? [buildMessage("user", text.trim())] : []),
      ...(voiceTranscript ? [buildMessage("user", `Client transcript: ${voiceTranscript.transcript}`)] : []),
      ...imageObservations.map((observation) => buildMessage("user", `Image note: ${observation.text}`)),
      ...attachments.map((attachment) => buildAttachmentMessage(attachment))
    ];
    const conversationItems = [...session.state.conversationItems, ...userItems];
    const currentFieldKey = session.stage === "collecting" ? session.pendingFieldKey : null;

    if (!session.activeFlow) {
      const flow = this.pickFlow(semanticText);
      if (!flow) {
        session.state = {
          ...session.state,
          conversationItems: [
            ...conversationItems,
            buildAssistantMessage("Which type of task is this?"),
            buildFlowSelectionQuestion()
          ],
          source: "local-mock"
        };
        return session.state;
      }

      session.activeFlow = flow;
      session.fields = {
        ...session.fields,
        ...flow.extractFields(semanticText, currentFieldKey)
      };
      return this.continueFlow(session, conversationItems);
    }

    session.fields = {
      ...session.fields,
      ...this.extractFields(session, semanticText, currentFieldKey)
    };
    return this.continueFlow(session, conversationItems);
  }

  confirmTask(conversationId: string, idempotencyKey: string): AssistantHomeState {
    const session = this.ensureSession(conversationId);
    if (!session.pendingPreview) {
      return session.state;
    }

    const nextTask: AssignedTask = {
      id: `task-${Date.now()}`,
      title: session.pendingPreview.type,
      room: session.pendingPreview.room,
      priority: `${session.pendingPreview.priority} Priority`,
      slaRemaining: session.pendingPreview.sla
    };

    session.state = {
      ...session.state,
      conversationItems: [
        ...session.state.conversationItems,
        buildAssistantMessage(
          `Task created successfully.${nextTask.id ? ` Task ID: ${nextTask.id}` : ""}`
        )
      ],
      nextAssignedTask: nextTask,
      confirmationIdempotencyKey: idempotencyKey,
      createdTaskId: nextTask.id,
      source: "local-mock"
    };
    session.stage = "confirmed";
    session.activeFlow = null;
    session.fields = {};
    session.pendingFieldKey = null;
    session.pendingPreview = null;
    return session.state;
  }

  resetConversation(conversationId: string): AssistantHomeState {
    this.session = {
      conversationId,
      state: buildInitialState(conversationId),
      stage: "idle",
      activeFlow: null,
      fields: {},
      pendingFieldKey: null,
      pendingPreview: null
    };
    return this.session.state;
  }

  private ensureSession(conversationId?: string): LocalConversationSession {
    if (!this.session) {
      const nextConversationId = conversationId ?? `local-conversation-${Date.now()}`;
      this.session = {
        conversationId: nextConversationId,
        state: buildInitialState(nextConversationId),
        stage: "idle",
        activeFlow: null,
        fields: {},
        pendingFieldKey: null,
        pendingPreview: null
      };
      return this.session;
    }

    if (conversationId && this.session.conversationId !== conversationId) {
      this.session.conversationId = conversationId;
      this.session.state = {
        ...this.session.state,
        conversationId
      };
    }

    return this.session;
  }

  private pickFlow(text: string): LocalConversationFlow | null {
    return localConversationFlows
      .slice()
      .sort((a, b) => b.matchScore(text) - a.matchScore(text))
      .find((flow) => flow.matchScore(text) > 0.5) ?? null;
  }

  private continueFlow(
    session: LocalConversationSession,
    conversationItems: ConversationItem[]
  ): AssistantHomeState {
    const flow = session.activeFlow;
    if (!flow) {
      return session.state;
    }

    const missingField = flow.requiredFields.find((field) => !session.fields[field.key]?.trim());
    const validationIssues = flow.validationRules.flatMap((rule) => rule(session.fields));

    if (missingField) {
      session.stage = "collecting";
      session.pendingFieldKey = missingField.key;
      session.state = {
        ...session.state,
        conversationItems: [
          ...conversationItems,
          buildAssistantMessage(questionPromptFor(flow, missingField)),
          flow.buildFollowUpQuestion(missingField)
        ],
        source: "local-mock"
      };
      return session.state;
    }

    if (validationIssues.length > 0) {
      const issue = validationIssues[0];
      if (!issue) {
        return session.state;
      }
      const field =
        flow.requiredFields.find((candidate) => candidate.key === issue.fieldKey) ??
        flow.optionalFields.find((candidate) => candidate.key === issue.fieldKey) ??
        { key: issue.fieldKey, label: issue.fieldKey, required: true };

      session.stage = "collecting";
      session.pendingFieldKey = field.key;
      session.state = {
        ...session.state,
        conversationItems: [
          ...conversationItems,
          buildAssistantMessage(issue.message),
          flow.buildFollowUpQuestion(field)
        ],
        source: "local-mock"
      };
      return session.state;
    }

    const preview = flow.buildPreview(session.fields);
    session.pendingPreview = preview;
    session.stage = "preview";
    session.pendingFieldKey = null;
    session.state = {
      ...session.state,
      conversationItems: [
        ...conversationItems,
        buildAssistantMessage("I have enough information. Please review the task."),
        buildTaskPreview(preview)
      ],
      source: "local-mock"
    };
    return session.state;
  }

  private extractFields(
    session: LocalConversationSession,
    text: string,
    currentFieldKey: string | null
  ): Record<string, string> {
    if (!session.activeFlow) {
      return {};
    }

    return session.activeFlow.extractFields(text, currentFieldKey);
  }
}

function buildInitialState(conversationId: string): AssistantHomeState {
  return {
    ...createEmptyAssistantHomeState("local-mock"),
    conversationId,
    createdTaskId: null,
    conversationItems: [buildAssistantMessage("Hi. How can I help you today?")]
  };
}

function buildMessage(author: "user" | "assistant", text: string): TextMessage {
  return {
    id: `${author}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type: "text",
    author,
    text,
    timestamp: shortTime()
  };
}

function buildAssistantMessage(text: string): ConversationItem {
  return {
    ...buildMessage("assistant", text),
    timestamp: undefined
  } as TextMessage;
}

function buildAttachmentMessage(attachment: LocalAttachmentMetadata): ConversationItem {
  return {
    id: `attachment-${attachment.id}`,
    type: "attachment",
    author: "user",
    attachment: {
      id: attachment.id,
      type: attachment.type,
      filename: attachment.originalFileName,
      size: formatFileSize(attachment.sizeBytes),
      mimeType: attachment.mimeType,
      widthPx: attachment.widthPx,
      heightPx: attachment.heightPx,
      localReference: attachment.localReference,
      storageStatus: "LOCAL_METADATA_ONLY"
    },
    timestamp: shortTime()
  };
}

function buildTaskPreview(task: TaskPreviewMessage["task"]): TaskPreviewMessage {
  return {
    id: `preview-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type: "taskPreview",
    task
  };
}

function questionPromptFor(flow: LocalConversationFlow, field: { key: string; label: string }): string {
  if (field.key === "roomNumber") {
    return flow.label === "Maintenance"
      ? "Where is the maintenance issue?"
      : "Which room is this request for?";
  }

  if (field.key === "description") {
    return flow.label === "Maintenance"
      ? "What is the maintenance issue?"
      : "What does the guest need?";
  }

  return `Please provide ${field.label.toLowerCase()}.`;
}

function shortTime(): string {
  return new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatFileSize(sizeBytes: number): string {
  if (sizeBytes >= 1_000_000) {
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }
  if (sizeBytes >= 1_000) {
    return `${Math.round(sizeBytes / 1_000)} KB`;
  }
  return `${sizeBytes} B`;
}
