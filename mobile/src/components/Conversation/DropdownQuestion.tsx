import { Pressable, StyleSheet, Text, View } from "react-native";
import { Check, ChevronDown } from "lucide-react-native";

import { colors, radius, spacing, typography } from "../../theme/tokens";
import { ActionQuestion } from "../../assistant/types";

type DropdownQuestionProps = {
  actions: ActionQuestion["actions"];
  onActionPress?: (action: ActionQuestion["actions"][number]) => void;
  disabled?: boolean;
};

export function DropdownQuestion({ actions, onActionPress, disabled }: DropdownQuestionProps) {
  return (
    <View style={styles.row}>
      {actions.map((action) => (
        <Pressable
          key={action.id}
          accessibilityRole="button"
          disabled={disabled}
          onPress={() => onActionPress?.(action)}
          style={({ pressed }) => [
            styles.button,
            action.variant === "confirm" ? styles.confirm : styles.secondary,
            pressed && !disabled ? styles.pressed : null,
            disabled ? styles.disabled : null
          ]}
        >
          <View style={styles.buttonContent}>
            {action.variant === "confirm" ? (
              <Check color={colors.green} size={12} strokeWidth={2.5} />
            ) : null}
            <Text
              style={[
                styles.label,
                action.variant === "confirm" ? styles.confirmLabel : styles.secondaryLabel
              ]}
            >
              {action.label}
            </Text>
            {action.variant === "secondary" ? (
              <ChevronDown color={colors.nav} size={12} strokeWidth={2.3} />
            ) : null}
          </View>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    justifyContent: "center",
    gap: 6,
    marginTop: 3,
    marginBottom: 6
  },
  button: {
    minHeight: 24,
    justifyContent: "center",
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: 10
  },
  pressed: {
    opacity: 0.88
  },
  disabled: {
    opacity: 0.55
  },
  buttonContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4
  },
  confirm: {
    borderColor: colors.greenBorder,
    backgroundColor: colors.surface
  },
  secondary: {
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface
  },
  label: {
    fontSize: typography.caption,
    fontWeight: "900"
  },
  confirmLabel: {
    color: colors.green
  },
  secondaryLabel: {
    color: colors.nav
  }
});
