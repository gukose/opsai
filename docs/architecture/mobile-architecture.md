# Mobile Architecture

## Goal

The React Native mobile app should feel like a compact assistant-first workflow, not a form system. The Home screen keeps fixed operational context visible while only the assistant conversation scrolls.

## Principles

- Component-based UI.
- Reusable assistant primitives.
- No duplicated UI logic.
- Assistant conversation is the primary interaction surface.
- Composer remains pinned to the bottom.
- Task Preview appears inline in the conversation.
- Avoid large modal dialogs unless required.
- UI references under `docs/ui-reference/` are the source of truth.

## Suggested Structure

```text
mobile/src
├── assistant
├── components
│   ├── Assistant
│   ├── Conversation
│   ├── Composer
│   ├── Navigation
│   └── ui
├── api
├── state
├── theme
└── utils
```

## Assistant Components

`components/Assistant`

Responsibilities:

- screen shell
- assistant header
- overview metrics
- next task card
- assistant conversation card

Reusable components:

- `AssistantHomeScreen`
- `AssistantHeader`
- `OverviewStrip`
- `NextTaskCard`
- `AssistantCard`

## Conversation Components

`components/Conversation`

Responsibilities:

- render typed conversation items
- message bubbles
- voice bubbles
- attachment cards
- intent badges
- dropdown questions
- task previews

Reusable components:

- `ConversationList`
- `MessageBubble`
- `VoiceBubble`
- `AttachmentCard`
- `IntentBadge`
- `DropdownQuestion`
- `TaskPreview`

## Composer Components

`components/Composer`

Responsibilities:

- text input
- attachment actions
- voice recording entry point
- send action

The composer should stay visually fixed and not grow the Home screen.

## State Management

Initial production approach:

- keep remote server state in API hooks/services
- keep local composer state inside composer scope
- keep active conversation state in a dedicated assistant store or hook
- avoid global state for purely visual component state

Recommended state groups:

- authenticated user
- active hotel context
- current shift
- active conversation session
- task list / next task
- notifications

## API Boundary

Mobile should call backend APIs only. It should never call OpenAI or UniMock directly.

Mobile sends:

- message text
- voice recording metadata or upload reference
- image attachment metadata or upload reference
- selected follow-up answers
- task confirmation actions

Mobile receives:

- assistant messages
- structured conversation items
- task preview
- required questions
- task creation result
- next task
- notification summaries

## UI Rendering Contract

Backend responses should map to stable mobile render types:

- `text`
- `voice`
- `attachment`
- `intent`
- `question`
- `taskPreview`
- `status`

The mobile app should not parse free-form assistant text to infer UI.

## Offline and Failure Handling

Minimum production behavior:

- composer disables send during active submission
- failed message shows retry affordance
- uploaded attachment has pending/failed state
- task confirmation is idempotent
- conversation can recover after app restart

## Performance

- keep Home screen compact
- use stable keys for conversation items
- avoid rerendering the full conversation on composer text changes
- lazy load heavy attachment previews
- keep image sizes bounded

## Design Source of Truth

Before modifying mobile UI:

1. inspect `docs/ui-reference/assistant/`
2. inspect `docs/ui-reference/components/`
3. identify reusable components
4. update components, not screen-specific copies
5. explain unavoidable deviations before implementation
