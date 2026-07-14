import { defaultOfflineCache, assistantDraftCacheKey } from "../offline/offlineCache";
import { OfflineScope } from "../offline/offlineTypes";
import {
  ASSISTANT_DRAFT_SCHEMA_VERSION,
  AssistantDraftInput,
  AssistantDraftSnapshot,
  normalizeDraft
} from "./assistantDraftNormalizer";

export async function saveAssistantDraft(scope: OfflineScope, draft: AssistantDraftInput): Promise<void> {
  await defaultOfflineCache.save(assistantDraftCacheKey(scope, draft.conversationId), normalizeDraft({
    schemaVersion: ASSISTANT_DRAFT_SCHEMA_VERSION,
    ...draft
  }));
}

export async function loadAssistantDraft(
  scope: OfflineScope,
  conversationId?: string | null
): Promise<AssistantDraftSnapshot | null> {
  const cached = await defaultOfflineCache.load<AssistantDraftSnapshot>(assistantDraftCacheKey(scope, conversationId));
  if (!cached || cached.data.schemaVersion !== ASSISTANT_DRAFT_SCHEMA_VERSION) {
    return null;
  }

  return normalizeDraft(cached.data);
}

export async function clearAssistantDraft(scope: OfflineScope, conversationId?: string | null): Promise<void> {
  await defaultOfflineCache.remove(assistantDraftCacheKey(scope, conversationId));
}
