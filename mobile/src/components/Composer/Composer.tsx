import { useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Camera, Grid2X2, Mic, Paperclip, SendHorizontal } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { IconButton } from "../ui/IconButton";

type ComposerProps = {
  onSend?: (text: string) => void | Promise<void>;
};

export function Composer({ onSend }: ComposerProps) {
  const [text, setText] = useState("");

  const handleSend = async () => {
    const message = text.trim();
    if (!message) {
      return;
    }

    setText("");
    void onSend?.(message);
  };

  return (
    <View style={styles.container}>
      <TextInput
        accessibilityLabel="Assistant message"
        placeholder="Type a message or hold to speak..."
        placeholderTextColor={colors.textSubtle}
        onChangeText={setText}
        onSubmitEditing={handleSend}
        blurOnSubmit={false}
        returnKeyType="send"
        value={text}
        style={styles.input}
      />
      <View style={styles.controls}>
        <View style={styles.leftActions}>
          <IconButton icon={Camera} label="Add photo" style={styles.flatIcon} size={14} />
          <IconButton icon={Grid2X2} label="Open templates" style={styles.flatIcon} size={14} />
          <IconButton icon={Paperclip} label="Attach file" style={styles.flatIcon} size={14} />
        </View>
        <Pressable accessibilityRole="button" style={styles.voiceButton}>
          <View style={styles.recordDot} />
          <Text style={styles.voiceText}>Hold to Speak</Text>
          <Mic color={colors.red} size={12} strokeWidth={2.4} />
        </Pressable>
        <IconButton
          icon={SendHorizontal}
          label="Send message"
          style={styles.sendButton}
          color={colors.nav}
          size={16}
          onPress={handleSend}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    height: 62,
    marginHorizontal: spacing.md,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 14,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    paddingTop: 5,
    paddingBottom: 5,
    ...shadow.card
  },
  input: {
    height: 19,
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "700",
    padding: 0
  },
  controls: {
    height: 26,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: spacing.sm
  },
  leftActions: {
    flexDirection: "row",
    gap: spacing.xs
  },
  flatIcon: {
    width: 20,
    height: 20,
    backgroundColor: colors.surface
  },
  voiceButton: {
    minWidth: 94,
    height: 24,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.xs,
    borderWidth: 1,
    borderColor: "#ffd4d8",
    borderRadius: radius.pill,
    backgroundColor: colors.redSoft
  },
  voiceText: {
    color: colors.red,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  recordDot: {
    width: 5,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: colors.red
  },
  sendButton: {
    width: 25,
    height: 25,
    backgroundColor: "#f2f5fa"
  }
});
