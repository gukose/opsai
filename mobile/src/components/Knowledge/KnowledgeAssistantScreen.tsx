import { useEffect, useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { AlertCircle, BookOpen, CheckCircle2, ChevronDown, ChevronUp, Send } from "lucide-react-native";

import {
  getKnowledgeAnswerDashboard,
  getKnowledgeAnswerHistory,
  getKnowledgeProviderReadiness,
  KnowledgeAnswerDto,
  KnowledgeAnswerDashboardDto,
  KnowledgeAnswerFeedbackType,
  KnowledgeSearchMode,
  submitKnowledgeAnswerFeedback,
  submitKnowledgeQuestion
} from "../../api/knowledge/KnowledgeAssistantApi";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, radius, spacing, typography } from "../../theme/tokens";

type KnowledgeAssistantScreenProps = {
  accessToken: string | null;
  currentUser: CurrentUserSnapshot | null;
};

const modes: KnowledgeSearchMode[] = ["KEYWORD", "SEMANTIC", "HYBRID"];
const feedback: { type: KnowledgeAnswerFeedbackType; label: string }[] = [
  { type: "HELPFUL", label: "Helpful" },
  { type: "NOT_HELPFUL", label: "Not helpful" },
  { type: "INSUFFICIENT", label: "Insufficient" },
  { type: "INCORRECT_SOURCE", label: "Wrong source" }
];

export function KnowledgeAssistantScreen({ accessToken }: KnowledgeAssistantScreenProps) {
  const [question, setQuestion] = useState("");
  const [mode, setMode] = useState<KnowledgeSearchMode>("HYBRID");
  const [answer, setAnswer] = useState<KnowledgeAnswerDto | null>(null);
  const [history, setHistory] = useState<KnowledgeAnswerDto[]>([]);
  const [dashboard, setDashboard] = useState<KnowledgeAnswerDashboardDto | null>(null);
  const [readiness, setReadiness] = useState<string>("UNKNOWN");
  const [expandedCitationIds, setExpandedCitationIds] = useState<Set<string>>(new Set());
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const trimmedQuestion = question.trim();
  const canSubmit = Boolean(accessToken && trimmedQuestion.length >= 2 && !isLoading);
  const statusTone = useMemo(() => {
    if (answer?.status === "ANSWERED") return "success";
    if (answer?.status === "INSUFFICIENT_CONTEXT") return "warning";
    if (answer) return "failure";
    return "neutral";
  }, [answer]);

  useEffect(() => {
    if (!accessToken) return;
    void Promise.all([
      getKnowledgeAnswerHistory(accessToken).then(setHistory),
      getKnowledgeProviderReadiness(accessToken).then((result) => setReadiness(result.readiness)),
      getKnowledgeAnswerDashboard(accessToken).then(setDashboard)
    ]).catch((error) => {
      setMessage(error instanceof Error ? error.message : "Knowledge assistant is unavailable.");
    });
  }, [accessToken]);

  async function submit() {
    if (!accessToken || !canSubmit) return;
    setIsLoading(true);
    setMessage(null);
    try {
      const result = await submitKnowledgeQuestion(accessToken, trimmedQuestion, mode);
      setAnswer(result);
      setHistory((current) => [result, ...current.filter((item) => item.id !== result.id)].slice(0, 8));
      void getKnowledgeAnswerDashboard(accessToken).then(setDashboard).catch(() => undefined);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Knowledge answer request failed.");
    } finally {
      setIsLoading(false);
    }
  }

  async function sendFeedback(type: KnowledgeAnswerFeedbackType) {
    if (!accessToken || !answer) return;
    try {
      await submitKnowledgeAnswerFeedback(accessToken, answer.id, type);
      setMessage("Feedback saved.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Feedback could not be saved.");
    }
  }

  function toggleCitation(citationId: string) {
    setExpandedCitationIds((current) => {
      const next = new Set(current);
      if (next.has(citationId)) {
        next.delete(citationId);
      } else {
        next.add(citationId);
      }
      return next;
    });
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.headerRow}>
        <View>
          <Text style={styles.title}>Knowledge Assistant</Text>
          <Text style={styles.subtitle}>Internal, cited answers only</Text>
        </View>
        <View style={styles.readinessPill}>
          <BookOpen size={13} color={colors.green} />
          <Text style={styles.readinessText}>{readiness}</Text>
        </View>
      </View>

      <View style={styles.panel}>
        <TextInput
          value={question}
          onChangeText={setQuestion}
          placeholder="Ask from imported hotel knowledge"
          placeholderTextColor={colors.textSubtle}
          multiline
          style={styles.input}
        />
        <View style={styles.modeRow}>
          {modes.map((item) => (
            <Pressable
              key={item}
              accessibilityRole="button"
              onPress={() => setMode(item)}
              style={[styles.modeButton, mode === item && styles.modeButtonActive]}
            >
              <Text style={[styles.modeText, mode === item && styles.modeTextActive]}>{item}</Text>
            </Pressable>
          ))}
          <Pressable
            accessibilityRole="button"
            onPress={submit}
            disabled={!canSubmit}
            style={[styles.sendButton, !canSubmit && styles.disabledButton]}
          >
            <Send size={14} color="#fff" />
          </Pressable>
        </View>
      </View>

      {message ? <Text style={styles.message}>{message}</Text> : null}
      {isLoading ? <StateBanner tone="neutral" label="Searching knowledge and validating citations..." /> : null}
      {answer ? (
        <View style={styles.answerPanel}>
          <StateBanner
            tone={statusTone}
            label={
              answer.status === "ANSWERED"
                ? `Answered with ${answer.citations.length} citation${answer.citations.length === 1 ? "" : "s"}`
                : answer.status === "INSUFFICIENT_CONTEXT"
                  ? "Insufficient context"
                  : answer.failureCategory ?? "Provider failure"
            }
          />
          {answer.answerText ? <Text style={styles.answerText}>{answer.answerText}</Text> : null}
          {answer.citations.map((citation) => {
            const expanded = expandedCitationIds.has(citation.citationId);
            return (
              <View key={citation.citationId} style={styles.citation}>
                <Pressable accessibilityRole="button" onPress={() => toggleCitation(citation.citationId)} style={styles.citationHeader}>
                  <View style={styles.citationTitleWrap}>
                    <Text style={styles.citationTitle}>{citation.citationId} · {citation.title}</Text>
                    <Text style={styles.citationMeta}>{citation.category} · chunk {citation.chunkPosition} · {answer.retrievalMode}</Text>
                  </View>
                  {expanded ? <ChevronUp size={16} color={colors.nav} /> : <ChevronDown size={16} color={colors.nav} />}
                </Pressable>
                {expanded ? <Text style={styles.excerpt}>{citation.excerpt ?? "Excerpt unavailable."}</Text> : null}
              </View>
            );
          })}
          {answer.status === "ANSWERED" ? (
            <View style={styles.feedbackRow}>
              {feedback.map((item) => (
                <Pressable key={item.type} accessibilityRole="button" onPress={() => void sendFeedback(item.type)} style={styles.feedbackButton}>
                  <Text style={styles.feedbackText}>{item.label}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}
        </View>
      ) : null}

      {dashboard ? (
        <View style={styles.dashboardPanel}>
          <Text style={styles.sectionTitle}>Operations</Text>
          <View style={styles.metricGrid}>
            <Metric label="Retrieval" value={dashboard.retrievalReadiness.state} />
            <Metric label="Hourly" value={`${dashboard.quotaUsage.hourlyUsed}/${dashboard.quotaUsage.hourlyLimit}`} />
            <Metric label="Daily" value={`${dashboard.quotaUsage.dailyUsed}/${dashboard.quotaUsage.dailyLimit}`} />
            <Metric label="In flight" value={`${dashboard.quotaUsage.inFlightUsed}/${dashboard.quotaUsage.inFlightLimit}`} />
            <Metric label="Answered" value={String(dashboard.statusCounts.answered)} />
            <Metric label="Failed" value={String(dashboard.statusCounts.failed)} />
          </View>
          <Text style={styles.dashboardLine}>
            Feedback: helpful {dashboard.feedbackAnalytics.counts.HELPFUL ?? 0}, not helpful {dashboard.feedbackAnalytics.counts.NOT_HELPFUL ?? 0}, insufficient {dashboard.feedbackAnalytics.counts.INSUFFICIENT ?? 0}
          </Text>
          <Text style={styles.dashboardLine}>
            Recent failures: {safeSummary(dashboard.recentFailureCategories)}
          </Text>
        </View>
      ) : null}

      <View style={styles.historyPanel}>
        <Text style={styles.sectionTitle}>Recent answers</Text>
        {history.length === 0 ? (
          <Text style={styles.emptyText}>No retained answer history.</Text>
        ) : (
          history.map((item) => (
            <Pressable key={item.id} accessibilityRole="button" onPress={() => setAnswer(item)} style={styles.historyItem}>
              <Text style={styles.historyStatus}>{item.status}</Text>
              <Text style={styles.historyMeta}>{item.retrievalMode} · {formatTime(item.createdAt)} · {item.citations.length} citations</Text>
            </Pressable>
          ))
        )}
      </View>
    </ScrollView>
  );
}

function StateBanner({ tone, label }: { tone: "success" | "warning" | "failure" | "neutral"; label: string }) {
  const Icon = tone === "success" ? CheckCircle2 : AlertCircle;
  return (
    <View style={[styles.stateBanner, tone === "success" && styles.successBanner, tone === "warning" && styles.warningBanner, tone === "failure" && styles.failureBanner]}>
      <Icon size={14} color={tone === "failure" ? colors.red : tone === "success" ? colors.green : colors.amber} />
      <Text style={styles.stateText}>{label}</Text>
    </View>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue} numberOfLines={1}>{value}</Text>
    </View>
  );
}

function safeSummary(values: Record<string, number>): string {
  const entries = Object.entries(values).filter(([, count]) => count > 0).slice(0, 3);
  return entries.length === 0 ? "none" : entries.map(([key, count]) => `${key.toLowerCase()} ${count}`).join(", ");
}

function formatTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "recently" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

const styles = StyleSheet.create({
  container: {
    flex: 1
  },
  content: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xxl,
    gap: spacing.md
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: spacing.md
  },
  title: {
    color: colors.text,
    fontSize: typography.title,
    fontWeight: "900"
  },
  subtitle: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.body,
    fontWeight: "700"
  },
  readinessPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: radius.pill,
    backgroundColor: colors.greenSoft
  },
  readinessText: {
    color: colors.green,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  panel: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.sm,
    padding: spacing.md,
    backgroundColor: colors.surface
  },
  input: {
    minHeight: 78,
    color: colors.text,
    fontSize: 13,
    fontWeight: "700",
    textAlignVertical: "top"
  },
  modeRow: {
    marginTop: spacing.sm,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs
  },
  modeButton: {
    height: 30,
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceMuted
  },
  modeButtonActive: {
    backgroundColor: colors.greenSoft,
    borderWidth: 1,
    borderColor: colors.greenBorder
  },
  modeText: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  modeTextActive: {
    color: colors.green
  },
  sendButton: {
    width: 36,
    height: 30,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.sm,
    backgroundColor: colors.green
  },
  disabledButton: {
    opacity: 0.45
  },
  message: {
    color: colors.textMuted,
    fontSize: typography.body,
    fontWeight: "700"
  },
  answerPanel: {
    gap: spacing.sm
  },
  stateBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceMuted
  },
  successBanner: {
    backgroundColor: colors.greenSoft
  },
  warningBanner: {
    backgroundColor: "#fff7ed"
  },
  failureBanner: {
    backgroundColor: colors.redSoft
  },
  stateText: {
    flex: 1,
    minWidth: 0,
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  answerText: {
    color: colors.text,
    fontSize: 13,
    lineHeight: 19,
    fontWeight: "700"
  },
  citation: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.sm,
    backgroundColor: colors.surface
  },
  citationHeader: {
    minHeight: 46,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs
  },
  citationTitleWrap: {
    flex: 1,
    minWidth: 0
  },
  citationTitle: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  citationMeta: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  excerpt: {
    paddingHorizontal: spacing.sm,
    paddingBottom: spacing.sm,
    color: colors.textMuted,
    fontSize: typography.body,
    lineHeight: 15,
    fontWeight: "700"
  },
  feedbackRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.xs
  },
  feedbackButton: {
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceMuted
  },
  feedbackText: {
    color: colors.nav,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  historyPanel: {
    gap: spacing.xs
  },
  dashboardPanel: {
    gap: spacing.xs,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.sm,
    padding: spacing.sm,
    backgroundColor: colors.surface
  },
  metricGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.xs
  },
  metric: {
    width: "31%",
    minHeight: 42,
    justifyContent: "center",
    paddingHorizontal: spacing.xs,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceMuted
  },
  metricLabel: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  metricValue: {
    marginTop: 2,
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  dashboardLine: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  sectionTitle: {
    color: colors.text,
    fontSize: typography.subtitle,
    fontWeight: "900"
  },
  emptyText: {
    color: colors.textMuted,
    fontSize: typography.body,
    fontWeight: "700"
  },
  historyItem: {
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceMuted
  },
  historyStatus: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  historyMeta: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  }
});
