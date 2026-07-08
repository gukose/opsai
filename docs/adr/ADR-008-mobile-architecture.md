# ADR-008: Mobile Architecture

## Context
The mobile app must support a premium assistant experience, reusable UI components, and both mock and backend modes without duplicating logic.

## Decision
The mobile app will use a component-based React Native architecture with feature-level orchestration hooks and reusable assistant components. Backend mode will coexist with mock modes through a common assistant data source abstraction.

## Alternatives considered
- Giant screen components with inline logic
- Separate app variants for mock and backend modes
- A fully form-driven mobile experience

## Consequences
- UI stays maintainable and testable
- Backend integration can be switched without redesign
- Assistant rendering and interaction logic remain reusable

## Future impact
The mobile architecture can support voice, image, and richer task workflows without rewriting the assistant shell.

