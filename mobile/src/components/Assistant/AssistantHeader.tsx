import { StyleSheet, Text, View } from "react-native";
import { Bell } from "lucide-react-native";

import { assistantDataSourceMode } from "../../config/assistantConfig";
import { colors, radius, spacing, typography } from "../../theme/tokens";
import { IconButton } from "../ui/IconButton";

type AssistantHeaderProps = {
  onReset?: () => void;
};

export function AssistantHeader({ onReset }: AssistantHeaderProps) {
  return (
    <View style={styles.header}>
      <View style={styles.content}>
        <View>
          <Text style={styles.greeting}>👋 Good Morning, Ayse</Text>
          <Text style={styles.role}>Housekeeping Attendant</Text>
          <Text style={styles.shift}>Morning Shift · Floor 3</Text>
          {__DEV__ ? (
            <View style={styles.modePill}>
              <Text style={styles.modeLabel}>assistant mode</Text>
              <Text style={styles.modeValue}>{assistantDataSourceMode}</Text>
            </View>
          ) : null}
        </View>
        <IconButton
          icon={Bell}
          label="Reset assistant"
          onPress={onReset}
          color={colors.text}
          size={16}
          style={styles.notification}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    minHeight: 64,
    paddingHorizontal: spacing.xl,
    paddingTop: 2
  },
  content: {
    minHeight: 54,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between"
  },
  greeting: {
    color: colors.text,
    fontSize: typography.title,
    fontWeight: "800"
  },
  role: {
    marginTop: spacing.xxs,
    color: colors.text,
    fontSize: typography.subtitle,
    fontWeight: "800"
  },
  shift: {
    marginTop: spacing.xxs,
    color: colors.nav,
    fontSize: typography.subtitle,
    fontWeight: "700"
  },
  modePill: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    gap: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderWidth: 1,
    borderColor: "#d8dee9",
    borderRadius: radius.pill,
    backgroundColor: "#f7f9fc"
  },
  modeLabel: {
    color: colors.textMuted,
    fontSize: 9,
    fontWeight: "700",
    textTransform: "uppercase"
  },
  modeValue: {
    color: colors.text,
    fontSize: 9,
    fontWeight: "800",
    textTransform: "lowercase"
  },
  notification: {
    width: 28,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface
  }
});
