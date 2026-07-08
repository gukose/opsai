import { AssignedTask, ConversationItem } from "./types";

export type AssistantHomeState = {
  conversationId?: string;
  conversationItems: ConversationItem[];
  nextAssignedTask: AssignedTask | null;
  confirmationIdempotencyKey?: string | null;
  createdTaskId?: string | null;
  source: "static-mock" | "local-mock" | "backend";
};

export function createEmptyAssistantHomeState(
  source: AssistantHomeState["source"]
): AssistantHomeState {
  return {
    conversationId: undefined,
    conversationItems: [],
    nextAssignedTask: null,
    confirmationIdempotencyKey: null,
    createdTaskId: null,
    source
  };
}
