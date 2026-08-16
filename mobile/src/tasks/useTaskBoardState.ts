import { AppState, type AppStateStatus } from "react-native";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { assistantStaticMockEnabled } from "../config/assistantConfig";
import { getAppApiErrorMessage, isConnectivityFailure } from "../api/client/AppApiError";
import { nextAssignedTask } from "../assistant/sampleConversation";
import { CurrentUserSnapshot } from "../session/sessionTypes";
import { defaultOfflineCache, taskListCacheKey } from "../offline/offlineCache";
import { defaultOfflineMutationQueue, OfflineMutationType } from "../offline/offlineMutationQueue";
import { AppApiError } from "../api/client/AppApiError";
import { TaskService } from "./TaskService";
import {
  emptyTaskFilters,
  TaskDetail,
  AssignmentCandidate,
  TaskFilterState,
  TaskSummary,
  taskDetailFromResponse
} from "./types";
import { buildTaskBoardOverview, selectHomeTask } from "./taskBoardSelectors";

type TaskBoardState = {
  tasks: TaskSummary[];
  selectedTask: TaskDetail | null;
  selectedTaskId: string | null;
  isLoading: boolean;
  isRefreshing: boolean;
  errorMessage: string | null;
  staleReason: string | null;
  cachedAt: string | null;
  filters: TaskFilterState;
  homeTask: TaskSummary | null;
  overview: ReturnType<typeof buildTaskBoardOverview>;
  updateFilters: (nextFilters: TaskFilterState) => void;
  clearFilters: () => void;
  selectTask: (taskId: string) => Promise<void>;
  refreshTasks: () => Promise<void>;
  startSelectedTask: () => Promise<void>;
  pauseSelectedTask: () => Promise<void>;
  resumeSelectedTask: () => Promise<void>;
  completeSelectedTask: () => Promise<void>;
  cancelSelectedTask: () => Promise<void>;
  startHomeTask: () => Promise<void>;
  resumeHomeTask: () => Promise<void>;
  assignmentCandidates: AssignmentCandidate[];
  refreshAssignmentCandidates: () => Promise<void>;
  assignSelectedTask: (candidate: AssignmentCandidate) => Promise<void>;
};

type TaskCommand = (taskId: string) => Promise<TaskDetail>;

export function useTaskBoardState(
  accessToken: string | null,
  currentUser?: CurrentUserSnapshot | null,
  refreshAccessToken?: () => Promise<string | null>
): TaskBoardState {
  const useMockTasks = assistantStaticMockEnabled;
  const service = useMemo(
    () =>
      new TaskService(() => {
        return accessToken;
      }, refreshAccessToken),
    [accessToken, refreshAccessToken]
  );

  const [tasks, setTasks] = useState<TaskSummary[]>(useMockTasks ? [mockTaskFromSample()] : []);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(
    useMockTasks ? nextAssignedTask?.id ?? null : null
  );
  const [selectedTask, setSelectedTask] = useState<TaskDetail | null>(
    useMockTasks ? mockTaskDetailFromSample() : null
  );
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [staleReason, setStaleReason] = useState<string | null>(null);
  const [cachedAt, setCachedAt] = useState<string | null>(null);
  const [filters, setFilters] = useState<TaskFilterState>(emptyTaskFilters);
  const [assignmentCandidates, setAssignmentCandidates] = useState<AssignmentCandidate[]>([]);
  const selectedTaskIdRef = useRef<string | null>(selectedTaskId);
  const homeTaskRef = useRef<TaskSummary | null>(useMockTasks ? mockTaskFromSample() : null);
  const inFlightRef = useRef(false);
  const filtersRef = useRef<TaskFilterState>(filters);

  const refreshAssignmentCandidates = useCallback(async (taskId: string | null = selectedTaskIdRef.current) => {
    if (!taskId || !currentUser?.permissions?.some((permission) => permission.code === "TASK_ASSIGN")) {
      setAssignmentCandidates([]);
      return;
    }
    setAssignmentCandidates([]);
    const candidates = await service.assignmentCandidates(taskId);
    if (selectedTaskIdRef.current === taskId) {
      setAssignmentCandidates(candidates);
    }
  }, [currentUser?.permissions, service]);

  useEffect(() => {
    selectedTaskIdRef.current = selectedTaskId;
  }, [selectedTaskId]);

  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  const homeTask = useMemo(() => selectHomeTask(tasks), [tasks]);
  const overview = useMemo(() => buildTaskBoardOverview(tasks), [tasks]);

  useEffect(() => {
    homeTaskRef.current = homeTask;
  }, [homeTask]);

  const loadTasks = useCallback(
    async (silent = false) => {
      if (!accessToken && !useMockTasks) {
        setTasks([]);
        setSelectedTask(null);
        setSelectedTaskId(null);
        return;
      }

      if (!silent) {
        setIsLoading(true);
      } else {
        setIsRefreshing(true);
      }

      setErrorMessage(null);

      try {
        if (useMockTasks) {
          const mockTask = mockTaskFromSample();
          const mockDetail = mockTaskDetailFromSample();
          setTasks([mockTask]);
          setSelectedTaskId(mockTask.id);
          setSelectedTask(mockDetail);
          return;
        }

        if(currentUser?.hotelId&&currentUser.userId&&globalThis.navigator?.onLine!==false){
          await defaultOfflineMutationQueue.sync({hotelId:currentUser.hotelId,userId:currentUser.userId},item=>service.submitOfflineOperation(item));
        }

        const nextTasks = await service.listTasks(filtersRef.current);
        setTasks(nextTasks);
        const cacheKey = scopedTaskCacheKey(currentUser, filtersRef.current);
        if (cacheKey) {
          void defaultOfflineCache.save(cacheKey, nextTasks);
        }
        setStaleReason(null);
        setCachedAt(null);
        if (nextTasks.length === 0) {
          setSelectedTaskId(null);
          setSelectedTask(null);
          return;
        }

        const firstTask = nextTasks[0];
        if (!firstTask) {
          setSelectedTaskId(null);
          setSelectedTask(null);
          return;
        }

        const resolvedId: string =
          selectedTaskIdRef.current &&
          nextTasks.some((task) => task.id === selectedTaskIdRef.current)
            ? selectedTaskIdRef.current
            : selectHomeTask(nextTasks)?.id ?? firstTask.id;
        setSelectedTaskId(resolvedId);
        const detail = await service.getTask(resolvedId);
        setSelectedTask(detail);
        await refreshAssignmentCandidates(resolvedId);
      } catch (error) {
        const message = getAppApiErrorMessage(error);
        setErrorMessage(message);
        if (!isConnectivityFailure(error)) {
          setStaleReason(null);
          setCachedAt(null);
          return;
        }
        const key = scopedTaskCacheKey(currentUser, filtersRef.current);
        if (tasks.length === 0 && key) {
          const cached = await defaultOfflineCache.load<TaskSummary[]>(key);
          if (cached) {
            setTasks(cached.data);
            setCachedAt(cached.cachedAt);
            setStaleReason("Refresh failed. Showing last saved data.");
            const firstTask = cached.data[0];
            setSelectedTaskId(firstTask?.id ?? null);
            setSelectedTask(null);
          } else {
            setStaleReason("No saved data is available offline.");
            setCachedAt(null);
          }
        } else if (tasks.length > 0) {
          setStaleReason("Refresh failed. Showing last saved data.");
        }
      } finally {
        if (!silent) {
          setIsLoading(false);
        } else {
          setIsRefreshing(false);
        }
      }
    },
    [accessToken, service, useMockTasks,currentUser,refreshAssignmentCandidates]
  );

  useEffect(() => {
    void loadTasks();
  }, [loadTasks, filters]);

  useEffect(() => {
    let previousState: AppStateStatus = AppState.currentState;
    const subscription = AppState.addEventListener("change", (nextState) => {
      const becameActive = previousState !== "active" && nextState === "active";
      previousState = nextState;
      if (becameActive) {
        void loadTasks(true);
      }
    });
    return () => subscription.remove();
  }, [loadTasks]);

  const selectTask = useCallback(
    async (taskId: string) => {
      setSelectedTaskId(taskId);
      setErrorMessage(null);

      if (useMockTasks) {
        setSelectedTask(mockTaskDetailFromSample());
        setStaleReason(null);
        return;
      }

      try {
        setIsRefreshing(true);
        const detail = await service.getTask(taskId);
        setSelectedTask(detail);
        await refreshAssignmentCandidates(taskId);
      } catch (error) {
        setErrorMessage(getAppApiErrorMessage(error));
      } finally {
        setIsRefreshing(false);
      }
    },
    [accessToken, service, useMockTasks, refreshAssignmentCandidates]
  );

  const runCommand = useCallback(
    async (operation:OfflineMutationType|null,command: TaskCommand): Promise<boolean> => {
      if (inFlightRef.current) {
        return false;
      }

      const taskId = selectedTaskIdRef.current;
      if (!taskId) {
        return false;
      }

      try {
        inFlightRef.current = true;
        setIsRefreshing(true);
        const updatedTask = await command(taskId);
        setSelectedTask(updatedTask);
        try {
          await refreshAssignmentCandidates(updatedTask.id);
        } catch {
          // A successful assignment must not be reported as failed if the
          // follow-up candidate refresh is temporarily unavailable.
        }
        setTasks((current) =>
          current.map((task) => (task.id === updatedTask.id ? taskSummaryFromDetail(updatedTask) : task))
        );
        return true;
      } catch (error) {
        const scope=currentUser?.hotelId&&currentUser.userId?{hotelId:currentUser.hotelId,userId:currentUser.userId}:null;
        if(operation && scope && (error instanceof AppApiError ? error.kind==="network"||error.kind==="timeout" : !globalThis.navigator?.onLine)){
          await defaultOfflineMutationQueue.enqueue(scope,operation,taskId);
          setErrorMessage("Saved offline. This operation will sync when connectivity returns.");
        } else setErrorMessage(getAppApiErrorMessage(error));
        return false;
      } finally {
        inFlightRef.current = false;
        setIsRefreshing(false);
      }
    },
    [selectedTaskId,currentUser,refreshAssignmentCandidates]
  );

  const runHomeCommand = useCallback(
    async (command: TaskCommand) => {
      if (inFlightRef.current) {
        return;
      }

      const task = homeTaskRef.current;
      if (!task) {
        return;
      }

      try {
        inFlightRef.current = true;
        setIsRefreshing(true);
        const updatedTask = await command(task.id);
        setTasks((current) =>
          current.map((item) => (item.id === updatedTask.id ? taskSummaryFromDetail(updatedTask) : item))
        );
        if (selectedTaskIdRef.current === updatedTask.id) {
          setSelectedTask(updatedTask);
        }
      } catch (error) {
        setErrorMessage(getAppApiErrorMessage(error));
      } finally {
        inFlightRef.current = false;
        setIsRefreshing(false);
      }
    },
    [homeTask]
  );

  const refreshTasks = useCallback(async () => {
    await loadTasks(true);
  }, [loadTasks]);

  const updateFilters = useCallback((nextFilters: TaskFilterState) => {
    setFilters(nextFilters);
  }, []);

  const clearFilters = useCallback(() => {
    setFilters(emptyTaskFilters());
  }, []);

  return {
    tasks,
    selectedTask,
    selectedTaskId,
    isLoading,
    isRefreshing,
      errorMessage,
      staleReason,
      cachedAt,
    filters,
    homeTask,
    overview,
    updateFilters,
    clearFilters,
    selectTask,
    refreshTasks,
    startSelectedTask: async () => { await runCommand("TASK_START",(taskId) => service.startTask(taskId)); },
    pauseSelectedTask: async () => { await runCommand("TASK_PAUSE",(taskId) => service.pauseTask(taskId)); },
    resumeSelectedTask: async () => { await runCommand("TASK_RESUME",(taskId) => service.resumeTask(taskId)); },
    completeSelectedTask: async () => { await runCommand("TASK_COMPLETE",(taskId) => service.completeTask(taskId)); },
    cancelSelectedTask: async () => { await runCommand(null,(taskId) => service.cancelTask(taskId)); },
    startHomeTask: async () => runHomeCommand((taskId) => service.startTask(taskId)),
    resumeHomeTask: async () => runHomeCommand((taskId) => service.resumeTask(taskId)),
    assignmentCandidates,
    refreshAssignmentCandidates: async () => {
      await refreshAssignmentCandidates();
    },
    assignSelectedTask: async (candidate) => {
      const succeeded = await runCommand(null, (taskId) => service.assignTask(taskId, candidate));
      if (!succeeded) throw new Error("Assignment failed. Try again.");
    }
  };
}

function scopedTaskCacheKey(currentUser: CurrentUserSnapshot | null | undefined, filters: TaskFilterState): string | null {
  if (!currentUser?.hotelId || !currentUser.userId) {
    return null;
  }

  return taskListCacheKey(
    {
      hotelId: currentUser.hotelId,
      userId: currentUser.userId
    },
    filters
  );
}

function taskSummaryFromDetail(task: TaskDetail): TaskSummary {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    status: task.status,
    priority: task.priority,
    slaDeadline: task.slaDeadline,
    roomOrLocation: task.roomOrLocation,
    assignmentLabel: task.assignmentLabel,
    updatedAt: task.updatedAt,
    intentType: task.intentType,
    source: task.source,
    unassignedReasonCode: task.unassignedReasonCode,
    unassignedReason: task.unassignedReason
  };
}

function mockTaskFromSample(): TaskSummary {
  return {
    id: nextAssignedTask?.id ?? "next-task-203",
    title: nextAssignedTask?.title ?? "Deliver Extra Towels",
    description: "Demo task",
    status: "ASSIGNED",
    priority: nextAssignedTask?.priority ?? "Medium Priority",
    slaDeadline: new Date(Date.now() + 18 * 60 * 60 * 1000 + 42 * 60 * 1000).toISOString(),
    roomOrLocation: nextAssignedTask?.room ?? "Room 203",
    assignmentLabel: "Housekeeping",
    updatedAt: new Date().toISOString(),
    intentType: "HOUSEKEEPING",
    source: "STATIC_MOCK",
    unassignedReasonCode: null,
    unassignedReason: null
  };
}

function mockTaskDetailFromSample(): TaskDetail {
  const summary = mockTaskFromSample();
  return {
    ...summary,
    hotelId: "hotel-opai-demo",
    createdAt: new Date().toISOString(),
    startedAt: null,
    completedAt: null,
    cancelledAt: null,
    overdueAt: null,
    assigneeType: "DEPARTMENT",
    assigneeId: "housekeeping",
    assignedAt: new Date().toISOString()
  };
}
