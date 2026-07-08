# Sprint 7 - Vision Pipeline

## Goal
Prepare and integrate the Vision pipeline so image analysis enters the Conversation Engine.

## Business value
Lets staff attach photos to describe maintenance, housekeeping, or service issues faster and more accurately.

## Architecture impact
- Adds image analysis as an input adapter into the Conversation Engine.
- Introduces attachment and storage foundations needed for later production Blob Storage.
- Keeps task creation behind preview and confirmation.

## Backend tasks
- Add attachment upload metadata and image-analysis request handling.
- Route image analysis results into Conversation Engine messages and slots.
- Link confirmed tasks to source images and analysis metadata.

## Mobile tasks
- Add camera/gallery selection, image preview, upload progress, retry, and attachment display on conversation/task screens.
- Support user correction of inferred issue details.

## AI tasks
- Add vision analysis abstraction and provider adapter.
- Return structured observations, suggested issue type, location hints, confidence, and clarification prompts.
- Validate results before conversation state changes.

## UniMock tasks
- Use UniMock assets, rooms, and issue types for vision-context validation.
- No scenario execution.

## Database tasks
- Add Flyway migrations for attachments, image-analysis metadata, provider metadata, and task attachment links.
- Store only metadata and storage references in PostgreSQL, not large binary files.

## Infrastructure tasks
- Prepare Blob Storage abstraction and local storage strategy.
- Configure upload size limits, content-type validation, antivirus/malware scanning plan, and retention rules.

## UI tasks
- Add image attachment and analysis states to assistant and task screens.
- Ensure photos do not crowd task details or hide critical status/SLA information.

## Documentation tasks
- Document vision pipeline, attachment lifecycle, storage abstraction, validation rules, and privacy expectations.

## Testing tasks
- Verify upload validation and storage-reference creation.
- Verify image analysis enters Conversation Engine.
- Verify low-confidence analysis asks follow-up questions.
- Verify confirmed task links back to attachments.

## Risks
- Image storage and privacy policies can become production blockers if postponed.
- Vision confidence may vary widely by lighting, angle, and hotel-specific assets.

## Definition of Done
- Images can be attached, analyzed, reviewed, and used in task preview.
- Vision results cannot create tasks without confirmation.
- Storage abstraction is ready for Blob Storage integration.

## Dependencies on previous sprints
- Depends on Sprint 5 AI abstraction, Sprint 4 Conversation Engine, and Sprint 2 UniMock PMS context.
