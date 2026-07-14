import { StyleSheet, Text, View } from "react-native";

import { TaskReportingSummary } from "../../dashboard/types";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";

type ReportingSectionProps = {
  report: TaskReportingSummary | null;
  isLoading: boolean;
  errorMessage: string | null;
};

export function ReportingSection({ report, isLoading, errorMessage }: ReportingSectionProps) {
  if (errorMessage) {
    return (
      <View style={styles.wrap}>
        <Text style={styles.title}>Reporting</Text>
        <Text style={styles.muted}>Reporting unavailable</Text>
      </View>
    );
  }

  if (!report) {
    return isLoading ? (
      <View style={styles.wrap}>
        <Text style={styles.title}>Reporting</Text>
        <Text style={styles.muted}>Loading reporting...</Text>
      </View>
    ) : null;
  }

  return (
    <View style={styles.wrap}>
      <View style={styles.headerRow}>
        <Text style={styles.title}>Reporting</Text>
        <Text style={styles.range}>{rangeLabel(report.range)} · {report.window.timeBasis}</Text>
      </View>
      <View style={styles.columns}>
        <View style={styles.panel}>
          <Text style={styles.panelTitle}>Created in range</Text>
          <MetricLine label="Total" value={report.createdInRange.total} />
          <MetricLine label="Type" value={topBucket(report.createdInRange.byType)} />
          <MetricLine label="Status" value={topBucket(report.createdInRange.byStatus)} />
          <MetricLine label="Priority" value={topBucket(report.createdInRange.byPriority)} />
          <MetricLine label="Late" value={report.createdInRange.sla.completedLate} />
          <MetricLine label="Open overdue" value={report.createdInRange.sla.openOverdue} />
        </View>
        <View style={styles.panel}>
          <Text style={styles.panelTitle}>Current snapshot</Text>
          <MetricLine label="Active" value={report.currentSnapshot.active} />
          <MetricLine label="Status" value={topBucket(report.currentSnapshot.byStatus)} />
          <MetricLine label="Priority" value={topBucket(report.currentSnapshot.byPriority)} />
          <MetricLine label="Due soon" value={report.currentSnapshot.sla.dueSoon} />
          <MetricLine label="Overdue" value={report.currentSnapshot.sla.overdue} />
        </View>
      </View>
    </View>
  );
}

function MetricLine({ label, value }: { label: string; value: number | string }) {
  return (
    <View style={styles.metricLine}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>{String(value)}</Text>
    </View>
  );
}

function topBucket(buckets: TaskReportingSummary["createdInRange"]["byType"]): string {
  const top = buckets.filter((bucket) => bucket.count > 0).sort((a, b) => b.count - a.count)[0];
  return top ? `${top.label} ${top.count}` : "None";
}

function rangeLabel(range: string): string {
  switch (range) {
    case "shift":
      return "Shift";
    case "7d":
      return "7 days";
    default:
      return "Today";
  }
}

const styles = StyleSheet.create({
  wrap: {
    marginHorizontal: spacing.xl,
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: colors.divider
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6
  },
  title: {
    color: colors.text,
    fontSize: 12,
    fontWeight: "900"
  },
  range: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  columns: {
    flexDirection: "row",
    gap: 7
  },
  panel: {
    flex: 1,
    minHeight: 110,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    paddingHorizontal: 8,
    paddingVertical: 7,
    ...shadow.soft
  },
  panelTitle: {
    marginBottom: 5,
    color: colors.nav,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  metricLine: {
    minHeight: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 6
  },
  metricLabel: {
    flex: 1,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  metricValue: {
    color: colors.text,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  muted: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  }
});
