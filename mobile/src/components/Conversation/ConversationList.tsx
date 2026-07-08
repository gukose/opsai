import { ScrollView, StyleSheet, View } from "react-native";

import { ActionQuestion, ConversationItem } from "../../assistant/types";
import { DropdownQuestion } from "./DropdownQuestion";
import { IntentBadge } from "./IntentBadge";
import { MessageBubble } from "./MessageBubble";
import { TaskPreview } from "./TaskPreview";
import { VoiceBubble } from "./VoiceBubble";
import { AttachmentCard } from "./AttachmentCard";

type ConversationListProps = {
  items: ConversationItem[];
  onQuestionActionPress?: (action: ActionQuestion["actions"][number]) => void;
  onTaskPreviewCancel?: () => void;
  onTaskPreviewCreate?: () => void;
};

export function ConversationList({
  items,
  onQuestionActionPress,
  onTaskPreviewCancel,
  onTaskPreviewCreate
}: ConversationListProps) {
  return (
    <ScrollView
      showsVerticalScrollIndicator={false}
      style={styles.scroll}
      contentContainerStyle={styles.content}
    >
      {items.map((item) => (
        <View key={item.id}>
          {item.type === "text" ? (
            <MessageBubble
              author={item.author}
              text={item.text}
              timestamp={item.timestamp}
            />
          ) : null}
          {item.type === "voice" ? (
            <VoiceBubble
              transcript={item.transcript}
              duration={item.duration}
              timestamp={item.timestamp}
            />
          ) : null}
          {item.type === "attachment" ? (
            <AttachmentCard
              imageUri={item.attachment.imageUri}
              filename={item.attachment.filename}
              size={item.attachment.size}
              timestamp={item.timestamp}
            />
          ) : null}
          {item.type === "intent" ? (
            <IntentBadge label={item.label} tone={item.tone} />
          ) : null}
          {item.type === "question" ? (
            <DropdownQuestion actions={item.actions} onActionPress={onQuestionActionPress} />
          ) : null}
          {item.type === "taskPreview" ? (
            <TaskPreview
              task={item.task}
              onCancel={onTaskPreviewCancel}
              onCreateTask={onTaskPreviewCreate}
            />
          ) : null}
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1
  },
  content: {
    paddingTop: 5,
    paddingBottom: 6
  }
});
