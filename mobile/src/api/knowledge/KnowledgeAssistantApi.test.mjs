import assert from "node:assert/strict";
import test from "node:test";

import {
  getKnowledgeAnswerDashboard,
  getKnowledgeAnswerHistory,
  getKnowledgeProviderReadiness,
  submitKnowledgeAnswerFeedback,
  submitKnowledgeQuestion
} from "./KnowledgeAssistantApi.ts";

test("knowledge assistant API uses internal endpoints with bearer auth", async () => {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return jsonResponse({
      id: "answer-1",
      providerId: "internal-demo",
      modelPresent: true,
      retrievalMode: "HYBRID",
      status: "ANSWERED",
      confidence: "MEDIUM",
      answerText: "Use the cited SOP.",
      citations: [{ citationId: "K1", title: "SOP", category: "HOUSEKEEPING", chunkPosition: 1, retrievalScore: 1, excerpt: "Bounded source." }],
      failureCategory: null,
      createdAt: "2026-08-01T10:00:00Z"
    });
  };

  const answer = await submitKnowledgeQuestion("token-1", "What is the SOP?", "HYBRID");

  assert.equal(answer.status, "ANSWERED");
  assert.equal(calls[0].url.endsWith("/api/v1/internal/knowledge/answers/test-query"), true);
  assert.equal(calls[0].init.headers.Authorization, "Bearer token-1");
  assert.equal(JSON.parse(calls[0].init.body).query, "What is the SOP?");
});

test("knowledge assistant API fetches history readiness and feedback through internal-only routes", async () => {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).includes("dashboard")) {
      return jsonResponse({
        providerReadiness: { providerId: "internal-demo", readiness: "READY", lifecycle: "AVAILABLE", active: true, enabled: true, endpointClassification: "INVALID", productionUseBlocked: true, blockingReasons: [], modelPresent: true },
        retrievalReadiness: { state: "READY", embeddedPercentage: 100, blockingReasons: [] },
        recentAnswerCount: 2,
        statusCounts: { answered: 1, insufficientContext: 1, failed: 0 },
        quotaUsage: { hourlyLimit: 60, hourlyUsed: 2, dailyLimit: 250, dailyUsed: 2, inFlightLimit: 2, inFlightUsed: 0 },
        activeInFlightCount: 0,
        abandonedRequestCount: 0,
        feedbackAnalytics: { counts: { HELPFUL: 1 }, rates: { HELPFUL: 1 } },
        citationCountBands: { one_two: 1 },
        latencyBands: { under_100ms: 1 },
        recentFailureCategories: {}
      });
    }
    if (String(url).includes("provider")) {
      return jsonResponse({ providerId: "internal-demo", readiness: "READY", lifecycle: "AVAILABLE", active: true, enabled: true, endpointClassification: "INVALID", productionUseBlocked: true, blockingReasons: [], modelPresent: true });
    }
    if (String(url).includes("feedback")) {
      return jsonResponse({ feedbackType: "HELPFUL", createdAt: "2026-08-01T10:00:00Z" });
    }
    return jsonResponse([]);
  };

  const history = await getKnowledgeAnswerHistory("token-2");
  const readiness = await getKnowledgeProviderReadiness("token-2");
  const dashboard = await getKnowledgeAnswerDashboard("token-2");
  const feedback = await submitKnowledgeAnswerFeedback("token-2", "answer-1", "HELPFUL");

  assert.deepEqual(history, []);
  assert.equal(readiness.readiness, "READY");
  assert.equal(dashboard.quotaUsage.inFlightLimit, 2);
  assert.equal(feedback.feedbackType, "HELPFUL");
  assert.equal(calls.every((call) => call.url.includes("/api/v1/internal/knowledge/answers")), true);
});

function jsonResponse(body) {
  return {
    ok: true,
    status: 200,
    json: async () => body
  };
}
