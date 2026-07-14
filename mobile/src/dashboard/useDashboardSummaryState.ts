import { useCallback, useEffect, useMemo, useState } from "react";

import { assistantDataSourceMode } from "../config/assistantConfig";
import { CurrentUserSnapshot } from "../session/sessionTypes";
import { dashboardCacheKey, defaultOfflineCache } from "../offline/offlineCache";
import { DashboardService } from "./DashboardService";
import { DashboardSummary } from "./types";

type DashboardSummaryState = {
  summary: DashboardSummary | null;
  staleReason: string | null;
  cachedAt: string | null;
  refreshDashboard: () => Promise<void>;
};

export function useDashboardSummaryState(
  accessToken: string | null,
  currentUser?: CurrentUserSnapshot | null
): DashboardSummaryState {
  const isBackendMode = assistantDataSourceMode === "backend";
  const service = useMemo(
    () =>
      new DashboardService(() => {
        return accessToken;
      }),
    [accessToken]
  );
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [staleReason, setStaleReason] = useState<string | null>(null);
  const [cachedAt, setCachedAt] = useState<string | null>(null);

  const refreshDashboard = useCallback(async () => {
    if (!accessToken || !isBackendMode) {
      setSummary(null);
      setStaleReason(null);
      setCachedAt(null);
      return;
    }

    try {
      const nextSummary = await service.getSummary();
      setSummary(nextSummary);
      setStaleReason(null);
      setCachedAt(null);
      const key = scopedDashboardCacheKey(currentUser);
      if (key) {
        void defaultOfflineCache.save(key, nextSummary);
      }
    } catch {
      const key = scopedDashboardCacheKey(currentUser);
      if (!summary && key) {
        const cached = await defaultOfflineCache.load<DashboardSummary>(key);
        if (cached) {
          setSummary(cached.data);
          setCachedAt(cached.cachedAt);
          setStaleReason("Refresh failed. Showing last saved data.");
          return;
        }
      }
      setStaleReason(summary ? "Refresh failed. Showing last saved data." : "No saved data is available offline.");
    }
  }, [accessToken, currentUser, isBackendMode, service, summary]);

  useEffect(() => {
    void refreshDashboard();
  }, [refreshDashboard]);

  return {
    summary,
    staleReason,
    cachedAt,
    refreshDashboard
  };
}

function scopedDashboardCacheKey(currentUser: CurrentUserSnapshot | null | undefined): string | null {
  if (!currentUser?.hotelId || !currentUser.userId) {
    return null;
  }

  return dashboardCacheKey({
    hotelId: currentUser.hotelId,
    userId: currentUser.userId
  });
}
