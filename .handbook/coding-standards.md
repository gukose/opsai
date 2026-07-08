# Coding Standards

## General Rules

- Prefer SOLID principles.
- Use Clean Architecture boundaries.
- Keep classes small.
- Prefer composition over inheritance.
- Avoid duplicated logic.

## Kotlin

- Use idiomatic Kotlin.
- Use data classes and value objects where appropriate.
- Keep domain validation inside the domain.

## React Native

- Build reusable components.
- Keep screens thin.
- Put state and orchestration in feature hooks or controllers.

## Review Standard

If a change introduces duplicated logic, unnecessary coupling, or ownership drift, refactor it before merging.

