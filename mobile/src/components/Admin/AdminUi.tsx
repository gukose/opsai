import { ReactNode } from "react";
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { colors, radius, spacing, typography } from "../../theme/tokens";
export function AdminButton({
  label,
  onPress,
  disabled = false,
  tone = "primary",
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  tone?: "primary" | "secondary" | "danger";
}) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.button,
        tone === "secondary" && styles.secondary,
        tone === "danger" && styles.danger,
        disabled && styles.disabled,
      ]}
    >
      <Text
        style={[
          styles.buttonText,
          tone === "secondary" && styles.secondaryText,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}
export function AdminCard({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>{title}</Text>
      {children}
    </View>
  );
}
export function AdminModal({
  visible,
  title,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <KeyboardAvoidingView
        style={styles.backdrop}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View style={styles.modal} testID="admin-scrollable-modal">
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>{title}</Text>
            <AdminButton label="Close" tone="secondary" onPress={onClose} />
          </View>
          <View style={styles.modalContent}>{children}</View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
export const adminStyles = StyleSheet.create({
  input: {
    width: "100%",
    minWidth: 0,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 12,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  label: { fontSize: 10, fontWeight: "800", color: colors.textMuted },
  row: {
    minWidth: 0,
    paddingVertical: 9,
    borderBottomWidth: 1,
    borderColor: colors.divider,
  },
  rowTitle: { fontSize: 12, fontWeight: "800", color: colors.text, flexShrink: 1 },
  rowDetail: { fontSize: 10, color: colors.textMuted, marginTop: 2, flexShrink: 1 },
  actions: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  error: {
    padding: 8,
    borderRadius: radius.md,
    backgroundColor: colors.redSoft,
    color: colors.red,
    fontSize: 11,
    fontWeight: "700",
  },
  help: { fontSize: 10, color: colors.textMuted },
  selected: {
    backgroundColor: colors.greenSoft,
    borderColor: colors.greenBorder,
  },
});
const styles = StyleSheet.create({
  button: {
    alignSelf: "flex-start",
    minHeight: 40,
    justifyContent: "center",
    backgroundColor: colors.green,
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  secondary: {
    backgroundColor: colors.surfaceMuted,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  danger: { backgroundColor: colors.red },
  disabled: { opacity: 0.45 },
  buttonText: { color: "white", fontSize: 11, fontWeight: "900" },
  secondaryText: { color: colors.nav },
  card: {
    width: "100%",
    minWidth: 0,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    padding: spacing.lg,
    backgroundColor: colors.surface,
    gap: spacing.sm,
  },
  cardTitle: {
    fontSize: typography.subtitle,
    fontWeight: "900",
    color: colors.text,
  },
  backdrop: {
    flex: 1,
    justifyContent: "flex-end",
    backgroundColor: "rgba(7,18,36,.32)",
  },
  modal: {
    width: "100%",
    maxWidth: 720,
    alignSelf: "center",
    maxHeight: "92%",
    backgroundColor: colors.background,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    padding: spacing.lg,
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
    flexShrink: 0,
  },
  modalContent: { flexShrink: 1, minHeight: 0 },
  modalTitle: {
    flex: 1,
    flexShrink: 1,
    fontSize: typography.title,
    fontWeight: "900",
    color: colors.text,
  },
});
