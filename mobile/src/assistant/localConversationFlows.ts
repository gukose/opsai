import {
  ActionQuestion,
  ConversationItem,
  TaskPreviewMessage
} from "./types";

export type LocalConversationField = {
  key: string;
  label: string;
  required: boolean;
};

export type LocalValidationIssue = {
  fieldKey: string;
  message: string;
};

export type LocalConversationFlow = {
  id: string;
  label: string;
  requiredFields: LocalConversationField[];
  optionalFields: LocalConversationField[];
  validationRules: Array<(fields: Record<string, string>) => LocalValidationIssue[]>;
  matchScore: (text: string) => number;
  extractFields: (text: string, currentFieldKey?: string | null) => Record<string, string>;
  buildFollowUpQuestion: (field: LocalConversationField) => ActionQuestion;
  buildPreview: (fields: Record<string, string>) => TaskPreviewMessage["task"];
};

const roomPattern = /\b(?:room\s*)?(\d{2,5})\b/i;

export const localConversationFlows: LocalConversationFlow[] = [
  {
    id: "guest-request",
    label: "Guest Request",
    requiredFields: [
      { key: "roomNumber", label: "Room", required: true },
      { key: "description", label: "Request details", required: true }
    ],
    optionalFields: [],
    validationRules: [
      (fields) => {
        const roomNumber = fields.roomNumber ?? "";
        if (roomNumber && /[^0-9]/.test(roomNumber)) {
          return [
            {
              fieldKey: "roomNumber",
              message: "Room number must contain only digits."
            }
          ];
        }

        return [];
      }
    ],
    matchScore: (text) => {
      const normalized = text.toLowerCase();
      const keywords = ["guest", "request", "towel", "blanket", "pillow", "water", "deliver", "need", "needs"];
      if (keywords.some((keyword) => normalized.includes(keyword))) {
        return 0.92;
      }
      if (roomPattern.test(text)) {
        return 0.72;
      }
      return 0.56;
    },
    extractFields: (text, currentFieldKey) => {
      const fields: Record<string, string> = {};
      const trimmed = text.trim();

      if (currentFieldKey) {
        fields[currentFieldKey] = trimmed;
      }

      const roomMatch = text.match(roomPattern);
      if (roomMatch?.[1]) {
        fields.roomNumber = roomMatch[1];
      }

      if (!currentFieldKey || currentFieldKey === "description") {
        fields.description = trimmed;
      }

      return fields;
    },
    buildFollowUpQuestion: (field) => {
      if (field.key === "roomNumber") {
        return {
          id: "guest-request-room",
          type: "question",
          actions: [
            { id: "guest-request-room-confirm", label: "Confirm", value: "101", variant: "confirm" },
            { id: "guest-request-room-other", label: "Choose another room", variant: "secondary" }
          ]
        };
      }

      if (field.key === "description") {
        return {
          id: "guest-request-description",
          type: "question",
          actions: [
            { id: "guest-request-description-confirm", label: "Confirm", variant: "confirm" },
            { id: "guest-request-description-other", label: "Try again", variant: "secondary" }
          ]
        };
      }

      return {
        id: `guest-request-${field.key}`,
        type: "question",
        actions: [{ id: `guest-request-${field.key}-confirm`, label: "Confirm", variant: "confirm" }]
      };
    },
    buildPreview: (fields) => ({
      intent: "Guest Request",
      type: "Guest Request",
      room: fields.roomNumber ?? "",
      description: fields.description ?? "",
      assignedTo: "Guest Services",
      priority: "Medium",
      sla: "60 min"
    })
  },
  {
    id: "maintenance",
    label: "Maintenance",
    requiredFields: [
      { key: "roomNumber", label: "Room or location", required: true },
      { key: "description", label: "Issue details", required: true }
    ],
    optionalFields: [],
    validationRules: [
      (fields) => {
        const roomNumber = fields.roomNumber ?? "";
        if (roomNumber && /[^0-9]/.test(roomNumber)) {
          return [
            {
              fieldKey: "roomNumber",
              message: "Room number must contain only digits."
            }
          ];
        }

        return [];
      },
      (fields) => {
        const description = fields.description ?? "";
        if (description && description.length < 3) {
          return [
            {
              fieldKey: "description",
              message: "Issue details are too short."
            }
          ];
        }

        return [];
      }
    ],
    matchScore: (text) => {
      const normalized = text.toLowerCase();
      const keywords = ["ac", "maintenance", "repair", "broken", "leak", "toilet", "not working"];
      if (keywords.some((keyword) => normalized.includes(keyword))) {
        return 0.93;
      }
      if (roomPattern.test(text)) {
        return 0.75;
      }
      return 0.55;
    },
    extractFields: (text, currentFieldKey) => {
      const fields: Record<string, string> = {};
      const trimmed = text.trim();

      if (currentFieldKey) {
        fields[currentFieldKey] = trimmed;
      }

      const roomMatch = text.match(roomPattern);
      if (roomMatch?.[1]) {
        fields.roomNumber = roomMatch[1];
      }

      if (!currentFieldKey || currentFieldKey === "description") {
        fields.description = trimmed;
      }

      return fields;
    },
    buildFollowUpQuestion: (field) => {
      if (field.key === "roomNumber") {
        return {
          id: "maintenance-room",
          type: "question",
          actions: [
            { id: "maintenance-room-confirm", label: "Confirm", value: "101", variant: "confirm" },
            { id: "maintenance-room-other", label: "Choose another room", variant: "secondary" }
          ]
        };
      }

      if (field.key === "description") {
        return {
          id: "maintenance-description",
          type: "question",
          actions: [
            { id: "maintenance-description-confirm", label: "Confirm", variant: "confirm" },
            { id: "maintenance-description-other", label: "Try again", variant: "secondary" }
          ]
        };
      }

      return {
        id: `maintenance-${field.key}`,
        type: "question",
        actions: [{ id: `maintenance-${field.key}-confirm`, label: "Confirm", variant: "confirm" }]
      };
    },
    buildPreview: (fields) => ({
      intent: "Maintenance",
      type: "Maintenance",
      room: fields.roomNumber ?? "",
      description: fields.description ?? "",
      assignedTo: "Maintenance",
      priority: "Medium",
      sla: "45 min"
    })
  }
];

export function buildFlowSelectionQuestion(): ActionQuestion {
  return {
    id: "local-flow-selection",
    type: "question",
    actions: localConversationFlows.map((flow, index) => ({
      id: `local-flow-${flow.id}`,
      label: flow.label,
      value: flow.id,
      variant: index === 0 ? "confirm" : "secondary"
    }))
  };
}
