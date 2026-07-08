# ADR-009: UI Source of Truth

## Context
Visual fidelity matters, and the implementation must not drift from the approved product references.

## Decision
`docs/ui-reference/` is the source of truth for UI implementation. Screens and reusable components must match the references rather than being redesigned.

## Alternatives considered
- Treat implementation as the source of truth
- Allow design interpretation per feature
- Use a separate style guide instead of reference images

## Consequences
- Visual changes become deliberate and reviewable
- UI work is constrained by reference fidelity
- Component reuse becomes easier to enforce

## Future impact
Additional reference images can be added over time and the UI can evolve without ambiguity about the intended appearance.

