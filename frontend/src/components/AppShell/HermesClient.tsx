"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AuthScreen } from "@/components/AuthScreen/AuthScreen";
import { AppShell } from "@/components/AppShell/AppShell";
import { ToastViewport } from "@/components/ToastViewport/ToastViewport";
import { PhotoViewerModal } from "@/components/PhotoViewerModal/PhotoViewerModal";
import { apiRequest, readStoredToken, writeStoredToken } from "@/lib/api";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationSummary,
  DrawerMode,
  InviteAcceptance,
  PendingAttachment,
  PhotoViewerState,
  ToastMessage,
} from "@/types/api";

const EMPTY_PHOTO_VIEWER: PhotoViewerState = { items: [], activeIndex: 0 };

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
  const [drawerMode, setDrawerMode] = useState<DrawerMode>(null);
  const [drawerConversationId, setDrawerConversationId] = useState<number | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [photoViewer, setPhotoViewer] = useState<PhotoViewerState>(EMPTY_PHOTO_VIEWER);
  const toastIdRef = useRef(1);

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedConversationId) ?? null,
    [conversations, selectedConversationId],
  );

  const drawerConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === drawerConversationId) ?? null,
    [conversations, drawerConversationId],
  );

  const pushToast = useCallback((message: string, kind: ToastMessage["kind"]) => {
    const id = toastIdRef.current++;
    setToasts((current) => [...current, { id, kind, message }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((item) => item.id !== id));
    }, 4200);
  }, []);

  const handleUnauthorized = useCallback(() => {
    writeStoredToken("");
    setToken("");
    setMe(null);
    setConversations([]);
    setSelectedConversationId(null);
    setMessages([]);
    setMembers([]);
    setDrawerMode(null);
    setDrawerConversationId(null);
  }, []);

  const request = useCallback(
    async <T,>(path: string, options: RequestInit = {}) => {
      try {
        return await apiRequest<T>(token, path, options);
      } catch (error) {
        if (error instanceof Error && error.message.includes("HTTP 401")) {
          handleUnauthorized();
        }
        throw error;
      }
    },
    [handleUnauthorized, token],
  );

  const loadConversations = useCallback(async () => {
    if (!token) {
      return;
    }

    setLoadingConversations(true);
    try {
      const nextConversations = await request<ConversationSummary[]>("/api/conversations");
      setConversations(nextConversations);

      setSelectedConversationId((current) => {
        if (current && nextConversations.some((conversation) => conversation.id === current)) {
          return current;
        }
        return nextConversations[0]?.id ?? null;
      });

      setDrawerConversationId((current) =>
        current && nextConversations.some((conversation) => conversation.id === current) ? current : null,
      );
    } finally {
      setLoadingConversations(false);
    }
  }, [request, token]);

  const loadConversationData = useCallback(
    async (conversationId: number) => {
      setLoadingConversationData(true);
      try {
        const [nextMessages, nextMembers] = await Promise.all([
          request<ConversationMessage[]>(`/api/conversations/${conversationId}/messages`),
          request<ConversationMember[]>(`/api/conversations/${conversationId}/members`),
        ]);
        setMessages(nextMessages);
        setMembers(nextMembers);
        await request(`/api/conversations/${conversationId}/read`, {
          method: "POST",
          body: JSON.stringify({}),
        });
        setConversations((current) =>
          current.map((conversation) =>
            conversation.id === conversationId ? { ...conversation, unreadCount: 0 } : conversation,
          ),
        );
      } finally {
        setLoadingConversationData(false);
      }
    },
    [request],
  );

  const bootstrap = useCallback(
    async (incomingToken: string) => {
      setAuthLoading(true);
      setToken(incomingToken);
      writeStoredToken(incomingToken);

      try {
        const user = await apiRequest<AuthUser>(incomingToken, "/api/auth/me");
        setMe(user);
        const nextConversations = await apiRequest<ConversationSummary[]>(incomingToken, "/api/conversations");
        setConversations(nextConversations);
        setSelectedConversationId(nextConversations[0]?.id ?? null);
      } catch (error) {
        writeStoredToken("");
        setToken("");
        setMe(null);
        throw error;
      } finally {
        setAuthLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    const savedToken = readStoredToken();
    if (!savedToken) {
      setBooting(false);
      return;
    }

    bootstrap(savedToken)
      .catch((error) => {
        pushToast(error instanceof Error ? error.message : "Не удалось открыть сессию.", "error");
      })
      .finally(() => {
        setBooting(false);
      });
  }, [bootstrap, pushToast]);

  useEffect(() => {
    if (!selectedConversationId || !token) {
      setMessages([]);
      setMembers([]);
      return;
    }

    loadConversationData(selectedConversationId).catch((error) => {
      pushToast(error instanceof Error ? error.message : "Не удалось загрузить чат.", "error");
    });
  }, [loadConversationData, pushToast, selectedConversationId, token]);

  useEffect(() => {
    if (!token || !me) {
      return;
    }

    const handle = window.setInterval(() => {
      void loadConversations();
      if (selectedConversationId) {
        void loadConversationData(selectedConversationId);
      }
    }, 4000);

    return () => window.clearInterval(handle);
  }, [loadConversationData, loadConversations, me, selectedConversationId, token]);

  const login = useCallback(
    async (incomingToken: string) => {
      if (!incomingToken) {
        pushToast("Вставь токен от бота.", "error");
        return;
      }

      try {
        await bootstrap(incomingToken);
      } catch (error) {
        pushToast(error instanceof Error ? error.message : "Не удалось войти.", "error");
      }
    },
    [bootstrap, pushToast],
  );

  const logout = useCallback(() => {
    handleUnauthorized();
    pushToast("Сессия закрыта.", "info");
  }, [handleUnauthorized, pushToast]);

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
      await request(`/api/conversations/${conversationId}`, { method: "DELETE" });
      setSelectedConversationId((current) => (current === conversationId ? null : current));
      setMessages([]);
      setMembers([]);
      setDrawerMode(null);
      await loadConversations();
      pushToast("Чат удалён.", "success");
    },
    [loadConversations, pushToast, request],
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
    }: {
      body: string;
      attachments: PendingAttachment[];
      replyToMessageId: number | null;
      sendAsVideoNote: boolean;
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
        attachments.forEach((attachment) => formData.append("files", attachment.file));
        await request(`/api/conversations/${selectedConversationId}/messages/upload`, {
          method: "POST",
          body: formData,
        });
      } else {
        await request(`/api/conversations/${selectedConversationId}/messages`, {
          method: "POST",
          body: JSON.stringify({
            body: body.trim(),
            replyToMessageId: replyToMessageId ?? undefined,
          }),
        });
      }

      await loadConversationData(selectedConversationId);
      await loadConversations();
    },
    [loadConversationData, loadConversations, request, selectedConversationId],
  );

  const refreshCurrentConversation = useCallback(async () => {
    if (!selectedConversationId) {
      return;
    }

    await loadConversations();
    await loadConversationData(selectedConversationId);
  }, [loadConversationData, loadConversations, selectedConversationId]);

  if (booting) {
    return null;
  }

  return (
    <>
      {me ? (
        <AppShell
          token={token}
          me={me}
          conversations={conversations}
          selectedConversation={selectedConversation}
          drawerMode={drawerMode}
          drawerConversation={drawerConversation}
          messages={messages}
          members={members}
          loadingConversations={loadingConversations}
          loadingConversationData={loadingConversationData}
          onLogout={logout}
          onRefreshConversations={loadConversations}
          onSelectConversation={setSelectedConversationId}
          onOpenDrawer={(mode, conversationId) => {
            setDrawerMode(mode);
            setDrawerConversationId(conversationId ?? selectedConversationId);
          }}
          onCloseDrawer={() => setDrawerMode(null)}
          onCreateConversation={createConversation}
          onJoinConversation={joinConversation}
          onUpdateProfile={updateProfile}
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
          onOpenPhotoViewer={(items, activeIndex) => setPhotoViewer({ items, activeIndex })}
        />
      ) : (
        <AuthScreen initialToken={token} submitting={authLoading} onSubmit={login} />
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
