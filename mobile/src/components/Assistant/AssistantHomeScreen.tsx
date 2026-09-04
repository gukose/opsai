import { useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, useWindowDimensions, View } from "react-native";

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
import { AdministrationScreen } from "../Admin/AdministrationScreen";
import { TaskDetailCard } from "../Tasks/TaskDetailCard";
import { ConversationList } from "../Conversation/ConversationList";
import { resolveExperienceMode, UserExperienceMode } from "../../auth/experienceMode";
import { FrontlineCompletionScreen, RoleAdaptiveHome } from "./RoleAdaptiveHome";
import { TaskDetail } from "../../tasks/types";

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
  const experienceMode: UserExperienceMode = resolveExperienceMode(currentUser);
  const frontlineSimple = experienceMode === "FRONTLINE_SIMPLE";
  const [activeSection, setActiveSection] = useState<BottomNavigationKey>("home");
  const [frontlineDetailOrigin, setFrontlineDetailOrigin] = useState<"home" | "list">("home");
  const [frontlineCompletionTask, setFrontlineCompletionTask] = useState<TaskDetail | null>(null);
  const [composerText, setComposerText] = useState("");
  const [selectedAttachments, setSelectedAttachments] = useState<LocalAttachmentMetadata[]>([]);
  const [voiceTranscript, setVoiceTranscript] = useState<LocalVoiceTranscriptMetadata | null>(null);
  const [imageObservations, setImageObservations] = useState<LocalImageObservationMetadata[]>([]);
  const [attachmentError, setAttachmentError] = useState<string | null>(null);
  const [voiceRecorderVisible, setVoiceRecorderVisible] = useState(false);
  const [visionAnalyzing, setVisionAnalyzing] = useState(false);
  const [taskCreateFeedback, setTaskCreateFeedback] = useState<string | null>(null);
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
    clearSelectedTask,
    refreshTasks,
    startSelectedTask,
    pauseSelectedTask,
    resumeSelectedTask,
    completeSelectedTask,
    cancelSelectedTask,
    startHomeTask,
    resumeHomeTask,
    assignmentCandidates,
    refreshAssignmentCandidates,
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
  const showAssistantComposer = false;
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

  const registerSelectedAttachment = async (attachment: LocalAttachmentMetadata): Promise<LocalAttachmentMetadata | null> => {
    if (!assistantBackendEnabled) {
      return attachment;
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
      return registered;
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
      return null;
    }
  };

  const acceptVoiceProposal = async (proposal: import("../../voice/voiceModels").VoiceTaskProposal) => {
    const transcript = createLocalVoiceTranscriptMetadata({
      transcript: proposal.transcript,
      languageCode: proposal.languageCode,
      durationMs: proposal.durationMs,
      source: "SERVER_STT"
    });
    setVoiceTranscript(transcript);
    setComposerText(proposal.transcript);
    setAttachmentError(proposal.confirmationRequired ? "Low-confidence voice intent: review the transcript before creating the task." : null);
    // The assistant response creates the shared preview only; task creation
    // remains behind the preview's explicit Create Task action.
    await sendTextMessage(proposal.transcript, selectedAttachments, transcript, imageObservations);
  };

  const handlePreviewCreate = async () => {
    if (assistantActionDisabled) return;
    if (typeof __DEV__ !== "undefined" && __DEV__) console.debug("TASK_PREVIEW_CREATE_TAP");
    setTaskCreateFeedback(null);
    const createdTaskId = await confirmTask();
    if (!createdTaskId) {
      setTaskCreateFeedback("Task could not be created. Check the details and try again.");
      return;
    }
    await refreshTasks();
    await resetConversation();
    setTaskCreateFeedback("Task created");
  };

  const addImageAttachment = async (source: "camera" | "gallery", roomContext?: string | null) => {
    try {
      if (source === "camera") setVisionAnalyzing(true);
      const selected = source === "camera"
        ? await selectImageFromCamera(selectedAttachments)
        : await selectImageFromGallery(selectedAttachments);
      if (!selected) {
        return;
      }
      setSelectedAttachments((current) => [...current, selected]);
      setAttachmentError(null);
      const registered = await registerSelectedAttachment(selected);
      if (source === "camera" && roomContext && registered) {
        await sendTextMessage(`Report the issue shown in this photo for ${roomContext}.`, [registered]);
      }
    } catch (error) {
      setAttachmentError(error instanceof Error ? error.message : "Attachment could not be selected.");
    } finally {
      if (source === "camera") setVisionAnalyzing(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.screen}>
        <AssistantHeader
          currentUser={currentUser}
          title={frontlineCompletionTask ? "Completed" : activeSection === "profile" ? "Profile" : activeSection === "tasks" && selectedTask ? frontlineRoomLabel(selectedTask.roomOrLocation) : activeSection === "tasks" ? "My Tasks" : experienceMode === "SUPERVISOR" ? "Supervisor" : experienceMode === "MANAGER" ? "Dashboard" : "Home"}
          nested={(activeSection === "tasks" && Boolean(selectedTask)) || Boolean(frontlineCompletionTask)}
          onBack={() => { clearSelectedTask(); setFrontlineCompletionTask(null); setActiveSection(frontlineDetailOrigin === "list" ? "tasks" : "home"); }}
          onMenu={() => { clearSelectedTask(); setFrontlineCompletionTask(null); setActiveSection("home"); }}
          unreadNotificationCount={dashboardSummary?.overview.unreadNotificationCount ?? 0}
          recentNotifications={dashboardSummary?.recentNotifications ?? []}
          notificationsStaleReason={dashboardStaleReason}
          onReset={() => { void resetConversation(); }}
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
            <RoleAdaptiveHome
              mode={experienceMode}
              currentUser={currentUser}
              tasks={tasks}
              homeTask={homeTask}
              overview={overviewForDisplay}
              taskError={errorMessage}
              taskLoading={isLoading}
              actionInProgress={isRefreshing}
              onStartTask={() => void startHomeTask()}
              onResumeTask={() => void resumeHomeTask()}
              onOpenTask={async (taskId) => { setFrontlineDetailOrigin("home"); await selectTask(taskId); setActiveSection("tasks"); }}
              onOpenTasks={() => { setFrontlineCompletionTask(null); clearSelectedTask(); setActiveSection("tasks"); }}
              onAssignTask={(taskId) => { void selectTask(taskId); setActiveSection("tasks"); }}
            />
            {dashboardStaleReason ? (
              <TaskErrorBanner
                title="Offline data"
                message={`${dashboardStaleReason}${dashboardCachedAt ? ` Last updated ${formatCacheTime(dashboardCachedAt)}.` : ""}`}
              />
            ) : null}
            {assistantErrorMessage ? <TaskErrorBanner title="Assistant sync issue" message={assistantErrorMessage} /> : null}
            {errorMessage ? <TaskErrorBanner title="Task sync issue" message={errorMessage} /> : null}
          </ScrollView>
        ) : frontlineSimple && frontlineCompletionTask ? (
          <ScrollView style={styles.homeScroll} contentContainerStyle={styles.homeContent}>
            <FrontlineCompletionScreen task={frontlineCompletionTask} tasks={tasks} onOpenTask={(taskId) => { setFrontlineCompletionTask(null); setFrontlineDetailOrigin("home"); void selectTask(taskId); setActiveSection("tasks"); }} onViewTasks={() => { setFrontlineCompletionTask(null); clearSelectedTask(); setActiveSection("tasks"); }} />
          </ScrollView>
        ) : activeSection === "tasks" && experienceMode === "FRONTLINE_SIMPLE" && selectedTask ? (
          <ScrollView style={styles.homeScroll} contentContainerStyle={styles.taskDetailContent}>
            <TaskDetailCard
              task={selectedTask}
              currentUser={currentUser}
              accessToken={accessToken}
              disabled={isRefreshing}
              onStart={() => void startSelectedTask()}
              onPause={() => void pauseSelectedTask()}
              onResume={() => void resumeSelectedTask()}
              onComplete={async () => { const completedTask = await completeSelectedTask(); if (completedTask) { await refreshTasks(); setFrontlineCompletionTask(completedTask); clearSelectedTask(); } }}
              onCancel={() => void cancelSelectedTask()}
              onInspectionDecision={refreshTasks}
              frontlineSimple
              onReportIssue={() => { void addImageAttachment("camera", selectedTask.roomOrLocation); }}
            />
          </ScrollView>
        ) : activeSection === "tasks" ? (
          <TasksScreen
            accessToken={accessToken}
            frontlineSimple={frontlineSimple}
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
            onSelectTask={async (taskId) => { setFrontlineDetailOrigin("list"); await selectTask(taskId); }}
            onStartTask={startSelectedTask}
            onPauseTask={pauseSelectedTask}
            onResumeTask={resumeSelectedTask}
            onCompleteTask={async () => { await completeSelectedTask(); }}
            onCancelTask={cancelSelectedTask}
            assignmentCandidates={assignmentCandidates}
            onAssignmentOpen={refreshAssignmentCandidates}
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
        ) : activeSection === "operations" ? (
          <AdministrationScreen accessToken={accessToken} currentUser={currentUser} refreshAccessToken={refreshAccessToken} />
        ) : (
          <TaskEmptyState
            title="Operations"
            message={`Operational tools for ${currentUser?.hotelName ?? "this hotel"} will appear here.`}
          />
        )}
        {frontlineSimple && conversationItems.some((item) => item.type === "taskPreview") ? (
          <View style={styles.taskPreviewSurface}>
            <ConversationList
              items={conversationItems.filter((item) => item.type === "taskPreview")}
              roomOptions={Array.from(new Set(tasks.map((item) => item.roomOrLocation).filter((value): value is string => Boolean(value))))}
              onTaskPreviewRoomChange={(room) => { void sendTextMessage(`Use room ${room}`, [], null, []); }}
              onTaskPreviewCancel={() => { void resetConversation(); setComposerText(""); setVoiceTranscript(null); }}
              onTaskPreviewCreate={() => { void handlePreviewCreate(); }}
              isActionDisabled={assistantActionDisabled}
            />
          </View>
        ) : null}
        {frontlineSimple && taskCreateFeedback ? <View style={styles.taskCreateFeedback}><Text style={styles.taskCreateFeedbackText}>{taskCreateFeedback}</Text></View> : null}
        {frontlineSimple && visionAnalyzing ? <View style={styles.visionStatus}><ActivityIndicator color={colors.green} size="small" /><Text style={styles.visionStatusText}>Analyzing issue…</Text></View> : null}
        <View style={[styles.footer, isDesktop ? styles.footerDesktop : null]}>
          {isHomeSurface && showAssistantComposer ? (
            <View style={isTablet && !isDesktop ? styles.composerTablet : null}>
              <View
                accessibilityElementsHidden
                importantForAccessibility="no-hide-descendants"
                style={styles.assistantRelationshipCue}
              >
                <View style={[styles.relationshipBracket, styles.relationshipBracketLeft]} />
                <View style={[styles.relationshipBracket, styles.relationshipBracketRight]} />
              </View>
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
                    void acceptVoiceProposal(proposal);
                  }}
                />
              ) : null}
              disabled={assistantActionDisabled}
              />
            </View>
          ) : null}
          {voiceRecorderVisible && accessToken ? (
            <View style={styles.voiceSurface}>
              <VoiceRecorderPanel
                accessToken={accessToken}
                onClose={() => setVoiceRecorderVisible(false)}
                onUseTranscript={(proposal) => {
                  void acceptVoiceProposal(proposal);
                }}
              />
            </View>
          ) : null}
          <BottomNavigation
            activeKey={activeSection}
            currentUser={currentUser}
            onAssistantPress={() => setVoiceRecorderVisible(true)}
            onSelect={(key) => {
              if (key === "home") {
                setFrontlineCompletionTask(null);
                clearSelectedTask();
                setFrontlineDetailOrigin("home");
                setActiveSection("home");
              } else if (key === "tasks") {
                setFrontlineCompletionTask(null);
                clearSelectedTask();
                setActiveSection("tasks");
              } else if (key === "assistant" || key === "knowledge" || key === "operations" || key === "profile") {
                if (key === "profile") setFrontlineCompletionTask(null);
                setActiveSection(key);
              }
            }}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

function frontlineRoomLabel(value: string | null): string {
  if (!value) return "Task";
  const match = value.match(/room\s+(.+)/i);
  if (match) return `ROOM ${match[1]}`.toUpperCase();
  return /^\d+$/.test(value.trim()) ? `ROOM ${value.trim()}` : value.toUpperCase();
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
  taskPreviewSurface: {
    marginHorizontal: 12,
    marginTop: 12,
    minHeight: 120,
    borderRadius: 16,
    overflow: "hidden"
  },
  visionStatus: {
    marginHorizontal: 12,
    marginTop: 8,
    paddingVertical: 10,
    alignItems: "center",
    borderRadius: 12,
    backgroundColor: "#eef8f1"
  },
  visionStatusText: {
    color: colors.green,
    fontWeight: "800"
  },
  taskCreateFeedback: {
    marginHorizontal: 12,
    marginTop: 8,
    padding: 10,
    borderRadius: 10,
    backgroundColor: "#e9f7ef"
  },
  taskCreateFeedbackText: {
    color: colors.green,
    fontWeight: "800",
    textAlign: "center"
  },
  homeContentTablet: {
    paddingHorizontal: 12
  },
  homeContentDesktop: {
    paddingHorizontal: 24,
    paddingBottom: 18
  },
  taskDetailContent: {
    padding: 12,
    paddingBottom: 24,
    flexGrow: 1
  },
  backToTasks: {
    alignSelf: "flex-start",
    minHeight: 44,
    justifyContent: "center",
    paddingHorizontal: 6
  },
  backToTasksLabel: {
    color: colors.green,
    fontSize: 15,
    fontWeight: "900"
  },
  frontlineIntro: {
    marginHorizontal: 18,
    paddingTop: 14,
    paddingBottom: 6
  },
  frontlineKicker: {
    color: colors.green,
    fontSize: 11,
    fontWeight: "900",
    letterSpacing: 0.8
  },
  frontlineTitle: {
    marginTop: 2,
    color: colors.text,
    fontSize: 22,
    fontWeight: "900"
  },
  footer: {
    paddingTop: 0,
    backgroundColor: colors.background
  },
  footerDesktop: {
    paddingHorizontal: 24,
    paddingBottom: 8
  },
  voiceSurface: {
    marginHorizontal: 12,
    marginBottom: 8,
    borderRadius: 18,
    overflow: "hidden",
    backgroundColor: colors.surface
  },
  composerTablet: {
    paddingHorizontal: 12
  },
  assistantRelationshipCue: {
    height: 6,
    marginHorizontal: 8,
    marginBottom: 0,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between"
  },
  relationshipBracket: {
    width: 13,
    height: 7,
    borderColor: "rgba(148, 163, 184, 0.46)",
    borderWidth: 1
  },
  relationshipBracketLeft: {
    borderRightWidth: 0,
    borderTopLeftRadius: 7,
    borderBottomLeftRadius: 7
  },
  relationshipBracketRight: {
    borderLeftWidth: 0,
    borderTopRightRadius: 7,
    borderBottomRightRadius: 7
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
