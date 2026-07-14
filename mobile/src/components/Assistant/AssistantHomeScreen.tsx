import { useState } from "react";
import { SafeAreaView, StatusBar, StyleSheet, Text, View } from "react-native";

import { colors } from "../../theme/tokens";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { Composer } from "../Composer/Composer";
import { BottomNavigation } from "../Navigation/BottomNavigation";
import { AssistantCard } from "./AssistantCard";
import { AssistantHeader } from "./AssistantHeader";
import { NextTaskCard } from "./NextTaskCard";
import { OverviewStrip } from "./OverviewStrip";
import { useAssistantHomeState } from "../../assistant/useAssistantHomeState";
import { useDashboardSummaryState } from "../../dashboard/useDashboardSummaryState";
import { useTaskBoardState } from "../../tasks/useTaskBoardState";
import { TaskEmptyState } from "../Tasks/TaskEmptyState";
import { TasksScreen } from "../Tasks/TasksScreen";
import { ProfilePanel } from "../Profile/ProfilePanel";
import { BottomNavigationKey } from "../Navigation/BottomNavigation";

type AssistantHomeScreenProps = {
  accessToken: string | null;
  currentUser: CurrentUserSnapshot | null;
  onLogout?: () => void;
};

export function AssistantHomeScreen({ accessToken, currentUser, onLogout }: AssistantHomeScreenProps) {
  const [activeSection, setActiveSection] = useState<BottomNavigationKey>("home");
  const {
    conversationItems,
    sendTextMessage,
    confirmTask,
    resetConversation,
    isSending,
    isConfirming,
    errorMessage: assistantErrorMessage
  } = useAssistantHomeState({ accessToken, currentUser });
  const {
    tasks,
    selectedTask,
    selectedTaskId,
    isLoading,
    isRefreshing,
    errorMessage,
    filters,
    homeTask,
    overview,
    updateFilters,
    clearFilters,
    selectTask,
    refreshTasks,
    startSelectedTask,
    pauseSelectedTask,
    resumeSelectedTask,
    completeSelectedTask,
    cancelSelectedTask,
    startHomeTask,
    resumeHomeTask
  } = useTaskBoardState(accessToken);
  const { summary: dashboardSummary, refreshDashboard } = useDashboardSummaryState(accessToken);
  const overviewForDisplay = dashboardSummary?.overview ?? overview;
  const assistantActionDisabled = isSending || isConfirming;
  const isHomeSurface = activeSection === "home" || activeSection === "assistant";

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.screen}>
        <AssistantHeader
          currentUser={currentUser}
          onReset={() => {
            void resetConversation();
          }}
          onLogout={onLogout}
        />
        {isHomeSurface ? (
          <>
            <OverviewStrip {...overviewForDisplay} />
            {assistantErrorMessage ? <TaskErrorBanner title="Assistant sync issue" message={assistantErrorMessage} /> : null}
            {errorMessage ? <TaskErrorBanner title="Task sync issue" message={errorMessage} /> : null}
            {homeTask ? (
              <NextTaskCard
                task={homeTask}
                isActionInProgress={isRefreshing}
                onStartTask={() => {
                  void startHomeTask();
                }}
                onResumeTask={() => {
                  void resumeHomeTask();
                }}
                onContinueTask={async () => {
                  setActiveSection("tasks");
                  await selectTask(homeTask.id);
                }}
              />
            ) : (
              <TaskEmptyState
                title="No next task"
                message="When the backend returns work, the next assigned task will appear here."
              />
            )}
            <AssistantCard
              items={conversationItems}
              onQuestionActionPress={(action) => {
                void sendTextMessage(action.value ?? action.label);
              }}
              onTaskPreviewCancel={() => {
                void resetConversation();
              }}
              onTaskPreviewCreate={async () => {
                const createdTaskId = await confirmTask();
                if (createdTaskId) {
                  await refreshTasks();
                  await refreshDashboard();
                }
              }}
              isActionDisabled={assistantActionDisabled}
            />
          </>
        ) : activeSection === "tasks" ? (
          <TasksScreen
            tasks={tasks}
            selectedTask={selectedTask}
            selectedTaskId={selectedTaskId}
            currentUser={currentUser}
            isLoading={isLoading}
            isRefreshing={isRefreshing}
            errorMessage={errorMessage}
            filters={filters}
            onRefresh={refreshTasks}
            onFiltersChange={updateFilters}
            onClearFilters={clearFilters}
            onSelectTask={selectTask}
            onStartTask={startSelectedTask}
            onPauseTask={pauseSelectedTask}
            onResumeTask={resumeSelectedTask}
            onCompleteTask={completeSelectedTask}
            onCancelTask={cancelSelectedTask}
          />
        ) : activeSection === "profile" ? (
          <ProfilePanel
            currentUser={currentUser}
            onLogout={() => {
              onLogout?.();
            }}
          />
        ) : (
          <TaskEmptyState
            title="Operations"
            message={`Operational tools for ${currentUser?.hotelName ?? "this hotel"} will appear here.`}
          />
        )}
        <View style={styles.footer}>
          {isHomeSurface ? <Composer onSend={sendTextMessage} disabled={assistantActionDisabled} /> : null}
          <BottomNavigation
            activeKey={activeSection}
            currentUser={currentUser}
            onSelect={(key) => {
              if (key === "home" || key === "tasks" || key === "assistant" || key === "operations" || key === "profile") {
                setActiveSection(key);
              }
            }}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

function TaskErrorBanner({ title, message }: { title: string; message: string }) {
  return (
    <View style={styles.errorBanner}>
      <View style={styles.errorDot} />
      <View style={styles.errorBody}>
        <Text style={styles.errorTitle}>{title}</Text>
        <Text style={styles.errorMessage}>{message}</Text>
      </View>
    </View>
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
  },
  errorBanner: {
    marginHorizontal: 13,
    marginTop: 6,
    marginBottom: 2,
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 14,
    backgroundColor: "#fff4f4",
    borderWidth: 1,
    borderColor: "#f8c7c7"
  },
  errorDot: {
    width: 8,
    height: 8,
    marginTop: 4,
    borderRadius: 99,
    backgroundColor: colors.red
  },
  errorBody: {
    flex: 1,
    minWidth: 0
  },
  errorTitle: {
    color: colors.red,
    fontSize: 11,
    fontWeight: "900"
  },
  errorMessage: {
    marginTop: 1,
    color: colors.textMuted,
    fontSize: 10,
    fontWeight: "700"
  }
});
