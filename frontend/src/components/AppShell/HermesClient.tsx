"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AuthScreen } from "@/components/AuthScreen/AuthScreen";
import { CompleteRegistrationScreen } from "@/components/CompleteRegistrationScreen/CompleteRegistrationScreen";
import { AppShell } from "@/components/AppShell/AppShell";
import { ToastViewport } from "@/components/ToastViewport/ToastViewport";
import { PhotoViewerModal } from "@/components/PhotoViewerModal/PhotoViewerModal";
import {
  type ApiRequestOptions,
  type UploadProgressState,
  apiRequest,
  completeHermesRegistration,
  exchangeBootstrapToken,
  loginHermesAccount,
  logoutSession,
  readStoredAccessToken,
  registerHermesAccount,
  refreshAccessToken,
  writeStoredAccessToken,
} from "@/lib/api";
import { isTelegramConversationTitle } from "@/lib/format";
import {
  readChannelMessageCache,
  removeChannelMessageCache,
  upsertChannelMessageCache,
  writeChannelMessageCache,
} from "@/lib/channelMessageCache";
import { cleanupProtectedMediaCache } from "@/lib/protectedMediaCache";
import { subscribeToConversationMessages } from "@/lib/ws";
import type { ConversationSocketSession } from "@/lib/ws";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationReadPayload,
  ConversationSocketEvent,
  ConversationSocketSummaryPayload,
  ConversationTypingPayload,
  ConversationSummary,
  DrawerMode,
  InviteAcceptance,
  PendingAttachment,
  PhotoViewerState,
  TdlightAvailableChannel,
  TdlightConnection,
  ToastMessage,
} from "@/types/api";

const EMPTY_PHOTO_VIEWER: PhotoViewerState = { items: [], activeIndex: 0 };
const SESSION_BOOT_TIMEOUT_MS = 12000;
const CHANNELS_REFRESH_TIMEOUT_MS = 10000;
const CHANNEL_SUBSCRIBE_TIMEOUT_MS = 15000;
const LAST_CONVERSATION_STORAGE_PREFIX = "hermes:last-conversation";

function pickPreferredTdlightConnection(connections: TdlightConnection[]): TdlightConnection | null {
  return (
    [...connections].sort((left, right) => {
      const leftVerified = left.lastVerifiedAt ? 1 : 0;
      const rightVerified = right.lastVerifiedAt ? 1 : 0;
      if (leftVerified !== rightVerified) {
        return rightVerified - leftVerified;
      }

      const leftAuthorized = left.tdlightUserId ? 1 : 0;
      const rightAuthorized = right.tdlightUserId ? 1 : 0;
      if (leftAuthorized !== rightAuthorized) {
        return rightAuthorized - leftAuthorized;
      }

      return right.createdAt.localeCompare(left.createdAt);
    })[0] ?? null
  );
}

function pickAuthorizedTdlightConnection(connections: TdlightConnection[]): TdlightConnection | null {
  return pickPreferredTdlightConnection(
    connections.filter((connection) => Boolean(connection.lastVerifiedAt && connection.tdlightUserId)),
  );
}

function isChannelConversation(conversation: ConversationSummary | null | undefined) {
  return isTelegramConversationTitle(conversation?.title);
}

function lastConversationStorageKey(user: Pick<AuthUser, "id" | "tenantKey">) {
  return `${LAST_CONVERSATION_STORAGE_PREFIX}:${user.tenantKey}:${user.id}`;
}

function readLastConversationId(user: Pick<AuthUser, "id" | "tenantKey">) {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(lastConversationStorageKey(user));
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function writeLastConversationId(user: Pick<AuthUser, "id" | "tenantKey">, conversationId: number | null) {
  if (typeof window === "undefined") {
    return;
  }

  const key = lastConversationStorageKey(user);
  if (!conversationId || !Number.isFinite(conversationId) || conversationId <= 0) {
    window.localStorage.removeItem(key);
    return;
  }

  window.localStorage.setItem(key, String(conversationId));
}

export function HermesClient() {
  const [token, setToken] = useState("");
  const [booting, setBooting] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [me, setMe] = useState<AuthUser | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [members, setMembers] = useState<ConversationMember[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [loadingConversationData, setLoadingConversationData] = useState(false);
  const [tdlightConnectionId, setTdlightConnectionId] = useState<number | null>(null);
  const [availableChannels, setAvailableChannels] = useState<TdlightAvailableChannel[]>([]);
  const [loadingAvailableChannels, setLoadingAvailableChannels] = useState(false);
  const [drawerMode, setDrawerMode] = useState<DrawerMode>(null);
  const [drawerConversationId, setDrawerConversationId] = useState<number | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [photoViewer, setPhotoViewer] = useState<PhotoViewerState>(EMPTY_PHOTO_VIEWER);
  const [wsConnected, setWsConnected] = useState(false);
  const [typingByConversation, setTypingByConversation] = useState<Record<number, Record<number, string>>>({});
  const [readByConversation, setReadByConversation] = useState<
    Record<number, Record<number, { displayName: string; readAt: string }>>
  >({});
  const toastIdRef = useRef(1);
  const lastReadConversationIdRef = useRef<number | null>(null);
  const conversationLoadSeqRef = useRef(0);
  const conversationsRef = useRef<ConversationSummary[]>([]);
  const wsSessionRef = useRef<ConversationSocketSession | null>(null);
  const typingTimeoutsRef = useRef<Record<string, number>>({});
  const wasWsConnectedRef = useRef(false);
  const refreshPromiseRef = useRef<Promise<string> | null>(null);

  const invalidatePendingConversationLoad = useCallback(() => {
    conversationLoadSeqRef.current += 1;
    setLoadingConversationData(false);
  }, []);

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedConversationId) ?? null,
    [conversations, selectedConversationId],
  );

  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  useEffect(() => {
    if (!me) {
      return;
    }

    if (booting || authLoading) {
      return;
    }

    if (!selectedConversationId && conversations.length === 0) {
      return;
    }

    writeLastConversationId(me, selectedConversationId);
  }, [authLoading, booting, conversations.length, me, selectedConversationId]);
  const selectedTypingNames = useMemo(() => {
    if (!selectedConversationId) {
      return [];
    }
    return Object.values(typingByConversation[selectedConversationId] ?? {}).sort((left, right) =>
      left.localeCompare(right, "ru"),
    );
  }, [selectedConversationId, typingByConversation]);

  const drawerConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === drawerConversationId) ?? null,
    [conversations, drawerConversationId],
  );
  const subscribedConversationIds = useMemo(
    () => conversations.map((conversation) => conversation.id).sort((left, right) => left - right),
    [conversations],
  );
  const subscribedConversationIdsKey = subscribedConversationIds.join(",");

  const pushToast = useCallback((message: string, kind: ToastMessage["kind"]) => {
    const id = toastIdRef.current++;
    setToasts((current) => [...current, { id, kind, message }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((item) => item.id !== id));
    }, 4200);
  }, []);

  const withClientTimeout = useCallback(async <T,>(promise: Promise<T>, timeoutMs: number, message: string) => {
    let timeoutId: number | null = null;
    try {
      return await Promise.race<T>([
        promise,
        new Promise<T>((_, reject) => {
          timeoutId = window.setTimeout(() => {
            reject(new Error(message));
          }, timeoutMs);
        }),
      ]);
    } finally {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId);
      }
    }
  }, []);

  const clearSession = useCallback(() => {
    writeStoredAccessToken("");
    setToken("");
    setMe(null);
    setConversations([]);
    setSelectedConversationId(null);
    setMessages([]);
    setMembers([]);
    setTypingByConversation({});
    setReadByConversation({});
    setTdlightConnectionId(null);
    setAvailableChannels([]);
    setDrawerMode(null);
    setDrawerConversationId(null);
  }, []);

  const refreshSession = useCallback(async () => {
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current;
    }

    const pendingRefresh = refreshAccessToken()
      .then((response) => {
        setToken(response.accessToken);
        writeStoredAccessToken(response.accessToken);
        return response.accessToken;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });

    refreshPromiseRef.current = pendingRefresh;
    return pendingRefresh;
  }, []);

  const request = useCallback(
    async <T,>(path: string, options: ApiRequestOptions = {}) => {
      const runRequest = async (currentToken: string) => apiRequest<T>(currentToken, path, options);

      try {
        return await runRequest(token);
      } catch (error) {
        if (error instanceof Error && error.message.includes("HTTP 401")) {
          try {
            const refreshedToken = await refreshSession();
            return await runRequest(refreshedToken);
          } catch (refreshError) {
            clearSession();
            throw refreshError;
          }
        }
        throw error;
      }
    },
    [clearSession, refreshSession, token],
  );

  const summarizeConversationMessage = useCallback((message: ConversationMessage) => {
    const body = message.body?.trim();
    if (body) {
      return body.length > 120 ? `${body.slice(0, 117)}...` : body;
    }

    if (message.attachments.length === 0) {
      return "Сообщение";
    }

    if (message.attachments.length > 1) {
      return `${message.attachments.length} вложения`;
    }

    const attachment = message.attachments[0];
    switch (attachment.kind) {
      case "PHOTO":
        return "Фото";
      case "VIDEO":
        return "Видео";
      case "VIDEO_NOTE":
        return "Кружок";
      case "VOICE":
        return "Голосовое";
      default:
        return "Файл";
    }
  }, []);

  const upsertConversationMessage = useCallback((message: ConversationMessage) => {
    setMessages((current) => {
      const withoutDuplicate = current.filter((item) => item.id !== message.id);
      return [...withoutDuplicate, message].sort((left, right) => {
        if (left.createdAt === right.createdAt) {
          return left.id - right.id;
        }
        return left.createdAt.localeCompare(right.createdAt);
      });
    });
  }, []);

  const applyConversationMessageSummary = useCallback(
    (message: ConversationMessage, options: { unreadCount?: number } = {}) => {
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === message.conversationId
            ? {
                ...conversation,
                lastMessagePreview: summarizeConversationMessage(message),
                lastMessageCreatedAt: message.createdAt,
                ...(typeof options.unreadCount === "number" ? { unreadCount: options.unreadCount } : {}),
              }
            : conversation,
        ),
      );
    },
    [summarizeConversationMessage],
  );

  const markConversationReadLocally = useCallback(
    async (conversationId: number) => {
      lastReadConversationIdRef.current = conversationId;
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === conversationId
            ? { ...conversation, unreadCount: 0, hasUnreadMention: false }
            : conversation,
        ),
      );
      if (wsSessionRef.current?.sendConversationRead(conversationId)) {
        return;
      }
      await request(`/api/conversations/${conversationId}/read`, {
        method: "POST",
        body: JSON.stringify({}),
      });
    },
    [request],
  );

  const updateTypingState = useCallback(
    (payload: ConversationTypingPayload) => {
      if (!payload.conversationId || !payload.userId || !me || payload.userId === me.id) {
        return;
      }

      const timerKey = `${payload.conversationId}:${payload.userId}`;
      const clearExistingTimer = () => {
        const handle = typingTimeoutsRef.current[timerKey];
        if (typeof handle === "number") {
          window.clearTimeout(handle);
          delete typingTimeoutsRef.current[timerKey];
        }
      };

      if (!payload.active) {
        clearExistingTimer();
        setTypingByConversation((current) => {
          const nextConversationTyping = { ...(current[payload.conversationId] ?? {}) };
          delete nextConversationTyping[payload.userId];
          if (Object.keys(nextConversationTyping).length === 0) {
            const next = { ...current };
            delete next[payload.conversationId];
            return next;
          }
          return { ...current, [payload.conversationId]: nextConversationTyping };
        });
        return;
      }

      setTypingByConversation((current) => ({
        ...current,
        [payload.conversationId]: {
          ...(current[payload.conversationId] ?? {}),
          [payload.userId]: payload.displayName,
        },
      }));

      clearExistingTimer();
      typingTimeoutsRef.current[timerKey] = window.setTimeout(() => {
        setTypingByConversation((current) => {
          const nextConversationTyping = { ...(current[payload.conversationId] ?? {}) };
          delete nextConversationTyping[payload.userId];
          if (Object.keys(nextConversationTyping).length === 0) {
            const next = { ...current };
            delete next[payload.conversationId];
            return next;
          }
          return { ...current, [payload.conversationId]: nextConversationTyping };
        });
        delete typingTimeoutsRef.current[timerKey];
      }, 4000);
    },
    [me],
  );

  const sendTypingState = useCallback((conversationId: number, active: boolean) => {
    if (!conversationId) {
      return;
    }
    wsSessionRef.current?.sendTypingState(conversationId, active);
  }, []);

  const upsertConversationReadState = useCallback((payload: ConversationReadPayload) => {
    if (!payload.conversationId || !payload.userId || !payload.readAt) {
      return;
    }

    setReadByConversation((current) => {
      const nextConversationReads = { ...(current[payload.conversationId] ?? {}) };
      const existing = nextConversationReads[payload.userId];
      if (existing && existing.readAt >= payload.readAt) {
        return current;
      }

      nextConversationReads[payload.userId] = {
        displayName: payload.displayName,
        readAt: payload.readAt,
      };

      return {
        ...current,
        [payload.conversationId]: nextConversationReads,
      };
    });
  }, []);

  const loadConversations = useCallback(async (options: { silent?: boolean } = {}) => {
    if (!token || !me) {
      return;
    }

    const { silent = false } = options;

    if (!silent) {
      setLoadingConversations(true);
    }
    try {
      const nextConversations = await request<ConversationSummary[]>("/api/conversations");
      setConversations(nextConversations);
      const rememberedConversationId = readLastConversationId(me);

      setSelectedConversationId((current) => {
        if (current && nextConversations.some((conversation) => conversation.id === current)) {
          return current;
        }
        if (
          rememberedConversationId &&
          nextConversations.some((conversation) => conversation.id === rememberedConversationId)
        ) {
          return rememberedConversationId;
        }
        return nextConversations[0]?.id ?? null;
      });

      setDrawerConversationId((current) =>
        current && nextConversations.some((conversation) => conversation.id === current) ? current : null,
      );
    } finally {
      if (!silent) {
        setLoadingConversations(false);
      }
    }
  }, [me, request, token]);

  const loadAvailableChannels = useCallback(async (options: { silent?: boolean } = {}) => {
    if (!token) {
      return;
    }

    const { silent = false } = options;
    if (!silent) {
      setLoadingAvailableChannels(true);
    }

    try {
      const connections = await withClientTimeout(
        request<TdlightConnection[]>("/api/tdlight/connections"),
        CHANNELS_REFRESH_TIMEOUT_MS,
        "Истекло время ожидания списка Telegram-сессий.",
      );
      const primaryConnection = pickAuthorizedTdlightConnection(connections);
      setTdlightConnectionId(primaryConnection?.id ?? null);

      if (!primaryConnection) {
        setAvailableChannels([]);
        return;
      }

      const nextChannels = await withClientTimeout(
        request<TdlightAvailableChannel[]>(
          `/api/tdlight/channels?tdlightConnectionId=${primaryConnection.id}`,
        ),
        CHANNELS_REFRESH_TIMEOUT_MS,
        "Истекло время ожидания списка Telegram-каналов.",
      );
      setAvailableChannels(nextChannels);
    } catch (error) {
      setAvailableChannels([]);
      throw error;
    } finally {
      if (!silent) {
        setLoadingAvailableChannels(false);
      }
    }
  }, [request, token, withClientTimeout]);

  const refreshSessionState = useCallback(async () => {
    if (!token) {
      return;
    }

    const updatedUser = await request<AuthUser>("/api/auth/me");
    setMe(updatedUser);
  }, [request, token]);

  const loadConversationData = useCallback(
    async (
      conversationId: number,
      options: { includeMembers?: boolean; markRead?: boolean } = {},
    ) => {
      const { includeMembers = true, markRead = true } = options;
      const requestSeq = ++conversationLoadSeqRef.current;
      const targetConversation = conversationsRef.current.find((conversation) => conversation.id === conversationId) ?? null;
      const shouldCacheMessages = targetConversation !== null;
      setLoadingConversationData(true);

      if (shouldCacheMessages) {
        const cachedMessages = readChannelMessageCache(token, conversationId);
        if (cachedMessages && requestSeq === conversationLoadSeqRef.current) {
          setMessages(cachedMessages);
        }
      }

      try {
        const requests: [Promise<ConversationMessage[]>, Promise<ConversationMember[]> | Promise<null>] = [
          request<ConversationMessage[]>(`/api/conversations/${conversationId}/messages`),
          includeMembers
            ? request<ConversationMember[]>(`/api/conversations/${conversationId}/members`)
            : Promise.resolve(null),
        ];

        const [nextMessages, nextMembers] = await Promise.all(requests);
        if (requestSeq !== conversationLoadSeqRef.current) {
          return;
        }
        setMessages(nextMessages);
        if (shouldCacheMessages) {
          writeChannelMessageCache(token, conversationId, nextMessages);
        }
        if (nextMembers) {
          setMembers(nextMembers);
          setReadByConversation((current) => ({
            ...current,
            [conversationId]: nextMembers.reduce<Record<number, { displayName: string; readAt: string }>>(
              (acc, member) => {
                if (member.lastReadMessageCreatedAt) {
                  acc[member.userId] = {
                    displayName: member.displayName,
                    readAt: member.lastReadMessageCreatedAt,
                  };
                }
                return acc;
              },
              {},
            ),
          }));
        }

        if (markRead && lastReadConversationIdRef.current !== conversationId) {
          await markConversationReadLocally(conversationId);
        }
      } catch (error) {
        if (
          error instanceof Error &&
          (error.message.includes("HTTP 404") || error.message.toLowerCase().includes("conversation") && error.message.toLowerCase().includes("not found"))
        ) {
          setMessages([]);
          setMembers([]);
          removeChannelMessageCache(token, conversationId);
          setConversations((current) => current.filter((conversation) => conversation.id !== conversationId));
          setSelectedConversationId((current) => (current === conversationId ? null : current));
        }
        throw error;
      } finally {
        if (requestSeq === conversationLoadSeqRef.current) {
          setLoadingConversationData(false);
        }
      }
    },
    [markConversationReadLocally, request, token],
  );

  const hydrateSession = useCallback(
    async (incomingToken: string, incomingUser?: AuthUser | null) => {
      setAuthLoading(true);
      setToken(incomingToken);
      writeStoredAccessToken(incomingToken);

      try {
        const user = incomingUser ?? (await apiRequest<AuthUser>(incomingToken, "/api/auth/me"));
        setMe(user);
        const nextConversations = await apiRequest<ConversationSummary[]>(incomingToken, "/api/conversations");
        setConversations(nextConversations);
        const rememberedConversationId = readLastConversationId(user);
        setSelectedConversationId(
          rememberedConversationId && nextConversations.some((conversation) => conversation.id === rememberedConversationId)
            ? rememberedConversationId
            : (nextConversations[0]?.id ?? null),
        );
      } catch (error) {
        clearSession();
        throw error;
      } finally {
        setAuthLoading(false);
      }
    },
    [clearSession],
  );

  useEffect(() => {
    const restoreSession = async () => {
      const savedToken = readStoredAccessToken();

      if (savedToken) {
        try {
          await hydrateSession(savedToken);
          return;
        } catch (error) {
          if (!(error instanceof Error) || !error.message.includes("HTTP 401")) {
            throw error;
          }
        }
      }

      const refreshed = await refreshSession();
      await hydrateSession(refreshed);
    };

    const withTimeout = async <T,>(promise: Promise<T>, timeoutMs: number) => {
      let timeoutId: number | null = null;
      try {
        return await Promise.race<T>([
          promise,
          new Promise<T>((_, reject) => {
            timeoutId = window.setTimeout(() => {
              reject(new Error("Истекло время ожидания запуска сессии."));
            }, timeoutMs);
          }),
        ]);
      } finally {
        if (timeoutId !== null) {
          window.clearTimeout(timeoutId);
        }
      }
    };

    withTimeout(restoreSession(), SESSION_BOOT_TIMEOUT_MS)
      .catch((error) => {
        clearSession();
        if (error instanceof Error && error.message.includes("HTTP 401")) {
          return;
        }
        pushToast(error instanceof Error ? error.message : "Не удалось открыть сессию.", "error");
      })
      .finally(() => {
        setBooting(false);
      });
  }, [clearSession, hydrateSession, pushToast, refreshSession]);

  useEffect(() => {
    void cleanupProtectedMediaCache(true);
  }, [token]);

  useEffect(() => {
    if (!token || !me) {
      return;
    }

    loadAvailableChannels({ silent: true }).catch(() => {});
  }, [loadAvailableChannels, me, token]);

  useEffect(() => {
    if (!selectedConversationId || !token) {
      setMessages([]);
      setMembers([]);
      lastReadConversationIdRef.current = null;
      return;
    }

    lastReadConversationIdRef.current = null;
    loadConversationData(selectedConversationId, { includeMembers: true, markRead: true }).catch((error) => {
      pushToast(error instanceof Error ? error.message : "Не удалось загрузить чат.", "error");
    });
  }, [loadConversationData, pushToast, selectedConversationId, token]);

  useEffect(() => {
    if (!selectedConversation || !token) {
      return;
    }

    if (selectedConversation.unreadCount <= 0) {
      return;
    }

    if (lastReadConversationIdRef.current === selectedConversation.id) {
      return;
    }

    if (typeof document !== "undefined" && document.visibilityState !== "visible") {
      return;
    }

    void markConversationReadLocally(selectedConversation.id).catch(() => {});
  }, [markConversationReadLocally, selectedConversation, token]);

  useEffect(() => {
    if (!token || !me || subscribedConversationIds.length === 0) {
      setWsConnected(false);
      return;
    }

    return subscribeToConversationMessages({
      token,
      userId: me.id,
      conversationIds: subscribedConversationIds,
      onConnectionChange: setWsConnected,
      onSessionReady: (session) => {
        wsSessionRef.current = session;
      },
      onEvent: (
        event: ConversationSocketEvent<
          ConversationMessage | ConversationReadPayload | ConversationSocketSummaryPayload | ConversationTypingPayload
        >,
      ) => {
        if (event.type === "conversation_summary") {
          const summary = event.payload as ConversationSocketSummaryPayload;
          setConversations((current) =>
            current.map((conversation) => (conversation.id === summary.id ? { ...conversation, ...summary } : conversation)),
          );
          return;
        }

        if (event.type === "typing_start" || event.type === "typing_stop") {
          updateTypingState(event.payload as ConversationTypingPayload);
          return;
        }

        if (event.type === "conversation_read") {
          upsertConversationReadState(event.payload as ConversationReadPayload);
          return;
        }

        if (event.type !== "new_message") {
          return;
        }

        const message = event.payload as ConversationMessage;
        const targetConversation = conversationsRef.current.find((conversation) => conversation.id === message.conversationId) ?? null;
        const isSelectedConversation = message.conversationId === selectedConversationId;
        const shouldAutoRead =
          isSelectedConversation && typeof document !== "undefined" && document.visibilityState === "visible";

        if (targetConversation) {
          upsertChannelMessageCache(token, message);
        }

        if (isSelectedConversation) {
          invalidatePendingConversationLoad();
          upsertConversationMessage(message);
        }

        if (shouldAutoRead) {
          void markConversationReadLocally(message.conversationId).catch(() => {});
        }
      },
    });
  }, [
    markConversationReadLocally,
    me,
    selectedConversationId,
    subscribedConversationIdsKey,
    invalidatePendingConversationLoad,
    token,
    upsertConversationReadState,
    updateTypingState,
    upsertConversationMessage,
  ]);

  useEffect(() => {
    if (!token || !me) {
      return;
    }

    const handle = window.setInterval(() => {
      if (typeof document !== "undefined" && document.visibilityState !== "visible") {
        return;
      }

      if (!wsConnected) {
        void loadConversations({ silent: true });
      }
      if (
        selectedConversationId &&
        selectedConversation &&
        !isChannelConversation(selectedConversation) &&
        !wsConnected
      ) {
        void loadConversationData(selectedConversationId, { includeMembers: false, markRead: false });
      }
    }, 15000);

    return () => window.clearInterval(handle);
  }, [loadConversationData, loadConversations, me, selectedConversation, selectedConversationId, token, wsConnected]);

  useEffect(() => {
    if (!selectedConversationId || !selectedConversation || !isChannelConversation(selectedConversation)) {
      return;
    }

    const summaryTimestamp = selectedConversation.lastMessageCreatedAt;
    const latestLoadedTimestamp = messages[messages.length - 1]?.createdAt ?? null;
    if (!summaryTimestamp || summaryTimestamp === latestLoadedTimestamp || loadingConversationData) {
      return;
    }

    void loadConversationData(selectedConversationId, { includeMembers: false, markRead: false }).catch(() => {});
  }, [
    loadConversationData,
    loadingConversationData,
    messages,
    selectedConversation,
    selectedConversationId,
  ]);

  useEffect(() => {
    if (!token || !me) {
      wasWsConnectedRef.current = false;
      return;
    }

    if (!wsConnected) {
      if (wasWsConnectedRef.current) {
        setTypingByConversation({});
      }
      wasWsConnectedRef.current = false;
      return;
    }

    const reconnected = wasWsConnectedRef.current === false;
    wasWsConnectedRef.current = true;

    if (!reconnected) {
      return;
    }

    void loadConversations({ silent: true });
    if (selectedConversationId) {
      void loadConversationData(selectedConversationId, { includeMembers: false, markRead: false });
    }
  }, [loadConversationData, loadConversations, me, selectedConversationId, token, wsConnected]);

  const login = useCallback(
    async (incomingToken: string) => {
      if (!incomingToken) {
        pushToast("Вставь токен от бота.", "error");
        return;
      }

      try {
        const session = await exchangeBootstrapToken<AuthUser>(incomingToken);
        await hydrateSession(session.accessToken, session.user);
      } catch (error) {
        pushToast(error instanceof Error ? error.message : "Не удалось войти.", "error");
      }
    },
    [hydrateSession, pushToast],
  );

  const loginWithPassword = useCallback(
    async (payload: { username: string; password: string }) => {
      if (!payload.username || !payload.password) {
        pushToast("Заполни username и пароль.", "error");
        return;
      }

      try {
        const session = await loginHermesAccount<AuthUser>(payload);
        await hydrateSession(session.accessToken, session.user);
      } catch (error) {
        pushToast(error instanceof Error ? error.message : "Не удалось войти в Hermes.", "error");
      }
    },
    [hydrateSession, pushToast],
  );

  const register = useCallback(
    async (payload: { username: string; displayName: string; password: string }) => {
      if (!payload.username || !payload.displayName || !payload.password) {
        pushToast("Заполни имя, username и пароль.", "error");
        return;
      }

      try {
        const session = await registerHermesAccount<AuthUser>(payload);
        await hydrateSession(session.accessToken, session.user);
      } catch (error) {
        pushToast(error instanceof Error ? error.message : "Не удалось создать Hermes account.", "error");
      }
    },
    [hydrateSession, pushToast],
  );

  const logout = useCallback(() => {
    void logoutSession().catch(() => {});
    clearSession();
    pushToast("Сессия закрыта.", "info");
  }, [clearSession, pushToast]);

  const createConversation = useCallback(
    async (title: string) => {
      const created = await request<ConversationSummary>("/api/conversations", {
        method: "POST",
        body: JSON.stringify({ title }),
      });
      pushToast(`Чат "${created.title}" создан.`, "success");
      await loadConversations();
      setSelectedConversationId(created.id);
      setDrawerMode(null);
    },
    [loadConversations, pushToast, request],
  );

  const joinConversation = useCallback(
    async (inviteCode: string) => {
      const joined = await request<InviteAcceptance>(`/api/invites/${encodeURIComponent(inviteCode)}/accept`, {
        method: "POST",
      });
      pushToast(`Вход выполнен: "${joined.conversationTitle}".`, "success");
      await loadConversations();
      setSelectedConversationId(joined.conversationId);
      setDrawerMode(null);
    },
    [loadConversations, pushToast, request],
  );

  const updateProfile = useCallback(
    async (payload: { displayName: string; username: string; avatar?: File | null; removeAvatar?: boolean }) => {
      const formData = new FormData();
      formData.append("displayName", payload.displayName);
      formData.append("username", payload.username);
      if (payload.avatar) {
        formData.append("avatar", payload.avatar);
      }
      if (payload.removeAvatar) {
        formData.append("removeAvatar", "true");
      }

      const updated = await request<AuthUser>("/api/auth/me/profile", {
        method: "PATCH",
        body: formData,
      });
      setMe(updated);
      setDrawerMode(null);
      pushToast("Профиль обновлён.", "success");
    },
    [pushToast, request],
  );

  const createTelegramLink = useCallback(async () => {
    const response = await request<{ code: string; expiresAt: string }>("/api/auth/link/telegram/start", {
      method: "POST",
    });
    pushToast("Код привязки Telegram создан и скопирован, если браузер разрешил clipboard.", "success");
    return response;
  }, [pushToast, request]);

  const completeRegistration = useCallback(
    async (payload: { username: string; displayName: string; password: string }) => {
      if (!token) {
        pushToast("Сессия не найдена. Войди заново.", "error");
        return;
      }
      if (!payload.username || !payload.displayName || !payload.password) {
        pushToast("Заполни имя, username и пароль.", "error");
        return;
      }

      try {
        setAuthLoading(true);
        const updatedUser = await completeHermesRegistration<AuthUser>(token, payload);
        setMe(updatedUser);
        pushToast("Hermes login настроен. Теперь можно входить по username и паролю.", "success");
      } catch (error) {
        pushToast(error instanceof Error ? error.message : "Не удалось завершить регистрацию.", "error");
      } finally {
        setAuthLoading(false);
      }
    },
    [pushToast, token],
  );

  const updateConversation = useCallback(
    async (
      conversationId: number,
      payload: { title: string; avatar?: File | null; removeAvatar?: boolean; muted?: boolean },
    ) => {
      if (typeof payload.muted === "boolean") {
        const updated = await request<ConversationSummary>(`/api/conversations/${conversationId}/preferences`, {
          method: "PATCH",
          body: JSON.stringify({ muted: payload.muted }),
        });
        setConversations((current) =>
          current.map((conversation) => (conversation.id === updated.id ? { ...conversation, ...updated } : conversation)),
        );
      }

      const formData = new FormData();
      formData.append("title", payload.title);
      if (payload.avatar) {
        formData.append("avatar", payload.avatar);
      }
      if (payload.removeAvatar) {
        formData.append("removeAvatar", "true");
      }

      const updatedConversation = await request<ConversationSummary>(`/api/conversations/${conversationId}/profile`, {
        method: "PATCH",
        body: formData,
      });

      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === updatedConversation.id ? { ...conversation, ...updatedConversation } : conversation,
        ),
      );
      setDrawerMode(null);
      pushToast("Чат обновлён.", "success");
    },
    [pushToast, request],
  );

  const deleteConversation = useCallback(
    async (conversationId: number) => {
      const targetConversation = conversations.find((conversation) => conversation.id === conversationId) ?? null;
      if (!targetConversation) {
        return;
      }

      if (isChannelConversation(targetConversation)) {
        await request(`/api/tdlight/subscriptions/conversation/${conversationId}`, { method: "DELETE" });
        removeChannelMessageCache(token, conversationId);
        pushToast("Канал отключён. Его можно подключить заново.", "success");
      } else {
        await request(`/api/conversations/${conversationId}`, { method: "DELETE" });
        removeChannelMessageCache(token, conversationId);
        pushToast("Чат удалён.", "success");
      }
      setSelectedConversationId((current) => (current === conversationId ? null : current));
      setMessages([]);
      setMembers([]);
      setDrawerMode(null);
      if (isChannelConversation(targetConversation)) {
        await loadAvailableChannels({ silent: true });
      }
      await loadConversations();
    },
    [conversations, loadAvailableChannels, loadConversations, pushToast, request, token],
  );

  const createInvite = useCallback(async () => {
    if (!selectedConversationId) {
      return;
    }

    const invite = await request<{ inviteCode: string }>(`/api/conversations/${selectedConversationId}/invites`, {
      method: "POST",
    });
    try {
      await navigator.clipboard.writeText(invite.inviteCode);
    } catch {
      // best effort
    }
    pushToast(`Инвайт скопирован: ${invite.inviteCode}`, "success");
  }, [pushToast, request, selectedConversationId]);

  const sendMessage = useCallback(
    async ({
      body,
      attachments,
      replyToMessageId,
      sendAsVideoNote,
      onUploadProgress,
    }: {
      body: string;
      attachments: PendingAttachment[];
      replyToMessageId: number | null;
      sendAsVideoNote: boolean;
      onUploadProgress?: (state: UploadProgressState) => void;
    }) => {
      if (!selectedConversationId) {
        return;
      }

      if (attachments.length > 0) {
        const formData = new FormData();
        if (body.trim()) {
          formData.append("body", body.trim());
        }
        if (replyToMessageId) {
          formData.append("replyToMessageId", String(replyToMessageId));
        }
        if (sendAsVideoNote) {
          formData.append("sendAsVideoNote", "true");
        }
        if (attachments.length === 1 && attachments[0]?.source === "voice") {
          formData.append("sendAsVoice", "true");
        }
        attachments.forEach((attachment) => formData.append("files", attachment.file));
        const createdMessage = await request<ConversationMessage>(`/api/conversations/${selectedConversationId}/messages/upload`, {
          method: "POST",
          body: formData,
          onUploadProgress,
        });
        invalidatePendingConversationLoad();
        upsertConversationMessage(createdMessage);
        applyConversationMessageSummary(createdMessage, { unreadCount: 0 });
      } else {
        const createdMessage = await request<ConversationMessage>(`/api/conversations/${selectedConversationId}/messages`, {
          method: "POST",
          body: JSON.stringify({
            body: body.trim(),
            replyToMessageId: replyToMessageId ?? undefined,
          }),
        });
        invalidatePendingConversationLoad();
        upsertConversationMessage(createdMessage);
        applyConversationMessageSummary(createdMessage, { unreadCount: 0 });
      }
    },
    [applyConversationMessageSummary, invalidatePendingConversationLoad, request, selectedConversationId, upsertConversationMessage],
  );

  const refreshCurrentConversation = useCallback(async () => {
    if (!selectedConversationId) {
      return;
    }

    await loadConversations();
    await loadConversationData(selectedConversationId);
  }, [loadConversationData, loadConversations, selectedConversationId]);

  const refreshSidebarData = useCallback(async () => {
    await loadConversations();
    await loadAvailableChannels({ silent: true });
  }, [loadAvailableChannels, loadConversations]);

  const subscribeChannels = useCallback(
    async (channels: TdlightAvailableChannel[]) => {
      if (!tdlightConnectionId) {
        pushToast("Сначала подключите Telegram через QR.", "error");
        return;
      }

      const uniqueChannels = channels.filter(
        (channel, index, list) =>
          list.findIndex((candidate) => candidate.telegramChannelId === channel.telegramChannelId) === index,
      );
      if (uniqueChannels.length === 0) {
        return;
      }

      let lastConversationId: number | null = null;
      for (const channel of uniqueChannels) {
        const subscription = await withClientTimeout(
          request<{ conversationId: number }>(
            "/api/tdlight/subscriptions",
            {
              method: "POST",
              body: JSON.stringify({
                tdlightConnectionId,
                telegramChannelId: channel.telegramChannelId,
                telegramChannelHandle: channel.telegramChannelHandle,
                channelTitle: channel.channelTitle,
                avatarUrl: channel.avatarUrl,
              }),
            },
          ),
          CHANNEL_SUBSCRIBE_TIMEOUT_MS,
          `Истекло время ожидания подключения канала «${channel.channelTitle}».`,
        );
        setAvailableChannels((current) =>
          current.filter((candidate) => candidate.telegramChannelId !== channel.telegramChannelId),
        );
        lastConversationId = subscription.conversationId;
      }

      await loadAvailableChannels({ silent: true });
      await loadConversations({ silent: true });
      if (lastConversationId) {
        setSelectedConversationId(lastConversationId);
        await loadConversationData(lastConversationId, { includeMembers: true, markRead: false });
      }

      pushToast(
        uniqueChannels.length === 1
          ? `Канал «${uniqueChannels[0]?.channelTitle ?? "Telegram"}» подключён.`
          : `Подключено каналов: ${uniqueChannels.length}.`,
        "success",
      );
    },
    [loadAvailableChannels, loadConversationData, loadConversations, pushToast, request, tdlightConnectionId, withClientTimeout],
  );

  const subscribeChannel = useCallback(
    async (channel: TdlightAvailableChannel) => {
      await subscribeChannels([channel]);
    },
    [subscribeChannels],
  );

  if (booting) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "grid",
          placeItems: "center",
          background: "linear-gradient(180deg, rgba(17, 19, 25, 0.98), rgba(10, 12, 18, 0.98))",
          color: "rgba(237, 241, 247, 0.92)",
          fontFamily: "var(--font-display)",
          letterSpacing: "0.04em",
        }}
      >
        <div style={{ display: "grid", gap: "10px", justifyItems: "center" }}>
          <strong>Hermes</strong>
          <span style={{ color: "rgba(237, 241, 247, 0.64)", fontSize: "0.95rem" }}>
            Загружаем сессию...
          </span>
        </div>
      </div>
    );
  }

  const needsHermesCompletion = Boolean(me && me.telegramLinked && !me.passwordLinked);

  return (
    <>
      {me && needsHermesCompletion ? (
        <CompleteRegistrationScreen
          me={me}
          submitting={authLoading}
          onSubmit={completeRegistration}
          onLogout={logout}
        />
      ) : me ? (
        <AppShell
          token={token}
          me={me}
          conversations={conversations}
          selectedConversation={selectedConversation}
          drawerMode={drawerMode}
          drawerConversation={drawerConversation}
          messages={messages}
          members={members}
          readReceipts={
            selectedConversationId
              ? Object.entries(readByConversation[selectedConversationId] ?? {}).map(([userId, value]) => ({
                  userId: Number(userId),
                  displayName: value.displayName,
                  readAt: value.readAt,
                  conversationId: selectedConversationId,
                }))
              : []
          }
          typingNames={selectedTypingNames}
          loadingConversations={loadingConversations}
          loadingConversationData={loadingConversationData}
          availableChannels={availableChannels}
          loadingAvailableChannels={loadingAvailableChannels}
          onLogout={logout}
          onRefreshConversations={refreshSidebarData}
          onRefreshAvailableChannels={loadAvailableChannels}
          onSubscribeChannel={subscribeChannel}
          onSubscribeChannels={subscribeChannels}
          onSelectConversation={setSelectedConversationId}
          onOpenDrawer={(mode, conversationId) => {
            setDrawerMode(mode);
            setDrawerConversationId(conversationId ?? selectedConversationId);
          }}
          onCloseDrawer={() => setDrawerMode(null)}
          onCreateConversation={createConversation}
          onJoinConversation={joinConversation}
          onUpdateProfile={updateProfile}
          onRefreshSessionState={refreshSessionState}
          onUpdateConversation={updateConversation}
          onDeleteConversation={deleteConversation}
          onCreateInvite={createInvite}
          onRefreshCurrentConversation={refreshCurrentConversation}
          onSendMessage={sendMessage}
          onReply={(messageId) => {
            const target = messages.find((message) => message.id === messageId);
            if (!target) {
              return null;
            }
            return target;
          }}
          onTypingStateChange={sendTypingState}
          onOpenPhotoViewer={(items, activeIndex) => setPhotoViewer({ items, activeIndex })}
        />
      ) : (
        <AuthScreen
          initialToken={token}
          submitting={authLoading}
          onTokenSubmit={login}
          onPasswordLogin={loginWithPassword}
          onRegister={register}
        />
      )}

      <ToastViewport toasts={toasts} />
      <PhotoViewerModal
        open={photoViewer.items.length > 0}
        viewer={photoViewer}
        onClose={() => setPhotoViewer(EMPTY_PHOTO_VIEWER)}
        onStep={(direction) =>
          setPhotoViewer((current) => ({
            ...current,
            activeIndex: (current.activeIndex + direction + current.items.length) % current.items.length,
          }))
        }
      />
    </>
  );
}
