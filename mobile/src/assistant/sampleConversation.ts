import { AssignedTask, ConversationItem } from "./types";

export const nextAssignedTask: AssignedTask | null = {
  id: "next-task-203",
  title: "Deliver Extra Towels",
  room: "Room 203",
  priority: "Medium Priority",
  slaRemaining: "18:42"
};

export const assistantConversation: ConversationItem[] = [
  {
    id: "welcome",
    type: "text",
    author: "assistant",
    text: "Hi Ayse! 👋\nHow can I help you today?"
  },
  {
    id: "user-text",
    type: "text",
    author: "user",
    text: "101 room AC not working",
    timestamp: "09:31"
  },
  {
    id: "guest-intent",
    type: "intent",
    label: "Guest Request detected",
    tone: "guest"
  },
  {
    id: "assistant-room-question",
    type: "text",
    author: "assistant",
    text: "I understood this as a Guest Request.\nWhich room is this for?",
    timestamp: "09:31"
  },
  {
    id: "voice",
    type: "voice",
    author: "user",
    transcript: "AC in room 101 not working",
    duration: "0:07",
    timestamp: "09:32"
  },
  {
    id: "assistant-confirm-room",
    type: "text",
    author: "assistant",
    text: "I heard \"one zero one\".\nIs room 101 correct?",
    timestamp: "09:32"
  },
  {
    id: "room-actions",
    type: "question",
    actions: [
      { id: "confirm", label: "Yes, 101", variant: "confirm" },
      { id: "other", label: "Choose another room", variant: "secondary" }
    ]
  },
  {
    id: "selected-room",
    type: "text",
    author: "user",
    text: "101",
    timestamp: "09:33"
  },
  {
    id: "enough-info",
    type: "text",
    author: "assistant",
    text: "Thank you. I have enough\ninformation.",
    timestamp: "09:33"
  },
  {
    id: "preview",
    type: "taskPreview",
    task: {
      type: "Guest Request",
      room: "101",
      description: "AC not working",
      assignedTo: "Maintenance",
      priority: "Medium",
      sla: "60 min"
    }
  }
];
