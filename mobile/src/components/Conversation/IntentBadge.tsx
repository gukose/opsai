import { StyleSheet, Text, View } from "react-native";
import { ComponentType } from "react";
import {
  Archive,
  Bed,
  LucideProps,
  UserRound,
  Wrench
} from "lucide-react-native";

import { colors, radius, spacing, typography } from "../../theme/tokens";
import { IntentBadgeMessage } from "../../assistant/types";

const toneIcon: Record<IntentBadgeMessage["tone"], ComponentType<LucideProps>> = {
  guest: UserRound,
  maintenance: Wrench,
  housekeeping: Bed,
  lostFound: Archive
};

const toneColor: Record<IntentBadgeMessage["tone"], string> = {
  guest: colors.blue,
  maintenance: colors.blue,
  housekeeping: "#0f766e",
  lostFound: colors.purple
};

type IntentBadgeProps = {
  label: string;
  tone: IntentBadgeMessage["tone"];
};

export function IntentBadge({ label, tone }: IntentBadgeProps) {
  const Icon = toneIcon[tone];

  return (
    <View style={styles.badge}>
      <Icon color={toneColor[tone]} size={10} strokeWidth={2.35} />
      <Text style={styles.label}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: "center",
    minHeight: 18,
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface
  },
  label: {
    color: colors.nav,
    fontSize: typography.tiny,
    fontWeight: "800"
  }
});
