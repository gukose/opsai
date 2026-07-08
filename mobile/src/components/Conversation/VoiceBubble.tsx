import { StyleSheet, Text, View } from "react-native";
import { Check, Play } from "lucide-react-native";

import { colors, radius, typography } from "../../theme/tokens";

type VoiceBubbleProps = {
  transcript: string;
  duration: string;
  timestamp?: string;
};

export function VoiceBubble({ transcript, duration, timestamp }: VoiceBubbleProps) {
  return (
    <View style={styles.row}>
      <View style={styles.bubble}>
        <View style={styles.topLine}>
          <View style={styles.play}>
            <Play color={colors.green} size={9} strokeWidth={2.8} fill="none" />
          </View>
          <Text style={styles.wave}>▁▂▃▅▃▂▃▆▇▅▃▂▃▅▃▁</Text>
          <Text style={styles.duration}>{duration}</Text>
        </View>
        <View style={styles.bottomLine}>
          <Text style={styles.title}>{transcript}</Text>
          {timestamp ? (
            <View style={styles.timestampWrap}>
              <Text style={styles.timestamp}>{timestamp}</Text>
              <Check color={colors.textSubtle} size={8} strokeWidth={2.5} />
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    width: "100%",
    alignItems: "flex-end",
    marginVertical: 2
  },
  bubble: {
    width: "68%",
    maxWidth: "68%",
    minWidth: 0,
    borderWidth: 1,
    borderColor: colors.greenBorder,
    borderRadius: 9,
    backgroundColor: colors.greenSoft,
    paddingHorizontal: 7,
    paddingTop: 6,
    paddingBottom: 4
  },
  topLine: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5
  },
  play: {
    width: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: colors.green
  },
  wave: {
    flex: 1,
    minWidth: 0,
    color: colors.green,
    fontSize: 11,
    fontWeight: "800"
  },
  duration: {
    color: colors.green,
    fontSize: typography.body,
    fontWeight: "900"
  },
  bottomLine: {
    marginTop: 2,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between"
  },
  title: {
    flex: 1,
    minWidth: 0,
    color: colors.text,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  timestamp: {
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  timestampWrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 1,
    flexShrink: 0
  }
});
