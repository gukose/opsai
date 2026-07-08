import { StyleSheet, Text, View } from "react-native";
import { Sparkles } from "lucide-react-native";

import { colors, radius, typography } from "../../theme/tokens";

type MessageBubbleProps = {
  author: "assistant" | "user";
  text: string;
  timestamp?: string;
};

export function MessageBubble({ author, text, timestamp }: MessageBubbleProps) {
  const isUser = author === "user";

  return (
    <View style={[styles.row, isUser ? styles.userRow : styles.assistantRow]}>
      {!isUser && <AssistantMark />}
      <View style={[styles.bubble, isUser ? styles.userBubble : styles.assistantBubble]}>
        <Text style={styles.text}>{text}</Text>
        {timestamp ? <Text style={styles.timestamp}>{timestamp}</Text> : null}
      </View>
    </View>
  );
}

function AssistantMark() {
  return (
    <View style={styles.mark}>
      <Sparkles color={colors.amber} size={11} strokeWidth={2.25} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    width: "100%",
    flexDirection: "row",
    alignItems: "flex-start",
    marginVertical: 2
  },
  assistantRow: {
    justifyContent: "flex-start"
  },
  userRow: {
    justifyContent: "flex-end"
  },
  mark: {
    width: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    marginRight: 6,
    marginTop: 2,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface
  },
  bubble: {
    maxWidth: "72%",
    minHeight: 27,
    justifyContent: "center",
    borderRadius: 9,
    paddingHorizontal: 8,
    paddingTop: 5,
    paddingBottom: 4
  },
  assistantBubble: {
    backgroundColor: "#f2f3f6"
  },
  userBubble: {
    backgroundColor: colors.greenSoft
  },
  text: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "600",
    lineHeight: 13
  },
  timestamp: {
    alignSelf: "flex-end",
    marginTop: 0,
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "700"
  }
});
