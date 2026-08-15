import { appApiBaseUrl } from "../../config/appConfig";

export type KnowledgeSearchMode = "KEYWORD" | "SEMANTIC" | "HYBRID";
export type KnowledgeAnswerStatus =
  | "ANSWERED"
  | "INSUFFICIENT_CONTEXT"
  | "PROVIDER_DISABLED"
  | "FAILED_VALIDATION"
  | "PROVIDER_FAILURE";

export type KnowledgeAnswerCitationDto = {
  citationId: string;
  title: string;
  category: string;
  chunkPosition: number;
  retrievalScore: number;
  excerpt: string | null;
};

export type KnowledgeAnswerDto = {
  id: string;
  providerId: string;
  modelPresent: boolean;
  retrievalMode: KnowledgeSearchMode;
  status: KnowledgeAnswerStatus;
  confidence: "LOW" | "MEDIUM" | "HIGH" | null;
  answerText: string | null;
  citations: KnowledgeAnswerCitationDto[];
  failureCategory: string | null;
  createdAt: string;
};

export type KnowledgeAnswerProviderReadinessDto = {
  providerId: string;
  readiness: string;
  lifecycle: string;
  active: boolean;
  enabled: boolean;
  endpointClassification: string;
  productionUseBlocked: boolean;
  blockingReasons: string[];
  modelPresent: boolean;
};

export type KnowledgeAnswerDashboardDto = {
  providerReadiness: KnowledgeAnswerProviderReadinessDto;
  retrievalReadiness: {
    state: string;
    embeddedPercentage: number;
    blockingReasons: string[];
  };
  recentAnswerCount: number;
  statusCounts: {
    answered: number;
    insufficientContext: number;
    failed: number;
  };
  quotaUsage: {
    hourlyLimit: number;
    hourlyUsed: number;
    dailyLimit: number;
    dailyUsed: number;
    inFlightLimit: number;
    inFlightUsed: number;
  };
  activeInFlightCount: number;
  abandonedRequestCount: number;
  feedbackAnalytics: {
    counts: Record<string, number>;
    rates: Record<string, number>;
  };
  citationCountBands: Record<string, number>;
  latencyBands: Record<string, number>;
  recentFailureCategories: Record<string, number>;
};

export type KnowledgeAnswerFeedbackType = "HELPFUL" | "NOT_HELPFUL" | "INSUFFICIENT" | "INCORRECT_SOURCE";

export async function submitKnowledgeQuestion(
  accessToken: string,
  query: string,
  retrievalMode: KnowledgeSearchMode
): Promise<KnowledgeAnswerDto> {
  return request("/api/v1/internal/knowledge/answers/test-query", accessToken, {
    method: "POST",
    body: JSON.stringify({ query, retrievalMode })
  });
}

export async function getKnowledgeAnswerHistory(accessToken: string): Promise<KnowledgeAnswerDto[]> {
  return request("/api/v1/internal/knowledge/answers?page=0&size=8", accessToken);
}

export async function getKnowledgeProviderReadiness(accessToken: string): Promise<KnowledgeAnswerProviderReadinessDto> {
  return request("/api/v1/internal/knowledge/answers/providers/internal-demo/readiness", accessToken);
}

export async function getKnowledgeAnswerDashboard(accessToken: string): Promise<KnowledgeAnswerDashboardDto> {
  return request("/api/v1/internal/knowledge/answers/dashboard", accessToken);
}

export async function submitKnowledgeAnswerFeedback(
  accessToken: string,
  answerId: string,
  feedbackType: KnowledgeAnswerFeedbackType
): Promise<{ feedbackType: KnowledgeAnswerFeedbackType; createdAt: string }> {
  return request(`/api/v1/internal/knowledge/answers/${encodeURIComponent(answerId)}/feedback`, accessToken, {
    method: "POST",
    body: JSON.stringify({ feedbackType })
  });
}

async function request<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${appApiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
      ...(init.headers ?? {})
    }
  });
  if (!response.ok) {
    throw new Error(`Knowledge request failed with status ${response.status}`);
  }
  return response.json() as Promise<T>;
}
