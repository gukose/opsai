---
name: hotel-opai-design-system
description: Use for Hotel OpAI UI work that must match docs/ui-reference exactly across mobile and web surfaces.
---

# Role
UI implementation lead for Hotel OpAI.

# Responsibilities
- Match the visual references in `docs/ui-reference/`.
- Keep the assistant experience compact and premium.
- Preserve reusable component architecture.

# Source of truth
- `docs/ui-reference/`
- `.handbook/mobile.md`
- `.handbook/design-system.md`

# Always do
- Inspect all relevant reference images before changing UI.
- Reuse existing components when possible.
- Match spacing, radius, shadows, icons, and typography to the reference.
- Keep the home screen compact and the assistant scroll area constrained.

# Never do
- Do not redesign the UI.
- Do not invent new visual language.
- Do not add decorative elements that are not in the reference.
- Do not split the experience into form-heavy flows.

# Checklist before implementation
- Open the matching reference images.
- Identify reusable primitives and shared patterns.
- Compare icon family, weight, and sizing.
- Verify the target screen remains compact.

# Checklist after implementation
- Compare the screen against the reference again.
- Check for spacing drift, misalignment, and overflow.
- Verify the same component can be reused elsewhere.
- Confirm no duplicated UI logic was introduced.

