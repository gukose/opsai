# Hotel OpAI UI Reference

This folder contains the visual design references for the Hotel OpAI mobile application.

The images in this folder are the source of truth for the UI implementation.

As UI references are added over time, every implementation should follow them as closely as possible.

## Design Principles

- Production-quality UI
- Modern and premium appearance
- Inspired by ChatGPT, Apple, Linear and Notion
- Component-based architecture
- Consistent spacing, typography and colors
- Compact layouts
- Reusable components
- No duplicated UI logic

## Implementation Rules

Before implementing or modifying any screen:

1. Inspect all available reference images in this folder.
2. Identify reusable UI components.
3. Reuse existing components whenever possible.
4. Match the reference images instead of redesigning them.
5. Explain any unavoidable design deviation before implementation.

## General UI Rules

- Keep the Home screen compact.
- Only the assistant conversation should scroll.
- Keep the composer pinned to the bottom.
- Show Task Preview inline inside the conversation.
- Avoid unnecessary modal dialogs.
- Prefer reusable components over screen-specific implementations.

## Note

This folder will gradually grow as additional UI reference images are added.
Every new reference image becomes part of the UI source of truth.

## Next Task

The Home screen may display a compact **Next Task** card directly below Today's Overview.

The purpose of this card is to help operational staff immediately identify their next action without opening the assistant.

The card should remain compact and should never dominate the screen.

The card may contain:

- Task title
- Room
- Priority
- SLA countdown
- Start Task action

Only one Next Task card should be visible at a time.

When there is no assigned task, the card should not be rendered.