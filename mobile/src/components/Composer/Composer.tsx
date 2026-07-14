import { useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Camera, FileText, Grid2X2, MessageSquareText, Paperclip, SendHorizontal, X } from "lucide-react-native";

import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "../../assistant/types";
import { formatAttachmentSize } from "../../assistant/attachmentMetadata";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { IconButton } from "../ui/IconButton";

type ComposerProps = {
  onSend?: (
    text: string,
    attachments: LocalAttachmentMetadata[],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations?: LocalImageObservationMetadata[]
  ) => boolean | void | Promise<boolean | void>;
  attachments?: LocalAttachmentMetadata[];
  voiceTranscript?: LocalVoiceTranscriptMetadata | null;
  imageObservations?: LocalImageObservationMetadata[];
  text?: string;
  draftMessage?: string | null;
  attachmentError?: string | null;
  onTextChange?: (text: string) => void;
  onAddAttachment?: () => void;
  onRemoveAttachment?: (attachmentId: string) => void;
  onAddVoiceTranscript?: () => void;
  onRemoveVoiceTranscript?: () => void;
  onAddImageObservation?: () => void;
  onRemoveImageObservation?: (observationId: string) => void;
  disabled?: boolean;
};

export function Composer({
  onSend,
  attachments = [],
  voiceTranscript,
  imageObservations = [],
  text: controlledText,
  draftMessage,
  attachmentError,
  onTextChange,
  onAddAttachment,
  onRemoveAttachment,
  onAddVoiceTranscript,
  onRemoveVoiceTranscript,
  onAddImageObservation,
  onRemoveImageObservation,
  disabled
}: ComposerProps) {
  const [localText, setLocalText] = useState("");
  const text = controlledText ?? localText;
  const setText = (nextText: string) => {
    if (controlledText === undefined) {
      setLocalText(nextText);
    }
    onTextChange?.(nextText);
  };

  const handleSend = async () => {
    const message = text.trim();
    if ((!message && attachments.length === 0 && !voiceTranscript && imageObservations.length === 0) || disabled) {
      return;
    }

    const sent = await onSend?.(message, attachments, voiceTranscript, imageObservations);
    if (sent !== false) {
      setText("");
    }
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
        editable={!disabled}
        style={styles.input}
      />
      {attachments.length > 0 || voiceTranscript || imageObservations.length > 0 || attachmentError ? (
        <View style={styles.attachmentTray}>
          {voiceTranscript ? (
            <View style={styles.attachmentPill}>
              <MessageSquareText color={colors.nav} size={11} strokeWidth={2.3} />
              <View style={styles.attachmentMeta}>
                <Text style={styles.attachmentName} numberOfLines={1}>
                  Client transcript
                </Text>
                <Text style={styles.attachmentState} numberOfLines={1}>
                  Client-provided · not server transcribed · {voiceTranscript.state}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={disabled}
                onPress={onRemoveVoiceTranscript}
                style={styles.removeAttachment}
              >
                <X color={colors.textMuted} size={10} strokeWidth={2.6} />
              </Pressable>
            </View>
          ) : null}
          {attachments.map((attachment) => (
            <View key={attachment.id} style={styles.attachmentPill}>
              {attachment.type === "IMAGE" ? (
                <Camera color={colors.nav} size={11} strokeWidth={2.3} />
              ) : (
                <FileText color={colors.nav} size={11} strokeWidth={2.3} />
              )}
              <View style={styles.attachmentMeta}>
                <Text style={styles.attachmentName} numberOfLines={1}>
                  {attachment.originalFileName}
                </Text>
                <Text style={styles.attachmentState} numberOfLines={1}>
                  Local reference · {formatAttachmentSize(attachment.sizeBytes)} · {attachment.state}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={disabled}
                onPress={() => onRemoveAttachment?.(attachment.id)}
                style={styles.removeAttachment}
              >
                <X color={colors.textMuted} size={10} strokeWidth={2.6} />
              </Pressable>
            </View>
          ))}
          {imageObservations.map((observation) => (
            <View key={observation.id} style={styles.attachmentPill}>
              <MessageSquareText color={colors.nav} size={11} strokeWidth={2.3} />
              <View style={styles.attachmentMeta}>
                <Text style={styles.attachmentName} numberOfLines={1}>
                  Image note
                </Text>
                <Text style={styles.attachmentState} numberOfLines={1}>
                  User-provided · not uploaded for server vision · {observation.state}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={disabled}
                onPress={() => onRemoveImageObservation?.(observation.id)}
                style={styles.removeAttachment}
              >
                <X color={colors.textMuted} size={10} strokeWidth={2.6} />
              </Pressable>
            </View>
          ))}
          {draftMessage ? <Text style={styles.attachmentState}>{draftMessage}</Text> : null}
          {attachmentError ? <Text style={styles.attachmentError}>{attachmentError}</Text> : null}
        </View>
      ) : null}
      <View style={styles.controls}>
        <View style={styles.leftActions}>
          <IconButton icon={Camera} label="Add photo reference" style={styles.flatIcon} size={14} onPress={onAddAttachment} disabled={disabled} />
          <IconButton icon={Grid2X2} label="Open templates" style={styles.flatIcon} size={14} />
          <IconButton icon={Paperclip} label="Attach local reference" style={styles.flatIcon} size={14} onPress={onAddAttachment} disabled={disabled} />
        </View>
        <Pressable accessibilityRole="button" style={styles.voiceButton} onPress={onAddVoiceTranscript} disabled={disabled}>
          <MessageSquareText color={colors.red} size={12} strokeWidth={2.4} />
          <Text style={styles.voiceText}>Client transcript</Text>
        </Pressable>
        <IconButton
          icon={MessageSquareText}
          label="Add image note"
          style={styles.flatIcon}
          size={14}
          onPress={onAddImageObservation}
          disabled={disabled || attachments.every((attachment) => attachment.type !== "IMAGE")}
        />
        <IconButton
          icon={SendHorizontal}
          label="Send message"
          style={styles.sendButton}
          disabled={disabled}
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
    minHeight: 62,
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
  attachmentTray: {
    gap: 5,
    marginBottom: 5
  },
  attachmentPill: {
    minHeight: 30,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    backgroundColor: "#f7f8fa",
    paddingHorizontal: 7,
    paddingVertical: 4
  },
  attachmentMeta: {
    flex: 1,
    minWidth: 0
  },
  attachmentName: {
    color: colors.text,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  attachmentState: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  removeAttachment: {
    width: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: colors.surface
  },
  attachmentError: {
    color: colors.red,
    fontSize: typography.tiny,
    fontWeight: "800"
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
    minWidth: 126,
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
  sendButton: {
    width: 25,
    height: 25,
    backgroundColor: "#f2f5fa"
  }
});
