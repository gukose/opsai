import { nextAssignedTask, assistantConversation } from "./sampleConversation";
import { AssistantHomeState } from "./homeState";

export const mockAssistantHomeState: AssistantHomeState = {
  conversationItems: assistantConversation,
  nextAssignedTask,
  createdTaskId: null,
  source: "static-mock"
};
