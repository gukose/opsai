import { useEffect, useMemo, useState } from "react";
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, useWindowDimensions, View } from "react-native";

import { colors } from "../../theme/tokens";
import { assistantBackendEnabled } from "../../config/assistantConfig";
import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "../../assistant/types";
import { applyRegisteredAttachment, sampleLocalImageAttachment } from "../../assistant/attachmentMetadata";
import { selectImageFromCamera, selectImageFromGallery } from "../../assistant/imageSelection";
import {
  createLocalImageObservationMetadata,
  createLocalVoiceTranscriptMetadata
} from "../../assistant/semanticInputMetadata";
import {
  clearAssistantDraft,
  loadAssistantDraft,
  saveAssistantDraft
} from "../../assistant/assistantDraftStorage";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { hasPermission } from "../../auth/currentUserHelpers";
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
import { KnowledgeAssistantScreen } from "../Knowledge/KnowledgeAssistantScreen";
import { VoiceRecorderPanel } from "../Voice/VoiceRecorderPanel";
import { resolveResponsiveLayout } from "../../layout/responsiveLayout";

type AssistantHomeScreenProps = {
  accessToken: string | null;
  currentUser: CurrentUserSnapshot | null;
  refreshAccessToken?: () => Promise<string | null>;
  onLogout?: () => void;
};

export function AssistantHomeScreen({ accessToken, currentUser, refreshAccessToken, onLogout }: AssistantHomeScreenProps) {
  const { width } = useWindowDimensions();
  const responsiveLayout = resolveResponsiveLayout(width);
  const isDesktop = responsiveLayout.mode === "desktop";
  const isTablet = responsiveLayout.mode === "tablet";
  const [activeSection, setActiveSection] = useState<BottomNavigationKey>("home");
  const [composerText, setComposerText] = useState("");
  const [selectedAttachments, setSelectedAttachments] = useState<LocalAttachmentMetadata[]>([]);
  const [voiceTranscript, setVoiceTranscript] = useState<LocalVoiceTranscriptMetadata | null>(null);
  const [imageObservations, setImageObservations] = useState<LocalImageObservationMetadata[]>([]);
  const [attachmentError, setAttachmentError] = useState<string | null>(null);
  const [voiceRecorderVisible, setVoiceRecorderVisible] = useState(false);
  const [draftHydrated, setDraftHydrated] = useState(false);
  const {
    conversationId,
    conversationItems,
    sendTextMessage,
    registerAttachment,
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
    staleReason: taskStaleReason,
    cachedAt: taskCachedAt,
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
    resumeHomeTask,
    assignmentCandidates,
    assignSelectedTask
  } = useTaskBoardState(accessToken, currentUser, refreshAccessToken);
  const {
    summary: dashboardSummary,
    staleReason: dashboardStaleReason,
    cachedAt: dashboardCachedAt,
    refreshDashboard
  } = useDashboardSummaryState(accessToken, currentUser, refreshAccessToken);
  const overviewForDisplay = dashboardSummary?.overview ?? overview;
  const assistantActionDisabled = isSending || isConfirming;
  const isHomeSurface = activeSection === "home" || activeSection === "assistant";
  const canUseKnowledgeAssistant = hasPermission(currentUser, "KNOWLEDGE_OPERATIONS");
  const offlineScope = useMemo(
    () =>
      currentUser?.hotelId && currentUser.userId
        ? { hotelId: currentUser.hotelId, userId: currentUser.userId }
        : null,
    [currentUser?.hotelId, currentUser?.userId]
  );

  useEffect(() => {
    if (!offlineScope) {
      setDraftHydrated(false);
      return;
    }

    let active = true;
    setDraftHydrated(false);
    void loadAssistantDraft(offlineScope, conversationId).then((draft) => {
      if (!active) {
        return;
      }
      if (draft) {
        setComposerText(draft.text);
        setSelectedAttachments(draft.attachments);
        setVoiceTranscript(draft.voiceTranscript);
        setImageObservations(draft.imageObservations);
      } else {
        setComposerText("");
        setSelectedAttachments([]);
        setVoiceTranscript(null);
        setImageObservations([]);
      }
      setDraftHydrated(true);
    }).catch(() => {
      if (active) {
        setDraftHydrated(true);
      }
    });

    return () => {
      active = false;
    };
  }, [conversationId, offlineScope]);

  useEffect(() => {
    if (!offlineScope || !draftHydrated) {
      return;
    }

    void saveAssistantDraft(offlineScope, {
      conversationId,
      text: composerText,
      attachments: selectedAttachments,
      voiceTranscript,
      imageObservations
    });
  }, [composerText, conversationId, draftHydrated, imageObservations, offlineScope, selectedAttachments, voiceTranscript]);

  const registerSelectedAttachment = async (attachment: LocalAttachmentMetadata) => {
    if (!assistantBackendEnabled) {
      return;
    }

    setSelectedAttachments((current) =>
      current.map((item) =>
        item.id === attachment.id ? { ...item, state: "REGISTERING", errorMessage: undefined } : item
      )
    );

    try {
      const response = await registerAttachment(attachment);
      if (!response) {
        throw new Error("Attachment registration is unavailable.");
      }
      const registered = applyRegisteredAttachment(attachment, response);
      setSelectedAttachments((current) =>
        current.map((item) => (item.id === attachment.id ? registered : item))
      );
      setImageObservations((current) =>
        current.map((observation) =>
          observation.attachmentId === attachment.id
            ? { ...observation, attachmentId: registered.id }
            : observation
        )
      );
    } catch (error) {
      setSelectedAttachments((current) =>
        current.map((item) =>
          item.id === attachment.id
            ? {
                ...item,
                state: "REGISTRATION_FAILED",
                errorMessage: error instanceof Error ? error.message : "Registration failed."
              }
            : item
        )
      );
    }
  };

  const addImageAttachment = async (source: "camera" | "gallery") => {
    try {
      const selected = source === "camera"
        ? await selectImageFromCamera(selectedAttachments)
        : await selectImageFromGallery(selectedAttachments);
      if (!selected) {
        return;
      }
      setSelectedAttachments((current) => [...current, selected]);
      setAttachmentError(null);
      void registerSelectedAttachment(selected);
    } catch (error) {
      setAttachmentError(error instanceof Error ? error.message : "Attachment could not be selected.");
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.screen}>
        <AssistantHeader
          currentUser={currentUser}
          unreadNotificationCount={dashboardSummary?.overview.unreadNotificationCount ?? 0}
          recentNotifications={dashboardSummary?.recentNotifications ?? []}
          notificationsStaleReason={dashboardStaleReason}
          onReset={() => {
            void resetConversation();
          }}
          onLogout={onLogout}
        />
        {isHomeSurface ? (
          <ScrollView
            style={styles.homeScroll}
            contentContainerStyle={[
              styles.homeContent,
              isTablet ? styles.homeContentTablet : null,
              isDesktop ? styles.homeContentDesktop : null
            ]}
            showsVerticalScrollIndicator={false}
          >
            <OverviewStrip {...overviewForDisplay} />
            {dashboardStaleReason ? (
              <TaskErrorBanner
                title="Offline data"
                message={`${dashboardStaleReason}${dashboardCachedAt ? ` Last updated ${formatCacheTime(dashboardCachedAt)}.` : ""}`}
              />
            ) : null}
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
                  setActiveSection("tasks");
                  await selectTask(createdTaskId);
                }
              }}
              isActionDisabled={assistantActionDisabled}
            />
          </ScrollView>
        ) : activeSection === "tasks" ? (
          <TasksScreen
            tasks={tasks}
            selectedTask={selectedTask}
            selectedTaskId={selectedTaskId}
            currentUser={currentUser}
            isLoading={isLoading}
            isRefreshing={isRefreshing}
            errorMessage={errorMessage}
            staleReason={taskStaleReason}
            cachedAt={taskCachedAt}
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
            assignmentCandidates={assignmentCandidates}
            onAssignTask={assignSelectedTask}
          />
        ) : activeSection === "knowledge" && canUseKnowledgeAssistant ? (
          <KnowledgeAssistantScreen accessToken={accessToken} currentUser={currentUser} />
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
        <View style={[styles.footer, isDesktop ? styles.footerDesktop : null]}>
          {isHomeSurface ? (
            <View style={isTablet && !isDesktop ? styles.composerTablet : null}>
              <Composer
              onSend={async (text, attachments, transcript, observations = []) => {
                if (assistantBackendEnabled && attachments.some((attachment) => attachment.storageStatus !== "REGISTERED")) {
                  setAttachmentError("Register attachment metadata before sending.");
                  return false;
                }
                const previousAttachments = attachments;
                setSelectedAttachments((current) => current.map((attachment) => ({ ...attachment, state: "MESSAGE_SENDING" })));
                setVoiceTranscript((current) => current ? { ...current, state: "sending" } : null);
                setImageObservations((current) => current.map((observation) => ({ ...observation, state: "sending" })));
                const sent = await sendTextMessage(text, attachments, transcript, observations);
                if (sent) {
                  setComposerText("");
                  setSelectedAttachments([]);
                  setVoiceTranscript(null);
                  setImageObservations([]);
                  setAttachmentError(null);
                  if (offlineScope) {
                    void clearAssistantDraft(offlineScope, conversationId);
                  }
                } else {
                  setSelectedAttachments(previousAttachments);
                  setVoiceTranscript((current) => current ? { ...current, state: "failed" } : null);
                  setImageObservations((current) => current.map((observation) => ({ ...observation, state: "failed" })));
                }
                return sent;
              }}
              text={composerText}
              onTextChange={setComposerText}
              attachments={selectedAttachments}
              voiceTranscript={voiceTranscript}
              imageObservations={imageObservations}
              draftMessage={composerText || selectedAttachments.length > 0 || voiceTranscript || imageObservations.length > 0 ? "Draft saved on this device." : null}
              attachmentError={attachmentError}
              onAddAttachment={() => {
                try {
                  if (assistantBackendEnabled) {
                    void addImageAttachment("gallery");
                    return;
                  }
                  setSelectedAttachments((current) => [...current, sampleLocalImageAttachment(current)]);
                  setAttachmentError(null);
                } catch (error) {
                  setAttachmentError(error instanceof Error ? error.message : "Attachment could not be selected.");
                }
              }}
              onAddCameraImage={() => {
                void addImageAttachment("camera");
              }}
              onRemoveAttachment={(attachmentId) => {
                setSelectedAttachments((current) => current.filter((attachment) => attachment.id !== attachmentId));
                setImageObservations((current) => current.filter((observation) => observation.attachmentId !== attachmentId));
                setAttachmentError(null);
              }}
              onRetryAttachmentRegistration={(attachmentId) => {
                const attachment = selectedAttachments.find((item) => item.id === attachmentId);
                if (attachment) {
                  void registerSelectedAttachment(attachment);
                }
              }}
              onAddVoiceTranscript={() => {
                if (!accessToken) { setAttachmentError("Sign in before recording a voice request."); return; }
                setVoiceRecorderVisible((visible) => !visible);
                setAttachmentError(null);
              }}
              onRemoveVoiceTranscript={() => {
                setVoiceTranscript(null);
                setAttachmentError(null);
              }}
              onAddImageObservation={() => {
                try {
                  const imageAttachment = selectedAttachments.find((attachment) => attachment.type === "IMAGE");
                  if (!imageAttachment) {
                    throw new Error("Select an image reference before adding an image note.");
                  }
                  setImageObservations((current) => [
                    ...current,
                    createLocalImageObservationMetadata(
                      imageAttachment,
                      "User-provided note: visible issue in the image reference",
                      current
                    )
                  ]);
                  setAttachmentError(null);
                } catch (error) {
                  setAttachmentError(error instanceof Error ? error.message : "Image note could not be added.");
                }
              }}
              onRemoveImageObservation={(observationId) => {
                setImageObservations((current) => current.filter((observation) => observation.id !== observationId));
                setAttachmentError(null);
              }}
              voiceRecorderActive={voiceRecorderVisible}
              voiceRecorder={voiceRecorderVisible && accessToken ? (
                <VoiceRecorderPanel
                  accessToken={accessToken}
                  onClose={() => setVoiceRecorderVisible(false)}
                  onUseTranscript={(proposal) => {
                    setVoiceTranscript(createLocalVoiceTranscriptMetadata({ transcript: proposal.transcript, languageCode: proposal.languageCode, durationMs: proposal.durationMs, source: "SERVER_STT" }));
                    setComposerText(proposal.transcript);
                    setAttachmentError(proposal.confirmationRequired ? "Low-confidence voice intent: review the transcript before sending." : null);
                  }}
                />
              ) : null}
              disabled={assistantActionDisabled}
              />
            </View>
          ) : null}
          <BottomNavigation
            activeKey={activeSection}
            currentUser={currentUser}
            onSelect={(key) => {
              if (key === "home" || key === "tasks" || key === "assistant" || key === "knowledge" || key === "operations" || key === "profile") {
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

function formatCacheTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "recently";
  }

  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background
  },
  screen: {
    flex: 1,
    width: "100%",
    maxWidth: 1360,
    alignSelf: "center",
    backgroundColor: colors.background
  },
  homeScroll: {
    flex: 1,
    width: "100%"
  },
  homeContent: {
    width: "100%",
    paddingBottom: 8
  },
  homeContentTablet: {
    paddingHorizontal: 12
  },
  homeContentDesktop: {
    paddingHorizontal: 24,
    paddingBottom: 18
  },
  footer: {
    paddingTop: 2,
    backgroundColor: colors.background
  },
  footerDesktop: {
    paddingHorizontal: 24,
    paddingBottom: 8
  },
  composerTablet: {
    paddingHorizontal: 12
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
