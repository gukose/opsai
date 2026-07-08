import { SafeAreaView, StatusBar, StyleSheet, View } from "react-native";

import { colors } from "../../theme/tokens";
import { Composer } from "../Composer/Composer";
import { BottomNavigation } from "../Navigation/BottomNavigation";
import { AssistantCard } from "./AssistantCard";
import { AssistantHeader } from "./AssistantHeader";
import { NextTaskCard } from "./NextTaskCard";
import { OverviewStrip } from "./OverviewStrip";
import { useAssistantHomeState } from "../../assistant/useAssistantHomeState";

export function AssistantHomeScreen() {
  const {
    conversationItems,
    nextAssignedTask,
    sendTextMessage,
    confirmTask,
    resetConversation
  } = useAssistantHomeState();

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.screen}>
        <AssistantHeader
          onReset={() => {
            void resetConversation();
          }}
        />
        <OverviewStrip />
        {nextAssignedTask ? <NextTaskCard task={nextAssignedTask} /> : null}
        <AssistantCard
          items={conversationItems}
          onQuestionActionPress={(action) => {
            void sendTextMessage(action.value ?? action.label);
          }}
          onTaskPreviewCancel={() => {
            void resetConversation();
          }}
          onTaskPreviewCreate={() => {
            void confirmTask();
          }}
        />
        <View style={styles.footer}>
          <Composer onSend={sendTextMessage} />
          <BottomNavigation />
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background
  },
  screen: {
    flex: 1,
    width: "100%",
    maxWidth: 390,
    alignSelf: "center",
    backgroundColor: colors.background
  },
  footer: {
    paddingTop: 5,
    backgroundColor: colors.background
  }
});
