import { useCallback, useEffect, useMemo, useState } from "react";

import { assistantDataSourceMode } from "../config/assistantConfig";
import { DashboardService } from "./DashboardService";
import { TaskReportingSummary } from "./types";

type TaskReportingState = {
  report: TaskReportingSummary | null;
  isLoading: boolean;
  errorMessage: string | null;
  refreshReport: () => Promise<void>;
};

export function useTaskReportingState(accessToken: string | null): TaskReportingState {
  const isBackendMode = assistantDataSourceMode === "backend";
  const service = useMemo(
    () =>
      new DashboardService(() => {
        return accessToken;
      }),
    [accessToken]
  );
  const [report, setReport] = useState<TaskReportingSummary | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const refreshReport = useCallback(async () => {
    if (!accessToken || !isBackendMode) {
      setReport(null);
      setErrorMessage(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const nextReport = await service.getTaskReport("today");
      setReport(nextReport);
      setErrorMessage(null);
    } catch {
      setErrorMessage("Reporting unavailable");
    } finally {
      setIsLoading(false);
    }
  }, [accessToken, isBackendMode, service]);

  useEffect(() => {
    void refreshReport();
  }, [refreshReport]);

  return {
    report,
    isLoading,
    errorMessage,
    refreshReport
  };
}
