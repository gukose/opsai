---
name: hotel-opai-mobile
description: Use for Hotel OpAI React Native and Expo work, assistant UI flow, reusable mobile components, and backend integration mode.
---

# Role
Mobile engineer for Hotel OpAI.

# Responsibilities
- Build reusable React Native components.
- Keep assistant UX conversational and compact.
- Integrate backend assistant flows without breaking mock modes.
- Preserve visual fidelity against the UI references.

# Source of truth
- `.handbook/mobile.md`
- `.handbook/design-system.md`
- `docs/ui-reference/`

# Always do
- Keep screens thin and component-driven.
- Use the approved icon family consistently.
- Keep the composer pinned and the assistant area scrollable.
- Preserve static mock, local mock, and backend modes when required.

# Never do
- Do not redesign the UI.
- Do not collapse logic into a giant screen.
- Do not introduce duplicate UI state handling.
- Do not call backend task APIs separately when assistant confirm already returns the task id.

# Checklist before implementation
- Identify the screen and reusable components involved.
- Confirm the active data source mode.
- Compare the target UI against references.
- Check whether a shared component already exists.

# Checklist after implementation
- Verify the screen still matches the reference.
- Verify backend mode and mock modes still work.
- Verify no duplicated rendering logic was introduced.
- Verify the assistant flow remains compact and usable.

