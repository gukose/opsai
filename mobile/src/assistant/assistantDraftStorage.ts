import { assistantDraftCacheKey, assistantDraftCacheKeyPrefix, defaultOfflineCache } from "../offline/offlineCache";
import type { OfflineScope } from "../offline/offlineTypes";
import {
  ASSISTANT_DRAFT_SCHEMA_VERSION,
  AssistantDraftInput,
  AssistantDraftSnapshot,
  hasAssistantDraftContent,
  normalizeDraft
} from "./assistantDraftNormalizer";

export async function saveAssistantDraft(scope: OfflineScope, draft: AssistantDraftInput): Promise<void> {
  if (!hasAssistantDraftContent(draft)) {
    await clearAssistantDraft(scope, draft.conversationId);
    return;
  }

  const normalized = normalizeDraft({
    schemaVersion: ASSISTANT_DRAFT_SCHEMA_VERSION,
    ...draft
  });
  await defaultOfflineCache.save(assistantDraftCacheKey(scope, draft.conversationId), normalized);
  if (draft.conversationId) {
    await defaultOfflineCache.save(assistantDraftCacheKey(scope, null), normalized);
  }
}

export async function loadAssistantDraft(
  scope: OfflineScope,
  conversationId?: string | null
): Promise<AssistantDraftSnapshot | null> {
  const cached = await loadDraftForKey(scope, conversationId);
  if (cached) {
    return cached;
  }

  if (conversationId) {
    const startupDraft = await loadDraftForKey(scope, null);
    if (!startupDraft) {
      return null;
    }

    const migratedDraft = normalizeDraft({
      ...startupDraft,
      conversationId
    });
    await defaultOfflineCache.save(assistantDraftCacheKey(scope, conversationId), migratedDraft);
    return migratedDraft;
  }

  return null;
}

export async function clearAssistantDraft(scope: OfflineScope, conversationId?: string | null): Promise<void> {
  await defaultOfflineCache.removeByPrefix(assistantDraftCacheKeyPrefix(scope));
  await defaultOfflineCache.remove(assistantDraftCacheKey(scope, conversationId));
  if (conversationId) {
    await defaultOfflineCache.remove(assistantDraftCacheKey(scope, null));
  }
}

async function loadDraftForKey(
  scope: OfflineScope,
  conversationId?: string | null
): Promise<AssistantDraftSnapshot | null> {
  const cached = await defaultOfflineCache.load<AssistantDraftSnapshot>(assistantDraftCacheKey(scope, conversationId));
  if (!cached || !cached.data || cached.data.schemaVersion !== ASSISTANT_DRAFT_SCHEMA_VERSION) {
    return null;
  }

  return normalizeDraft(cached.data);
}
