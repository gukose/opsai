import { useCallback, useEffect, useRef, useState } from "react";

import {
  assistantBackendEnabled,
  assistantDataSourceMode,
  assistantStaticMockEnabled
} from "../config/assistantConfig";
import { createAssistantHomeDataSource } from "./assistantDataSourceFactory";
import { AssistantHomeState, createEmptyAssistantHomeState } from "./homeState";
import { ConversationItem } from "./types";

const dataSource = createAssistantHomeDataSource();

type AssistantHomeController = AssistantHomeState & {
  isBackendMode: boolean;
  sendTextMessage: (text: string) => Promise<void>;
  confirmTask: () => Promise<void>;
  resetConversation: () => Promise<void>;
};

type AssistantSessionSnapshot = {
  conversationId?: string;
  confirmationIdempotencyKey?: string | null;
  createdTaskId?: string | null;
};

export function useAssistantHomeState(): AssistantHomeController {
  const [state, setState] = useState<AssistantHomeState>(
    createEmptyAssistantHomeState(assistantDataSourceMode)
  );
  const stateRef = useRef<AssistantHomeState>(
    createEmptyAssistantHomeState(assistantDataSourceMode)
  );
  const sessionRef = useRef<AssistantSessionSnapshot>({
    conversationId: undefined,
    confirmationIdempotencyKey: null,
    createdTaskId: null
  });
  const bootstrapPromiseRef = useRef<Promise<AssistantHomeState> | null>(null);

  const applyState = useCallback((nextState: AssistantHomeState) => {
    stateRef.current = nextState;
    setState(nextState);
    sessionRef.current = {
      conversationId: nextState.conversationId ?? sessionRef.current.conversationId,
      confirmationIdempotencyKey: nextState.confirmationIdempotencyKey ?? null,
      createdTaskId: nextState.createdTaskId ?? null
    };
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
  }, [applyState]);

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
          applyState(createEmptyAssistantHomeState(assistantDataSourceMode));
        }

        return createEmptyAssistantHomeState(assistantDataSourceMode);
      });

    return () => {
      cancelled = true;
      bootstrapPromiseRef.current = null;
    };
  }, [applyState]);

  const sendTextMessage = useCallback(
    async (text: string) => {
      const message = text.trim();
      if (!message || assistantStaticMockEnabled) {
        return;
      }

      let previousState: AssistantHomeState | null = null;

      try {
        const conversationId = await ensureConversation();
        if (!conversationId) {
          return;
        }

        previousState = stateRef.current;
        const optimisticState = appendUserMessage(previousState, message);
        applyState(optimisticState);

        const nextState = await dataSource.sendTextMessage(conversationId, message);
        applyState(nextState);
      } catch (error) {
        console.warn("Assistant message send failed.", error);
        if (previousState) {
          applyState(previousState);
        }
      }
    },
    [applyState, ensureConversation]
  );

  const confirmTask = useCallback(async () => {
    if (assistantStaticMockEnabled) {
      return;
    }

    try {
      const conversationId = await ensureConversation();
      if (!conversationId) {
        return;
      }

      const idempotencyKey =
        sessionRef.current.confirmationIdempotencyKey ?? buildIdempotencyKey();

      const nextState = await dataSource.confirmTask(conversationId, idempotencyKey);
      applyState(nextState);
    } catch (error) {
      console.warn("Assistant task confirmation failed.", error);
    }
  }, [applyState, ensureConversation]);

  const resetConversation = useCallback(async () => {
    if (assistantStaticMockEnabled) {
      applyState(await dataSource.loadHomeState());
      return;
    }

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
      applyState(previousState);
    }
  }, [applyState, ensureConversation]);

  return {
    ...state,
    isBackendMode: assistantBackendEnabled,
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
  text: string
): AssistantHomeState {
  const message: ConversationItem = {
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type: "text",
    author: "user",
    text,
    timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
  };

  return {
    ...state,
    conversationItems: [...state.conversationItems, message]
  };
}
