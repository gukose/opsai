import { ReactNode, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Camera, FileText, Grid2X2, MessageSquareText, Mic, Paperclip, SendHorizontal, X } from "lucide-react-native";

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
  onAddCameraImage?: () => void;
  onRemoveAttachment?: (attachmentId: string) => void;
  onRetryAttachmentRegistration?: (attachmentId: string) => void;
  onAddVoiceTranscript?: () => void;
  onRemoveVoiceTranscript?: () => void;
  onAddImageObservation?: () => void;
  onRemoveImageObservation?: (observationId: string) => void;
  voiceRecorder?: ReactNode;
  voiceRecorderActive?: boolean;
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
  onAddCameraImage,
  onRemoveAttachment,
  onRetryAttachmentRegistration,
  onAddVoiceTranscript,
  onRemoveVoiceTranscript,
  onAddImageObservation,
  onRemoveImageObservation,
  voiceRecorder,
  voiceRecorderActive = false,
  disabled
}: ComposerProps) {
  const [localText, setLocalText] = useState("");
  const [isFocused, setIsFocused] = useState(false);
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
    <View style={[styles.container, isFocused ? styles.containerFocused : null]}>
      <View style={styles.composerRow}>
        <View style={styles.opaiControl}>
          <Text style={styles.opaiLabel}>OpAI</Text>
        </View>
        <TextInput
          accessibilityLabel="Assistant message"
          placeholder="Ask or give a command..."
          placeholderTextColor={colors.textSubtle}
          onBlur={() => setIsFocused(false)}
          onChangeText={setText}
          onFocus={() => setIsFocused(true)}
          onSubmitEditing={handleSend}
          blurOnSubmit={false}
          returnKeyType="send"
          value={text}
          editable={!disabled}
          style={styles.input}
        />
        {!voiceRecorderActive ? (
          <Pressable
            accessibilityLabel="Record voice"
            accessibilityRole="button"
            disabled={disabled}
            onPress={onAddVoiceTranscript}
            style={({ pressed }) => [
              styles.composerAction,
              pressed && !disabled ? styles.actionPressed : null,
              disabled ? styles.actionDisabled : null
            ]}
          >
            <Mic color={colors.red} size={17} strokeWidth={2.4} />
          </Pressable>
        ) : null}
        <IconButton
          icon={SendHorizontal}
          label="Send message"
          style={styles.sendButton}
          disabled={disabled}
          color={colors.nav}
          size={17}
          onPress={handleSend}
        />
      </View>
      {voiceRecorder}
      {attachments.length > 0 || voiceTranscript || imageObservations.length > 0 || attachmentError ? (
        <View style={styles.attachmentTray}>
          {voiceTranscript ? (
            <View style={styles.attachmentPill}>
              <MessageSquareText color={colors.nav} size={11} strokeWidth={2.3} />
              <View style={styles.attachmentMeta}>
                <Text style={styles.attachmentName} numberOfLines={1}>
                  {voiceTranscript.source === "SERVER_STT" ? "Voice transcript" : "Client transcript"}
                </Text>
                <Text style={styles.attachmentState} numberOfLines={1}>
                  {voiceTranscript.source === "SERVER_STT" ? "Server transcribed" : "Client-provided · not server transcribed"} · {voiceTranscript.state}
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
                  {attachmentStatusLabel(attachment)} · {formatAttachmentSize(attachment.sizeBytes)}
                </Text>
                {attachment.errorMessage ? (
                  <Text style={styles.attachmentError} numberOfLines={1}>
                    {attachment.errorMessage}
                  </Text>
                ) : null}
              </View>
              {attachment.state === "REGISTRATION_FAILED" ? (
                <Pressable
                  accessibilityRole="button"
                  disabled={disabled}
                  onPress={() => onRetryAttachmentRegistration?.(attachment.id)}
                  style={styles.retryAttachment}
                >
                  <Text style={styles.retryAttachmentText}>Retry</Text>
                </Pressable>
              ) : null}
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
                  User-provided · not server vision · {observation.state}
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
          <IconButton icon={Camera} label="Add photo reference" style={styles.flatIcon} size={14} onPress={onAddCameraImage ?? onAddAttachment} disabled={disabled} />
          <IconButton icon={Grid2X2} label="Open templates" style={styles.flatIcon} size={14} />
          <IconButton icon={Paperclip} label="Attach local reference" style={styles.flatIcon} size={14} onPress={onAddAttachment} disabled={disabled} />
        </View>
        <IconButton
          icon={MessageSquareText}
          label="Add image note"
          style={styles.flatIcon}
          size={14}
          onPress={onAddImageObservation}
          disabled={disabled || attachments.every((attachment) => attachment.type !== "IMAGE")}
        />
      </View>
    </View>
  );
}

function attachmentStatusLabel(attachment: LocalAttachmentMetadata): string {
  switch (attachment.state) {
    case "REGISTERING":
      return "Registering metadata";
    case "REGISTERED":
      return "Registered metadata";
    case "REGISTRATION_FAILED":
      return "Registration failed · Retry";
    case "MESSAGE_SENDING":
      return "Sending message";
    case "MESSAGE_SENT":
      return "Message sent";
    case "LOCAL_SELECTED":
    default:
      return "Local preview only";
  }
}

const styles = StyleSheet.create({
  container: {
    minHeight: 62,
    marginHorizontal: 5,
    borderWidth: 1.5,
    borderColor: "#cbd5e1",
    borderRadius: 15,
    backgroundColor: colors.surface,
    paddingTop: 5,
    paddingBottom: 5,
    ...shadow.card
  },
  containerFocused: {
    borderColor: colors.blue,
    shadowOpacity: 0.08,
    elevation: 3
  },
  composerRow: {
    minHeight: 42,
    flexDirection: "row",
    alignItems: "center"
  },
  opaiControl: {
    minWidth: 58,
    height: 40,
    alignItems: "flex-start",
    justifyContent: "center",
    borderRightWidth: 1,
    borderRightColor: colors.divider,
    paddingHorizontal: spacing.md
  },
  opaiLabel: {
    color: colors.nav,
    fontSize: typography.body,
    fontWeight: "900"
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
  retryAttachment: {
    minWidth: 44,
    height: 20,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: colors.nav,
    paddingHorizontal: 8
  },
  retryAttachmentText: {
    color: colors.surface,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  attachmentError: {
    color: colors.red,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  input: {
    flex: 1,
    minWidth: 0,
    minHeight: 40,
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "700",
    paddingHorizontal: spacing.md,
    paddingVertical: 0
  },
  controls: {
    minHeight: 34,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: spacing.sm,
    paddingHorizontal: spacing.md
  },
  leftActions: {
    flexDirection: "row",
    gap: spacing.xs
  },
  flatIcon: {
    width: 28,
    height: 28,
    borderRadius: 0,
    backgroundColor: "transparent"
  },
  composerAction: {
    width: 34,
    height: 40,
    alignItems: "center",
    justifyContent: "center",
    borderLeftWidth: 1,
    borderLeftColor: colors.divider
  },
  actionPressed: {
    opacity: 0.65
  },
  actionDisabled: {
    opacity: 0.45
  },
  sendButton: {
    width: 34,
    height: 40,
    borderRadius: 0,
    backgroundColor: "transparent"
  }
});
