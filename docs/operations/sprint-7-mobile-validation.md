# Sprint 7 Manual Mobile Validation Checklist

Use this checklist for final Sprint 7 manual validation. Mark checks as passed only when they are actually performed against the target build.

## Session and Mode

- Log in successfully.
- Validate backend mode.
- Validate static mode.
- Validate local mode.
- Log out successfully.
- Confirm current user scope is cleared after logout.
- Confirm user/hotel scope isolation where test accounts support it.

## Image Selection and Local Preview

- Select an image from gallery.
- Select an image from camera where the platform supports camera access.
- Confirm the selected image shows a local preview.
- Confirm preview wording says “Local preview only”.
- Confirm the selected item enters `LOCAL_SELECTED`.
- Confirm no backend call is made by selection alone.
- Confirm no analysis call is made by selection alone.
- Confirm no task is created by selection alone.

## Metadata Registration

- Start registration and confirm `REGISTERING` state is visible.
- Confirm successful registration enters `REGISTERED`.
- Confirm UI says “Registered metadata”.
- Confirm registration failure enters a visible retry state.
- Retry registration manually after a failure.
- Confirm reconnect alone does not retry registration.
- Confirm no fake progress percentage appears.
- Confirm UI never says “Uploaded”.
- Confirm UI never says “Stored”.
- Confirm UI never says “Upload complete”.
- Confirm no download/open-from-server action appears.

## Draft, Refresh, and Offline Behavior

- Refresh/restart with a selected local attachment and confirm draft restoration where the platform can preserve the local preview reference.
- Refresh/restart with a registered attachment and confirm the server `attachmentId` restores.
- Confirm restored draft does not auto-register.
- Confirm restored draft does not auto-send.
- Confirm reconnect does not replay registration.
- Confirm reconnect does not replay message send.
- Confirm failed registration preserves text.
- Confirm failed registration preserves transcript.
- Confirm failed registration preserves image notes.
- Confirm failed registration preserves attachment metadata and order.

## Assistant Message Flow

- Send a message with a registered attachment.
- Confirm the request uses `attachmentIds`.
- Confirm the request body contains no local URI.
- Confirm the request body contains no local reference as durable media.
- Confirm the request body contains no base64.
- Confirm the request body contains no raw binary.
- Confirm the request body contains no storage/provider URL.
- Add an image note and confirm it remains `USER_PROVIDED`.
- Confirm there is no fake server vision wording.
- Confirm normal required-field follow-up still appears when information is missing.
- Confirm a task preview appears before task creation.
- Confirm explicit confirmation is required.
- Confirm task is created only after confirmation.

## Task Attachment Display

- Open task detail after confirming a task with a registered attachment.
- Confirm task detail displays attachment metadata.
- Confirm task detail displays `ASSISTANT_MESSAGE` provenance where applicable.
- Confirm task detail displays `VISION_ANALYSIS` provenance where a valid deterministic test path is available.
- Confirm task detail says “Registered metadata”.
- Confirm task detail shows no download button.
- Confirm task detail shows no server thumbnail.
- Confirm task detail shows no storage reference.
- Confirm task detail shows no provider payload.
- Confirm task SLA remains visible.
- Confirm task status remains visible.

## Network Inspection

- Inspect attachment registration request bodies and confirm no local URI is sent.
- Inspect assistant message request bodies and confirm no local URI is sent.
- Confirm no base64 or raw binary appears in request bodies.
- Confirm no storage URL or provider URL appears in request bodies.
- Confirm no client hotel/user ownership fields are sent for attachment registration.

## Notes

- `REGISTERED` means backend-owned metadata identity only.
- Local previews are device/browser local only.
- Sprint 7 does not upload image bytes.
- Sprint 7 does not provide server thumbnails or download/open-from-server behavior.
- Sprint 7 does not call OpenAI Vision or any external vision provider.
