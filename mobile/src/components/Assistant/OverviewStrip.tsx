import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Bell, ClipboardCheck, TriangleAlert } from "lucide-react-native";
import { ComponentType } from "react";
import { LucideProps } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { TaskBoardOverview } from "../../tasks/taskBoardSelectors";
import { resolveResponsiveLayout } from "../../layout/responsiveLayout";

type OverviewStripProps = TaskBoardOverview;

export function OverviewStrip({
  taskCount,
  urgentCount,
  completionPercent,
  unreadNotificationCount
}: OverviewStripProps) {
  const { width } = useWindowDimensions();
  const desktop = resolveResponsiveLayout(width).mode === "desktop";
  return (
    <View style={styles.wrap}>
      <Text style={styles.title}>Today's Overview</Text>
      <View style={styles.metrics}>
        <OverviewCard
          icon={ClipboardCheck}
          iconColor={colors.blue}
          iconBackground="#e9f1ff"
          title="Tasks"
          value={String(taskCount)}
        />
        <OverviewCard
          icon={TriangleAlert}
          iconColor="#f97316"
          iconBackground="#fff1e6"
          title="Urgent"
          value={String(urgentCount)}
        />
        {typeof unreadNotificationCount === "number" ? (
          <OverviewCard
            icon={Bell}
            iconColor="#0f766e"
            iconBackground="#e6f7f4"
            title="Unread"
            value={String(unreadNotificationCount)}
          />
        ) : null}
        <View style={[styles.progressCard, desktop ? styles.cardDesktop : null]}>
          <View style={styles.track}>
            <View style={[styles.progress, { width: `${Math.max(0, Math.min(100, completionPercent))}%` }]} />
          </View>
          <Text style={styles.percent}>{completionPercent}%</Text>
        </View>
      </View>
    </View>
  );
}

type OverviewCardProps = {
  icon: ComponentType<LucideProps>;
  iconColor: string;
  iconBackground: string;
  title: string;
  value: string;
};

function OverviewCard({
  icon: Icon,
  iconColor,
  iconBackground,
  title,
  value
}: OverviewCardProps) {
  return (
    <View style={styles.overviewCard}>
      <View style={[styles.iconWell, { backgroundColor: iconBackground }]}>
        <Icon color={iconColor} size={18} strokeWidth={2.35} />
      </View>
      <View style={styles.metricText}>
        <Text style={styles.metricTitle} numberOfLines={1}>{title}</Text>
        <Text style={styles.metricValue}>{value}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginHorizontal: spacing.xl,
    paddingTop: 7,
    paddingBottom: 8,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider
  },
  title: {
    marginBottom: 7,
    color: colors.text,
    fontSize: 12,
    fontWeight: "800"
  },
  metrics: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6
  },
  overviewCard: {
    flexGrow: 1,
    flexShrink: 1,
    flexBasis: 145,
    minWidth: 140,
    minHeight: 62,
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    paddingHorizontal: 7,
    ...shadow.soft
  },
  iconWell: {
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill
  },
  metricText: {
    flex: 1,
    marginLeft: 7,
    minWidth: 0
  },
  metricTitle: {
    color: colors.text,
    fontSize: 12,
    fontWeight: "900"
  },
  metricValue: {
    marginTop: 3,
    color: colors.nav,
    fontSize: 15,
    fontWeight: "900"
  },
  progressCard: {
    flexGrow: 1.35,
    flexShrink: 1,
    flexBasis: 170,
    minWidth: 150,
    minHeight: 62,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    ...shadow.soft
  },
  track: {
    flex: 1,
    height: 6,
    borderRadius: radius.pill,
    backgroundColor: "#e4e7ee"
  },
  progress: {
    height: 6,
    borderRadius: radius.pill,
    backgroundColor: "#d1d5db"
  },
  percent: {
    color: colors.nav,
    fontSize: 11,
    fontWeight: "900"
  },
  cardDesktop: {
    flexBasis: 220
  }
});
