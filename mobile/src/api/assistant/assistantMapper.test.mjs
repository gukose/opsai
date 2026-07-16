import assert from "node:assert/strict";
import test from "node:test";

import {
  assistantInterpretationFailureMessage,
  isAssistantInterpretationFailureResponse,
  mapAssistantConversationResponseToHomeState
} from "./assistantMapper.ts";

const baseResponse = {
  conversationId: "conversation-1",
  state: "WAITING_FOR_CONFIRMATION",
  assistantMessage: "I have enough information. Please review the task.",
  intent: "MAINTENANCE",
  missingFields: [],
  followUpQuestion: null,
  taskPreview: null,
  taskCreationRequest: null,
  createdTaskId: null,
  messages: [
    {
      id: "message-1",
      role: "USER",
      inputType: "TEXT",
      text: "Room 502 has a leaking sink, please send maintenance.",
      voiceTranscript: null,
      voiceTranscriptMetadata: null,
      audioMetadata: null,
      attachments: [],
      imageObservations: [],
      attachmentIds: [],
      createdAt: "2026-07-10T09:15:00Z"
    }
  ]
};

test("maps successful OpenAI interpretation into intent and task preview details", () => {
  const state = mapAssistantConversationResponseToHomeState({
    ...baseResponse,
    taskPreview: {
      type: "MAINTENANCE",
      title: "Fix leaking sink",
      description: "Room 502 sink is leaking.",
      roomNumber: "502",
      publicAreaId: null,
      assetId: null,
      assignedTeam: "Maintenance",
      priority: "Medium",
      slaMinutes: 60,
      requiresPmsUpdate: true
    },
    taskCreationRequest: {
      conversationId: "conversation-1",
      draftId: "draft-1",
      draftVersion: 1,
      idempotencyKey: "confirm-1",
      preview: {
        type: "MAINTENANCE",
        title: "Fix leaking sink",
        description: "Room 502 sink is leaking.",
        roomNumber: "502",
        publicAreaId: null,
        assetId: null,
        assignedTeam: "Maintenance",
        priority: "Medium",
        slaMinutes: 60,
        requiresPmsUpdate: true
      }
    }
  });

  const intent = state.conversationItems.find((item) => item.type === "intent");
  const preview = state.conversationItems.find((item) => item.type === "taskPreview");

  assert.equal(intent?.label, "Maintenance detected");
  assert.equal(preview?.task.intent, "Maintenance");
  assert.equal(preview?.task.room, "502");
  assert.equal(preview?.task.priority, "Medium");
  assert.equal(preview?.task.type, "Fix leaking sink");
  assert.equal(preview?.task.description, "Room 502 sink is leaking.");
});

test("maps persisted image observations as user-provided image notes", () => {
  const state = mapAssistantConversationResponseToHomeState({
    ...baseResponse,
    messages: [
      {
        id: "message-attachment",
        role: "USER",
        inputType: "MIXED",
        text: null,
        voiceTranscript: null,
        voiceTranscriptMetadata: null,
        audioMetadata: null,
        attachments: [
          {
            id: "att-1",
            type: "IMAGE",
            originalFileName: "sink.png",
            mimeType: "image/png",
            sizeBytes: 1234,
            widthPx: 100,
            heightPx: 100,
            localReference: "local://sink.png",
            storageStatus: "LOCAL_METADATA_ONLY"
          }
        ],
        imageObservations: [
          {
            id: "obs-1",
            attachmentId: "att-1",
            text: "Water visible under the sink",
            source: "USER_PROVIDED"
          }
        ],
        attachmentIds: ["att-1"],
        createdAt: "2026-07-10T09:15:00Z"
      }
    ]
  });

  const imageNote = state.conversationItems.find(
    (item) => item.type === "text" && item.text === "Image note: Water visible under the sink"
  );

  assert.equal(imageNote?.author, "user");
});

test("maps registered attachment response into conversation attachment without local preview", () => {
  const state = mapAssistantConversationResponseToHomeState({
    ...baseResponse,
    messages: [
      {
        id: "message-registered",
        role: "USER",
        inputType: "MIXED",
        text: "Room 101 sink is leaking",
        voiceTranscript: null,
        voiceTranscriptMetadata: null,
        audioMetadata: null,
        attachments: [
          {
            id: "018f0000-0000-7000-8000-000000000001",
            type: "IMAGE",
            originalFileName: "sink.jpg",
            mimeType: "image/jpeg",
            sizeBytes: 2048,
            widthPx: 640,
            heightPx: 480,
            localReference: null,
            storageStatus: "REGISTERED",
            storageReference: null
          }
        ],
        imageObservations: [],
        attachmentIds: ["018f0000-0000-7000-8000-000000000001"],
        createdAt: "2026-07-10T09:15:00Z"
      }
    ]
  });

  const attachment = state.conversationItems.find((item) => item.type === "attachment");

  assert.equal(attachment?.attachment.id, "018f0000-0000-7000-8000-000000000001");
  assert.equal(attachment?.attachment.filename, "sink.jpg");
  assert.equal(attachment?.attachment.storageStatus, "REGISTERED");
  assert.equal(attachment?.attachment.imageUri, undefined);
  assert.equal(attachment?.attachment.localReference, undefined);
});

test("detects OpenAI interpretation failure responses for UI error feedback", () => {
  const response = {
    ...baseResponse,
    state: "WAITING_FOR_USER_ANSWER",
    assistantMessage: "OpenAI request failed with status 400",
    intent: "UNKNOWN"
  };

  assert.equal(isAssistantInterpretationFailureResponse(response), true);
  assert.equal(
    assistantInterpretationFailureMessage(response),
    "OpenAI interpretation failed. OpenAI request failed with status 400"
  );
});

test("does not treat normal clarification follow-up as OpenAI failure", () => {
  const response = {
    ...baseResponse,
    state: "WAITING_FOR_USER_ANSWER",
    assistantMessage: "Which room should I create this for?",
    intent: "UNKNOWN",
    missingFields: [{ key: "roomNumber", label: "Room", required: true }]
  };

  assert.equal(isAssistantInterpretationFailureResponse(response), false);
});
