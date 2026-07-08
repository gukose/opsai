# Mobile

The mobile app is built with React Native and Expo.

## Mobile Goals

- Keep the home experience compact.
- Make the assistant feel conversational, not form-driven.
- Keep the composer pinned at the bottom.
- Render task previews inline in the conversation.

## Component Rules

- Build reusable components for assistant messages, voice bubbles, attachments, task previews, questions, and navigation.
- Avoid giant screens with embedded business logic.
- Keep UI state in feature-level hooks or controllers, not in view components.

## Data Source Modes

The assistant UI supports:

- static mock mode
- local interactive mock mode
- backend mode

Backend mode must be easy to enable for real API testing.

## UI Source of Truth

Reference images under `docs/ui-reference/` define the visual contract.
Do not redesign the UI when matching references.

