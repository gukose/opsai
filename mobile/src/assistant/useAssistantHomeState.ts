import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  assistantBackendEnabled,
  assistantDataSourceMode,
  assistantStaticMockEnabled
} from "../config/assistantConfig";
import { getAppApiErrorMessage } from "../api/client/AppApiError";
import { CurrentUserSnapshot } from "../session/sessionTypes";
import { createAssistantHomeDataSource } from "./assistantDataSourceFactory";
import { AssistantHomeState, createEmptyAssistantHomeState } from "./homeState";
import { ConversationItem } from "./types";
import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";

type AssistantHomeController = AssistantHomeState & {
  isBackendMode: boolean;
  isSending: boolean;
  isConfirming: boolean;
  errorMessage: string | null;
  sendTextMessage: (
    text: string,
    attachments?: LocalAttachmentMetadata[],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations?: LocalImageObservationMetadata[]
  ) => Promise<boolean>;
  confirmTask: () => Promise<string | null>;
  resetConversation: () => Promise<void>;
};

type AssistantSessionSnapshot = {
  conversationId?: string;
  confirmationIdempotencyKey?: string | null;
  createdTaskId?: string | null;
};

type UseAssistantHomeStateOptions = {
  accessToken: string | null;
  currentUser: CurrentUserSnapshot | null;
};

export function useAssistantHomeState({
  accessToken,
  currentUser
}: UseAssistantHomeStateOptions): AssistantHomeController {
  const accessTokenRef = useRef(accessToken);
  const currentUserRef = useRef(currentUser);
  const [state, setState] = useState<AssistantHomeState>(
    createEmptyAssistantHomeState(assistantDataSourceMode)
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [isConfirming, setIsConfirming] = useState(false);
  const stateRef = useRef<AssistantHomeState>(
    createEmptyAssistantHomeState(assistantDataSourceMode)
  );
  const sessionRef = useRef<AssistantSessionSnapshot>({
    conversationId: undefined,
    confirmationIdempotencyKey: null,
    createdTaskId: null
  });
  const bootstrapPromiseRef = useRef<Promise<AssistantHomeState> | null>(null);
  const requestLockRef = useRef<"send" | "confirm" | "reset" | null>(null);

  useEffect(() => {
    accessTokenRef.current = accessToken;
  }, [accessToken]);

  useEffect(() => {
    currentUserRef.current = currentUser;
  }, [currentUser]);

  const dataSource = useMemo(
    () =>
      createAssistantHomeDataSource({
        accessTokenProvider: () => accessTokenRef.current,
        currentUserProvider: () => currentUserRef.current
      }),
    []
  );

  const applyState = useCallback((nextState: AssistantHomeState) => {
    stateRef.current = nextState;
    setState(nextState);
    setErrorMessage(null);
    sessionRef.current = {
      conversationId: nextState.conversationId ?? sessionRef.current.conversationId,
      confirmationIdempotencyKey: nextState.confirmationIdempotencyKey ?? null,
      createdTaskId: nextState.createdTaskId ?? null
    };
  }, []);

  const handleError = useCallback((error: unknown, fallbackState?: AssistantHomeState) => {
    const message = getAppApiErrorMessage(error);
    setErrorMessage(message);

    if (fallbackState) {
      stateRef.current = fallbackState;
      setState(fallbackState);
    }
  }, []);

  const ensureConversation = useCallback(async (): Promise<string | null> => {
    if (sessionRef.current.conversationId) {
      return sessionRef.current.conversationId;
    }

    if (bootstrapPromiseRef.current) {
      const loadedState = await bootstrapPromiseRef.current;
      if (loadedState.conversationId) {
        return loadedState.conversationId;
      }
    }

    const initialState = await dataSource.loadHomeState();
    applyState(initialState);
    return initialState.conversationId ?? null;
  }, [applyState, dataSource]);

  useEffect(() => {
    let cancelled = false;

    bootstrapPromiseRef.current = dataSource
      .loadHomeState()
      .then((nextState) => {
        if (!cancelled) {
          applyState(nextState);
        }

        return nextState;
      })
      .catch((error) => {
        if (!cancelled) {
          console.warn("Assistant bootstrap failed, keeping empty state.", error);
          handleError(error, createEmptyAssistantHomeState(assistantDataSourceMode));
        }

        return createEmptyAssistantHomeState(assistantDataSourceMode);
      });

    return () => {
      cancelled = true;
      bootstrapPromiseRef.current = null;
    };
  }, [applyState, dataSource, handleError]);

  const sendTextMessage = useCallback(
    async (
      text: string,
      attachments: LocalAttachmentMetadata[] = [],
      voiceTranscript?: LocalVoiceTranscriptMetadata | null,
      imageObservations: LocalImageObservationMetadata[] = []
    ) => {
      const message = text.trim();
      const hasSemanticInput = Boolean(voiceTranscript?.transcript.trim()) || imageObservations.length > 0;
      if ((!message && attachments.length === 0 && !hasSemanticInput) || assistantStaticMockEnabled) {
        return false;
      }

      if (requestLockRef.current) {
        return false;
      }

      requestLockRef.current = "send";
      setIsSending(true);
      setErrorMessage(null);

      let previousState: AssistantHomeState | null = null;

      try {
        const conversationId = await ensureConversation();
        if (!conversationId) {
          return false;
        }

        previousState = stateRef.current;
        if (assistantBackendEnabled) {
          applyState(appendUserMessage(previousState, message, voiceTranscript, imageObservations));
        }

        const nextState = await dataSource.sendTextMessage(
          conversationId,
          message,
          attachments,
          voiceTranscript,
          imageObservations
        );
        applyState(nextState);
        return true;
      } catch (error) {
        console.warn("Assistant message send failed.", error);
        handleError(error, previousState ?? stateRef.current);
        return false;
      } finally {
        requestLockRef.current = null;
        setIsSending(false);
      }
    },
    [applyState, dataSource, ensureConversation, handleError]
  );

  const confirmTask = useCallback(async () => {
    if (assistantStaticMockEnabled) {
      return null;
    }

    if (requestLockRef.current) {
      return null;
    }

    requestLockRef.current = "confirm";
    setIsConfirming(true);
    setErrorMessage(null);

    try {
      const conversationId = await ensureConversation();
      if (!conversationId) {
        return null;
      }

      const idempotencyKey =
        sessionRef.current.confirmationIdempotencyKey ?? buildIdempotencyKey();

      const nextState = await dataSource.confirmTask(conversationId, idempotencyKey);
      applyState(nextState);
      return nextState.createdTaskId ?? null;
    } catch (error) {
      console.warn("Assistant task confirmation failed.", error);
      handleError(error, stateRef.current);
      return null;
    } finally {
      requestLockRef.current = null;
      setIsConfirming(false);
    }
  }, [applyState, dataSource, ensureConversation, handleError]);

  const resetConversation = useCallback(async () => {
    if (assistantStaticMockEnabled) {
      applyState(await dataSource.loadHomeState());
      return;
    }

    if (requestLockRef.current) {
      return;
    }

    requestLockRef.current = "reset";
    const previousState = stateRef.current;

    try {
      const conversationId = await ensureConversation();
      if (!conversationId) {
        return;
      }

      applyState({
        ...createEmptyAssistantHomeState(assistantDataSourceMode),
        conversationId
      });

      const nextState = await dataSource.resetConversation(conversationId);
      applyState(nextState);
    } catch (error) {
      console.warn("Assistant conversation reset failed.", error);
      handleError(error, previousState);
    } finally {
      requestLockRef.current = null;
    }
  }, [applyState, dataSource, ensureConversation, handleError]);

  return {
    ...state,
    isBackendMode: assistantBackendEnabled,
    isSending,
    isConfirming,
    errorMessage,
    sendTextMessage,
    confirmTask,
    resetConversation
  };
}

function buildIdempotencyKey(): string {
  return `confirm-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function appendUserMessage(
  state: AssistantHomeState,
  text: string,
  voiceTranscript?: LocalVoiceTranscriptMetadata | null,
  imageObservations: LocalImageObservationMetadata[] = []
): AssistantHomeState {
  const timestamp = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const messages: ConversationItem[] = [
    ...(text
      ? [{
          id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          type: "text" as const,
          author: "user" as const,
          text,
          timestamp
        }]
      : []),
    ...(voiceTranscript
      ? [{
          id: `local-voice-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          type: "voice" as const,
          author: "user" as const,
          transcript: voiceTranscript.transcript,
          duration: voiceTranscript.durationMs ? `${Math.round(voiceTranscript.durationMs / 1000)}s` : "0:00",
          timestamp
        }]
      : []),
    ...imageObservations.map((observation) => ({
      id: `local-observation-${observation.id}`,
      type: "text" as const,
      author: "user" as const,
      text: `Image note: ${observation.text}`,
      timestamp
    }))
  ];

  return {
    ...state,
    conversationItems: [...state.conversationItems, ...messages]
  };
}
