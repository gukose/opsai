import { StyleSheet, Text, View } from "react-native";
import { Info, Sparkles } from "lucide-react-native";

import { ActionQuestion, ConversationItem } from "../../assistant/types";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { ConversationList } from "../Conversation/ConversationList";

type AssistantCardProps = {
  items: ConversationItem[];
  onQuestionActionPress?: (action: ActionQuestion["actions"][number]) => void;
  onTaskPreviewCancel?: () => void;
  onTaskPreviewCreate?: () => void;
  isActionDisabled?: boolean;
};

export function AssistantCard({
  items,
  onQuestionActionPress,
  onTaskPreviewCancel,
  onTaskPreviewCreate,
  isActionDisabled
}: AssistantCardProps) {
  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View>
          <View style={styles.titleRow}>
            <Sparkles color={colors.amber} size={13} strokeWidth={2.2} />
            <Text style={styles.title}>OpAI Assistant</Text>
          </View>
          <View style={styles.statusRow}>
            <View style={styles.statusDot} />
            <Text style={styles.status}>Online</Text>
          </View>
        </View>
        <Info color={colors.nav} size={15} strokeWidth={2.1} />
      </View>
      <ConversationList
        items={items}
        onQuestionActionPress={onQuestionActionPress}
        onTaskPreviewCancel={onTaskPreviewCancel}
        onTaskPreviewCreate={onTaskPreviewCreate}
        isActionDisabled={isActionDisabled}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    marginHorizontal: 5,
    marginTop: 5,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 15,
    backgroundColor: colors.surface,
    paddingHorizontal: 9,
    paddingTop: 8,
    paddingBottom: 5,
    ...shadow.card
  },
  header: {
    minHeight: 30,
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between"
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6
  },
  title: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "800"
  },
  statusRow: {
    marginLeft: 21,
    marginTop: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs
  },
  statusDot: {
    width: 5,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: colors.green
  },
  status: {
    color: colors.nav,
    fontSize: typography.caption,
    fontWeight: "800"
  }
});
