import { useCallback, useEffect, useMemo, useState } from "react";

import { assistantDataSourceMode } from "../config/assistantConfig";
import { DashboardService } from "./DashboardService";
import { DashboardSummary } from "./types";

type DashboardSummaryState = {
  summary: DashboardSummary | null;
  refreshDashboard: () => Promise<void>;
};

export function useDashboardSummaryState(accessToken: string | null): DashboardSummaryState {
  const isBackendMode = assistantDataSourceMode === "backend";
  const service = useMemo(
    () =>
      new DashboardService(() => {
        return accessToken;
      }),
    [accessToken]
  );
  const [summary, setSummary] = useState<DashboardSummary | null>(null);

  const refreshDashboard = useCallback(async () => {
    if (!accessToken || !isBackendMode) {
      setSummary(null);
      return;
    }

    try {
      setSummary(await service.getSummary());
    } catch {
      setSummary(null);
    }
  }, [accessToken, isBackendMode, service]);

  useEffect(() => {
    void refreshDashboard();
  }, [refreshDashboard]);

  return {
    summary,
    refreshDashboard
  };
}
