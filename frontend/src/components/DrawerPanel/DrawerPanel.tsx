"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import styles from "./DrawerPanel.module.scss";
import { Avatar } from "@/components/ui/Avatar";
import { apiRequest, getApiBaseUrl } from "@/lib/api";
import { clearChannelMessageCache, inspectChannelMessageCacheStats } from "@/lib/channelMessageCache";
import { displayConversationTitle, formatBytes, isTelegramConversationTitle } from "@/lib/format";
import {
  clearProtectedMediaCache,
  getProtectedMediaCacheSettings,
  inspectProtectedMediaCacheStats,
  updateProtectedMediaCacheSettings,
} from "@/lib/protectedMediaCache";
import type { AuthUser, ConversationSummary, DrawerMode, TdlightAvailableChannel } from "@/types/api";

interface DrawerPanelProps {
  token: string;
  open: boolean;
  mode: DrawerMode;
  me: AuthUser;
  conversation: ConversationSummary | null;
  onClose: () => void;
  onCreateConversation: (title: string) => Promise<void>;
  onJoinConversation: (inviteCode: string) => Promise<void>;
  onUpdateProfile: (payload: {
    displayName: string;
    username: string;
    avatar?: File | null;
    removeAvatar?: boolean;
  }) => Promise<void>;
  onRefreshSessionState: () => Promise<void>;
  availableChannels: TdlightAvailableChannel[];
  loadingAvailableChannels: boolean;
  onRefreshAvailableChannels: () => Promise<void>;
  onSubscribeChannel: (channel: TdlightAvailableChannel) => Promise<void>;
  onSubscribeChannels: (channels: TdlightAvailableChannel[]) => Promise<void>;
  onUpdateConversation: (
    conversationId: number,
    payload: { title: string; avatar?: File | null; removeAvatar?: boolean; muted?: boolean },
  ) => Promise<void>;
  onDeleteConversation: (conversationId: number) => Promise<void>;
  inline?: boolean;
}

function pickPreferredTdlightConnection(
  connections: Array<{
    id: number;
    tdlightUserId: string | null;
    createdAt: string;
    lastVerifiedAt: string | null;
  }>,
) {
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

export function DrawerPanel({
  token,
  open,
  mode,
  me,
  conversation,
  onClose,
  onCreateConversation,
  onJoinConversation,
  onUpdateProfile,
  onRefreshSessionState,
  availableChannels,
  loadingAvailableChannels,
  onRefreshAvailableChannels,
  onSubscribeChannel,
  onSubscribeChannels,
  onUpdateConversation,
  onDeleteConversation,
  inline = false,
}: DrawerPanelProps) {
  type TdlightDevConnectionResponse = { id: number };
  type TdlightConnectionResponse = {
    id: number;
    userAccountId: number;
    status: string;
    phoneMask: string | null;
    tdlightUserId: string | null;
    createdAt: string;
    lastVerifiedAt: string | null;
  };
  type TdlightQrAuthorizationStatus = {
    tdlightConnectionId: number;
    phase: string;
    qrLink: string | null;
    phoneNumber: string | null;
    lastError: string | null;
    updatedAt: string | null;
  };

  const apiBaseUrl = getApiBaseUrl();
  const origin = typeof window === "undefined" ? "" : window.location.origin;
  const [createTitle, setCreateTitle] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [profileName, setProfileName] = useState(me.displayName);
  const [profileUsername, setProfileUsername] = useState(me.username);
  const [profileAvatar, setProfileAvatar] = useState<File | null>(null);
  const [removeProfileAvatar, setRemoveProfileAvatar] = useState(false);
  const [cacheTtlHours, setCacheTtlHours] = useState(getProtectedMediaCacheSettings().ttlHours);
  const [cacheVersion, setCacheVersion] = useState(0);
  const [cacheBusy, setCacheBusy] = useState(false);
  const [qrBusy, setQrBusy] = useState(false);
  const [qrConnectionId, setQrConnectionId] = useState<number | null>(null);
  const [qrPhase, setQrPhase] = useState("NOT_STARTED");
  const [qrLink, setQrLink] = useState("");
  const [qrLastError, setQrLastError] = useState("");
  const [qrUpdatedAt, setQrUpdatedAt] = useState<string | null>(null);
  const [qrTelegramLinked, setQrTelegramLinked] = useState(me.telegramLinked);
  const [qrPassword, setQrPassword] = useState("");
  const [conversationTitle, setConversationTitle] = useState(conversation?.title ?? "");
  const [conversationAvatar, setConversationAvatar] = useState<File | null>(null);
  const [conversationAvatarPreview, setConversationAvatarPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedChannelIds, setSelectedChannelIds] = useState<string[]>([]);
  const [subscribingChannelIds, setSubscribingChannelIds] = useState<string[]>([]);
  const [batchSubscribeBusy, setBatchSubscribeBusy] = useState(false);
  const unsubscribedChannels = useMemo(
    () => availableChannels.filter((channel) => !channel.subscribed),
    [availableChannels],
  );
  const selectedChannels = useMemo(
    () => unsubscribedChannels.filter((channel) => selectedChannelIds.includes(channel.telegramChannelId)),
    [selectedChannelIds, unsubscribedChannels],
  );
  const cacheStats = useMemo(() => inspectProtectedMediaCacheStats(), [cacheVersion]);
  const channelMessageCacheStats = useMemo(() => inspectChannelMessageCacheStats(token), [cacheVersion, token]);
  const qrImageUrl = qrLink
    ? `${apiBaseUrl || origin}/local/tdlight/qr/image?link=${encodeURIComponent(qrLink)}`
    : "";

  useEffect(() => {
    setProfileName(me.displayName);
    setProfileUsername(me.username);
    setConversationTitle(conversation?.title ?? "");
    setConversationAvatar(null);
    setCacheTtlHours(getProtectedMediaCacheSettings().ttlHours);
    setQrTelegramLinked(me.telegramLinked);
    setQrPassword("");
    setErrorMessage("");
    setSelectedChannelIds([]);
    setSubscribingChannelIds([]);
    setBatchSubscribeBusy(false);
  }, [conversation?.title, me.displayName, me.telegramLinked, me.username, mode, open]);

  useEffect(() => {
    if (!open || mode !== "profile") {
      return;
    }

    setCacheVersion((current) => current + 1);
  }, [mode, open]);

  useEffect(() => {
    setSelectedChannelIds((current) =>
      current.filter((channelId) => unsubscribedChannels.some((channel) => channel.telegramChannelId === channelId)),
    );
  }, [unsubscribedChannels]);

  useEffect(() => {
    if (!conversationAvatar) {
      setConversationAvatarPreview(null);
      return;
    }

    const objectUrl = URL.createObjectURL(conversationAvatar);
    setConversationAvatarPreview(objectUrl);

    return () => URL.revokeObjectURL(objectUrl);
  }, [conversationAvatar]);

  useEffect(() => {
    if (
      !open ||
      mode !== "profile" ||
      !token ||
      !qrConnectionId ||
      qrPhase === "READY" ||
      qrPhase === "NOT_STARTED" ||
      qrPhase === "FAILED"
    ) {
      return;
    }

    let cancelled = false;
    const handle = window.setInterval(async () => {
      try {
        const status = await apiRequest<TdlightQrAuthorizationStatus>(
          token,
          `${apiBaseUrl}/api/tdlight/auth/${qrConnectionId}/status`,
        );
        if (cancelled) {
          return;
        }
        setQrPhase(status.phase);
        setQrLink(status.phase === "READY" ? "" : (status.qrLink ?? ""));
        setQrLastError(status.lastError ?? "");
        setQrUpdatedAt(status.updatedAt ?? null);
        if (status.phase === "READY") {
          await onRefreshSessionState();
          const refreshedMe = await apiRequest<AuthUser>(token, `${apiBaseUrl}/api/auth/me`);
          if (!cancelled) {
            setQrTelegramLinked(refreshedMe.telegramLinked);
            setQrPassword("");
            setQrLink("");
          }
          await onRefreshAvailableChannels();
        } else if (status.phase === "WAIT_PASSWORD" || status.phase === "WAIT_QR_SCAN" || status.phase === "WAIT_CODE") {
          setQrTelegramLinked(false);
        }
      } catch (error) {
        if (cancelled) {
          return;
        }
        setQrLastError(error instanceof Error ? error.message : "Не удалось обновить QR-статус.");
      }
    }, 2000);

    return () => {
      cancelled = true;
      window.clearInterval(handle);
    };
  }, [apiBaseUrl, mode, onRefreshAvailableChannels, onRefreshSessionState, open, qrConnectionId, qrPhase, token]);

  useEffect(() => {
    if (!open || mode !== "profile" || !token) {
      return;
    }

    let cancelled = false;

    const loadExistingConnection = async () => {
      try {
        const connections = await apiRequest<TdlightConnectionResponse[]>(
          token,
          `${apiBaseUrl}/api/tdlight/connections`,
        );
        if (cancelled) {
          return;
        }

        const primaryConnection = pickPreferredTdlightConnection(connections);
        const hasAuthorizedConnection = Boolean(primaryConnection?.lastVerifiedAt && primaryConnection?.tdlightUserId);
        setQrConnectionId(primaryConnection?.id ?? null);

        if (!primaryConnection) {
          setQrPhase("NOT_STARTED");
          setQrLink("");
          setQrLastError("");
          setQrUpdatedAt(null);
          return;
        }

        const status = await apiRequest<TdlightQrAuthorizationStatus>(
          token,
          `${apiBaseUrl}/api/tdlight/auth/${primaryConnection.id}/status`,
        );
        if (cancelled) {
          return;
        }
        const normalizedPhase = status.phase === "NOT_STARTED" && hasAuthorizedConnection ? "READY" : status.phase;
        setQrPhase(normalizedPhase);
        setQrLink(normalizedPhase === "READY" ? "" : (status.qrLink ?? ""));
        setQrLastError(status.lastError ?? "");
        setQrUpdatedAt(status.updatedAt ?? primaryConnection.lastVerifiedAt ?? null);
        if (normalizedPhase === "READY") {
          await onRefreshSessionState();
          const refreshedMe = await apiRequest<AuthUser>(token, `${apiBaseUrl}/api/auth/me`);
          if (cancelled) {
            return;
          }
          setQrTelegramLinked(refreshedMe.telegramLinked || hasAuthorizedConnection);
          await onRefreshAvailableChannels();
        } else {
          setQrTelegramLinked(false);
        }
      } catch (error) {
        if (cancelled) {
          return;
        }
        setQrLastError(error instanceof Error ? error.message : "Не удалось получить Telegram-сессию.");
      }
    };

    void loadExistingConnection();

    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, mode, onRefreshAvailableChannels, onRefreshSessionState, open, token]);

  if (!open || !mode) {
    return null;
  }

  const canManageConversation =
    conversation?.membershipRole === "OWNER" || conversation?.membershipRole === "ADMIN";
  const isChannelConversation = isTelegramConversationTitle(conversation?.title);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!createTitle.trim()) {
      return;
    }
    setBusy(true);
    try {
      setErrorMessage("");
      await onCreateConversation(createTitle.trim());
      setCreateTitle("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось создать чат.");
    } finally {
      setBusy(false);
    }
  }

  async function handleJoin(event: FormEvent) {
    event.preventDefault();
    if (!inviteCode.trim()) {
      return;
    }
    setBusy(true);
    try {
      setErrorMessage("");
      await onJoinConversation(inviteCode.trim());
      setInviteCode("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось войти по коду.");
    } finally {
      setBusy(false);
    }
  }

  async function handleProfile(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateProfile({
        displayName: profileName.trim(),
        username: profileUsername.trim(),
        avatar: profileAvatar,
        removeAvatar: removeProfileAvatar,
      });
      setProfileAvatar(null);
      setRemoveProfileAvatar(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить профиль.");
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveCacheSettings() {
    setCacheBusy(true);
    try {
      setErrorMessage("");
      updateProtectedMediaCacheSettings({ ttlHours: cacheTtlHours });
      await clearProtectedMediaCache();
      setCacheVersion((current) => current + 1);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось сохранить настройки кэша.");
    } finally {
      setCacheBusy(false);
    }
  }

  async function handleClearCache() {
    setCacheBusy(true);
    try {
      setErrorMessage("");
      await clearProtectedMediaCache();
      clearChannelMessageCache(token);
      setCacheVersion((current) => current + 1);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось очистить кэш.");
    } finally {
      setCacheBusy(false);
    }
  }

  async function handleStartQrLink() {
    setQrBusy(true);
    try {
      setErrorMessage("");
      setQrTelegramLinked(false);
      setQrPhase("STARTING");
      setQrLink("");
      setQrLastError("");
      setQrUpdatedAt(null);
      setQrPassword("");
      const connection = await apiRequest<TdlightDevConnectionResponse>(
        token,
        `${apiBaseUrl}/api/tdlight/connections/dev`,
        {
          method: "POST",
          body: JSON.stringify({
            tdlightUserId: `profile-qr-${Date.now()}`,
            forceNew: true,
          }),
        },
      );
      setQrConnectionId(connection.id);

      const status = await apiRequest<TdlightQrAuthorizationStatus>(
        token,
        `${apiBaseUrl}/api/tdlight/auth/qr/start`,
        {
          method: "POST",
          body: JSON.stringify({ tdlightConnectionId: connection.id }),
        },
      );
      setQrPhase(status.phase);
      setQrLink(status.phase === "READY" ? "" : (status.qrLink ?? ""));
      setQrLastError(status.lastError ?? "");
      setQrUpdatedAt(status.updatedAt ?? null);
      if (status.phase === "READY") {
        await onRefreshSessionState();
        const refreshedMe = await apiRequest<AuthUser>(token, `${apiBaseUrl}/api/auth/me`);
        setQrPassword("");
        setQrTelegramLinked(refreshedMe.telegramLinked);
        await onRefreshAvailableChannels();
      }
    } catch (error) {
      setQrLastError(error instanceof Error ? error.message : "Не удалось запустить Telegram QR.");
    } finally {
      setQrBusy(false);
    }
  }

  async function handleResetQrLink() {
    let tdlightConnectionId = qrConnectionId;

    if (!tdlightConnectionId) {
      try {
        const connections = await apiRequest<TdlightConnectionResponse[]>(
          token,
          `${apiBaseUrl}/api/tdlight/connections`,
        );
        tdlightConnectionId = pickPreferredTdlightConnection(connections)?.id ?? null;
        setQrConnectionId(tdlightConnectionId);
      } catch (error) {
        setQrLastError(error instanceof Error ? error.message : "Не удалось найти Telegram-сессию.");
        return;
      }
    }

    if (!tdlightConnectionId) {
      setQrPhase("NOT_STARTED");
      setQrLink("");
      setQrLastError("");
      setQrUpdatedAt(null);
      setQrTelegramLinked(false);
      return;
    }

    setQrBusy(true);
    try {
      setErrorMessage("");
      const status = await apiRequest<TdlightQrAuthorizationStatus>(
        token,
        `${apiBaseUrl}/api/tdlight/auth/reset`,
        {
          method: "POST",
          body: JSON.stringify({ tdlightConnectionId }),
        },
      );
      setQrPhase(status.phase);
      setQrLink("");
      setQrLastError(status.lastError ?? "");
      setQrUpdatedAt(status.updatedAt ?? null);
      setQrTelegramLinked(false);
      setQrPassword("");
      setQrConnectionId(null);
      await onRefreshSessionState();
      await onRefreshAvailableChannels();
    } catch (error) {
      setQrLastError(error instanceof Error ? error.message : "Не удалось сбросить Telegram-сессию.");
    } finally {
      setQrBusy(false);
    }
  }

  async function handleSubmitQrPassword() {
    if (!qrConnectionId || !qrPassword.trim()) {
      return;
    }

    setQrBusy(true);
    try {
      setQrLastError("");
      const status = await apiRequest<TdlightQrAuthorizationStatus>(
        token,
        `${apiBaseUrl}/api/tdlight/auth/password/submit`,
        {
          method: "POST",
          body: JSON.stringify({
            tdlightConnectionId: qrConnectionId,
            password: qrPassword,
          }),
        },
      );
      setQrPhase(status.phase);
      setQrLink(status.phase === "READY" ? "" : (status.qrLink ?? ""));
      setQrLastError(status.lastError ?? "");
      setQrUpdatedAt(status.updatedAt ?? null);
      if (status.phase !== "WAIT_PASSWORD") {
          setQrPassword("");
      }
      if (status.phase === "READY") {
        await onRefreshSessionState();
        const refreshedMe = await apiRequest<AuthUser>(token, `${apiBaseUrl}/api/auth/me`);
        setQrTelegramLinked(refreshedMe.telegramLinked);
        setQrLink("");
        await onRefreshAvailableChannels();
      } else {
        setQrTelegramLinked(false);
      }
    } catch (error) {
      setQrLastError(error instanceof Error ? error.message : "Не удалось подтвердить Telegram-пароль.");
    } finally {
      setQrBusy(false);
    }
  }

  async function handleSubscribeChannel(channel: TdlightAvailableChannel) {
    setSubscribingChannelIds((current) =>
      current.includes(channel.telegramChannelId) ? current : [...current, channel.telegramChannelId],
    );
    try {
      setErrorMessage("");
      await onSubscribeChannel(channel);
      setSelectedChannelIds((current) => current.filter((channelId) => channelId !== channel.telegramChannelId));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось подключить канал.");
    } finally {
      setSubscribingChannelIds((current) => current.filter((channelId) => channelId !== channel.telegramChannelId));
    }
  }

  async function handleSubscribeSelectedChannels() {
    if (selectedChannels.length === 0) {
      return;
    }

    setBatchSubscribeBusy(true);
    setSubscribingChannelIds(selectedChannels.map((channel) => channel.telegramChannelId));
    try {
      setErrorMessage("");
      await onSubscribeChannels(selectedChannels);
      setSelectedChannelIds([]);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось подключить выбранные каналы.");
    } finally {
      setSubscribingChannelIds([]);
      setBatchSubscribeBusy(false);
    }
  }

  function toggleChannelSelection(channelId: string) {
    setSelectedChannelIds((current) =>
      current.includes(channelId)
        ? current.filter((value) => value !== channelId)
        : [...current, channelId],
    );
  }

  function toggleAllChannelsSelection() {
    if (selectedChannels.length === unsubscribedChannels.length && unsubscribedChannels.length > 0) {
      setSelectedChannelIds([]);
      return;
    }
    setSelectedChannelIds(unsubscribedChannels.map((channel) => channel.telegramChannelId));
  }

  async function handleConversation(event: FormEvent) {
    event.preventDefault();
    if (!conversation || !canManageConversation) {
      if (!canManageConversation) {
        setErrorMessage("Только owner или admin могут менять фото и название чата.");
      }
      return;
    }
    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateConversation(conversation.id, {
        title: conversationTitle.trim(),
        avatar: conversationAvatar,
      });
      setConversationAvatar(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить чат.");
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleMute() {
    if (!conversation) {
      return;
    }

    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateConversation(conversation.id, {
        title: conversationTitle.trim(),
        muted: !conversation.muted,
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить уведомления.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteConversation() {
    if (!conversation || !canManageConversation) {
      setErrorMessage("Только owner или admin могут удалить чат.");
      return;
    }

    setBusy(true);
    try {
      setErrorMessage("");
      await onDeleteConversation(conversation.id);
    } catch (error) {
      const fallbackMessage = isChannelConversation
        ? "Не удалось отключить канал. Попробуйте ещё раз."
        : "Не удалось удалить чат.";
      const normalizedMessage =
        error instanceof Error && error.message && error.message !== "Failed to fetch"
          ? error.message
          : fallbackMessage;
      setErrorMessage(normalizedMessage);
    } finally {
      setBusy(false);
    }
  }

  const rootClassName = inline
    ? styles.inlineRoot
    : `${styles.backdrop} ${mode === "profile" ? styles.profileBackdrop : ""}`.trim();
  const drawerClassName = inline
    ? `${styles.drawer} ${styles.inlineDrawer}`
    : `${styles.drawer} ${mode === "profile" ? styles.profileDrawer : ""}`.trim();

  return (
    <div className={rootClassName}>
      <section className={drawerClassName}>
        <div className={styles.head}>
          <div>
            <div className={styles.caption}>
              {mode === "create" && "Новый чат"}
              {mode === "join" && "Инвайт"}
              {mode === "profile" && "Профиль"}
              {mode === "conversation" && (isChannelConversation ? "Канал" : "Настройки")}
            </div>
            <h3>
              {mode === "create" && "Создать пространство"}
              {mode === "join" && "Войти по коду"}
              {mode === "profile" && "Профиль Hermes"}
              {mode === "conversation" && (isChannelConversation ? "Настройки канала" : "Настройки чата")}
            </h3>
          </div>
          <button className={styles.close} type="button" onClick={onClose}>
            ×
          </button>
        </div>

        {errorMessage && <div className={styles.errorBanner}>{errorMessage}</div>}

        {mode === "create" && (
          <form className={styles.form} onSubmit={handleCreate}>
            <input value={createTitle} onChange={(event) => setCreateTitle(event.target.value)} placeholder="Название чата" />
            <button type="submit" disabled={busy}>
              Создать чат
            </button>
          </form>
        )}

        {mode === "join" && (
          <form className={styles.form} onSubmit={handleJoin}>
            <input value={inviteCode} onChange={(event) => setInviteCode(event.target.value)} placeholder="join_..." />
            <button type="submit" disabled={busy}>
              Войти в чат
            </button>
          </form>
        )}

        {mode === "profile" && (
          <form className={styles.form} onSubmit={handleProfile}>
            <section className={styles.sectionCard}>
              <div className={styles.sectionHead}>
                <strong>Профиль Hermes</strong>
                <span>Основные данные аккаунта</span>
              </div>
            <input value={profileName} onChange={(event) => setProfileName(event.target.value)} placeholder="Имя в Hermes" />
            <input
              value={profileUsername}
              onChange={(event) => setProfileUsername(event.target.value)}
              placeholder="Ник в Hermes"
            />
            <label className={styles.fileField}>
              <span>Аватар</span>
              <input type="file" accept="image/*" onChange={(event) => setProfileAvatar(event.target.files?.[0] ?? null)} />
            </label>
            <label className={styles.checkbox}>
              <input
                type="checkbox"
                checked={removeProfileAvatar}
                onChange={(event) => setRemoveProfileAvatar(event.target.checked)}
              />
              <span>Удалить текущий аватар</span>
            </label>
            <button type="submit" disabled={busy}>
              Сохранить профиль
            </button>
            </section>

            <section className={styles.sectionCard}>
              <div className={styles.sectionHead}>
                <strong>Кэш медиа</strong>
                <span>Посты и вложения уже держатся локально на устройстве.</span>
              </div>
              <label className={styles.fieldRow}>
                <span>Удалять кэш через</span>
                <select
                  value={cacheTtlHours}
                  onChange={(event) => setCacheTtlHours(Number(event.target.value))}
                  disabled={cacheBusy}
                >
                  <option value={6}>6 часов</option>
                  <option value={12}>12 часов</option>
                  <option value={24}>24 часа</option>
                  <option value={72}>3 дня</option>
                  <option value={168}>7 дней</option>
                </select>
              </label>
              <div className={styles.statusPanel}>
                <div>Файлов в кэше: <strong>{cacheStats.entries}</strong></div>
                <div>Сообщений в кэше: <strong>{channelMessageCacheStats.messages}</strong></div>
                <div>Памяти используется: <strong>{formatBytes(cacheStats.totalSizeBytes + channelMessageCacheStats.totalSizeBytes)}</strong></div>
                <div>Текущий TTL: <strong>{cacheStats.ttlHours} ч</strong></div>
                <div>Самый свежий объект: <strong>{cacheStats.newestCreatedAt ? new Date(cacheStats.newestCreatedAt).toLocaleString("ru-RU") : "n/a"}</strong></div>
              </div>
              <div className={styles.actionRow}>
                <button className={styles.secondary} type="button" disabled={cacheBusy} onClick={() => void handleSaveCacheSettings()}>
                  Сохранить TTL
                </button>
                <button className={styles.secondary} type="button" disabled={cacheBusy} onClick={() => void handleClearCache()}>
                  Очистить кэш
                </button>
              </div>
            </section>

            <section className={styles.sectionCard}>
              <div className={styles.sectionHead}>
                <strong>Telegram по QR</strong>
                <span>Локальный TDLight REAL login прямо из профиля.</span>
              </div>
              <div className={styles.statusPanel}>
                <div>Статус: <strong>{qrPhase}</strong></div>
                <div>Telegram: <strong>{qrTelegramLinked ? "подключён" : "ещё не привязан"}</strong></div>
                <div>Обновлено: <strong>{qrUpdatedAt ? new Date(qrUpdatedAt).toLocaleString("ru-RU") : "n/a"}</strong></div>
              </div>
              {qrLastError ? <div className={styles.helperNote}>{qrLastError}</div> : null}
              {qrImageUrl ? (
                <div className={styles.qrBox}>
                  <img src={qrImageUrl} alt="Telegram QR login" className={styles.qrImage} />
                </div>
              ) : qrPhase === "WAIT_PASSWORD" ? (
                <div className={styles.helperNote}>
                  QR уже подтверждён. Введите облачный пароль Telegram, чтобы завершить вход.
                </div>
              ) : qrPhase === "READY" ? (
                <div className={styles.helperNote}>
                  TDLight уже авторизован на этой локальной сессии. Новый QR не нужен, аккаунт можно использовать сразу.
                </div>
              ) : (
                <div className={styles.helperNote}>Запустите QR-вход, затем отсканируйте код телефоном из Telegram.</div>
              )}
              {qrPhase === "WAIT_PASSWORD" ? (
                <div className={styles.actionStack}>
                  <input
                    type="password"
                    value={qrPassword}
                    onChange={(event) => setQrPassword(event.target.value)}
                    placeholder="Пароль Telegram 2FA"
                    autoComplete="current-password"
                  />
                  <button type="button" disabled={qrBusy || !qrPassword.trim()} onClick={() => void handleSubmitQrPassword()}>
                    {qrBusy ? "Проверяем пароль..." : "Подтвердить пароль"}
                  </button>
                </div>
              ) : null}
              <div className={styles.actionRow}>
                <button type="button" disabled={qrBusy} onClick={() => void handleStartQrLink()}>
                  {qrBusy ? "Готовим QR..." : "Запустить QR-вход"}
                </button>
                <button className={styles.secondary} type="button" disabled={qrBusy} onClick={() => void handleResetQrLink()}>
                  Сбросить Telegram-сессию
                </button>
                {qrLink ? (
                  <button
                    className={styles.secondary}
                    type="button"
                    onClick={() => window.open(qrLink, "_blank", "noopener,noreferrer")}
                  >
                    Открыть в Telegram
                  </button>
                ) : null}
              </div>
            </section>

            <section className={styles.sectionCard}>
              <div className={styles.sectionHead}>
                <strong>Подключить каналы</strong>
                <span>Здесь только Telegram-каналы, которые ещё не подключены к Hermes.</span>
              </div>
              <div className={styles.actionRow}>
                <button
                  className={styles.secondary}
                  type="button"
                  disabled={loadingAvailableChannels || batchSubscribeBusy || !qrTelegramLinked}
                  onClick={() => void onRefreshAvailableChannels()}
                >
                  {loadingAvailableChannels ? "Обновляем..." : "Обновить список"}
                </button>
                <button
                  className={styles.secondary}
                  type="button"
                  disabled={batchSubscribeBusy || unsubscribedChannels.length === 0}
                  onClick={toggleAllChannelsSelection}
                >
                  {selectedChannels.length === unsubscribedChannels.length && unsubscribedChannels.length > 0
                    ? "Снять выбор"
                    : "Выбрать все"}
                </button>
              </div>
              {qrTelegramLinked && unsubscribedChannels.length > 0 ? (
                <div className={styles.batchBar}>
                  <span className={styles.batchCaption}>
                    Выбрано: <strong>{selectedChannels.length}</strong> из <strong>{unsubscribedChannels.length}</strong>
                  </span>
                  <button
                    className={`${styles.secondary} ${batchSubscribeBusy ? styles.buttonBusy : ""}`}
                    type="button"
                    disabled={batchSubscribeBusy || selectedChannels.length === 0}
                    onClick={() => void handleSubscribeSelectedChannels()}
                  >
                    {batchSubscribeBusy ? `Подключаем ${selectedChannels.length}...` : "Подключить выбранные"}
                  </button>
                </div>
              ) : null}
              {qrTelegramLinked && unsubscribedChannels.length > 0 ? (
                <div className={styles.channelList}>
                  {unsubscribedChannels.map((channel) => (
                    <div
                      key={channel.telegramChannelId}
                      className={`${styles.channelCard} ${
                        selectedChannelIds.includes(channel.telegramChannelId) ? styles.channelCardSelected : ""
                      }`}
                    >
                      <label className={styles.channelSelect}>
                        <input
                          type="checkbox"
                          checked={selectedChannelIds.includes(channel.telegramChannelId)}
                          disabled={batchSubscribeBusy || subscribingChannelIds.includes(channel.telegramChannelId)}
                          onChange={() => toggleChannelSelection(channel.telegramChannelId)}
                        />
                        <span>Выбрать</span>
                      </label>
                      <Avatar token={token} name={channel.channelTitle} src={channel.avatarUrl} size="md" />
                      <div className={styles.channelMeta}>
                        <strong>{channel.channelTitle}</strong>
                        <span>{channel.telegramChannelHandle ? `@${channel.telegramChannelHandle}` : "Telegram channel"}</span>
                      </div>
                      <button
                        className={`${styles.secondary} ${
                          subscribingChannelIds.includes(channel.telegramChannelId) ? styles.buttonBusy : ""
                        }`}
                        type="button"
                        disabled={batchSubscribeBusy || subscribingChannelIds.includes(channel.telegramChannelId)}
                        onClick={() => void handleSubscribeChannel(channel)}
                      >
                        {subscribingChannelIds.includes(channel.telegramChannelId) ? "Подключаем..." : "Подключить"}
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className={styles.helperNote}>
                  {qrTelegramLinked
                    ? "Неподключённых каналов пока нет. После выхода из Telegram этот список должен очищаться, а уже созданные Hermes-каналы останутся."
                    : "Сначала завершите вход в Telegram. После выхода из Telegram этот список очищается, а уже созданные Hermes-каналы остаются."}
                </div>
              )}
            </section>
          </form>
        )}

        {mode === "conversation" && conversation && (
          <form className={styles.form} onSubmit={handleConversation}>
            {isChannelConversation ? (
              <div className={styles.channelReadonlyCard}>
                <div className={styles.helperNote}>
                  Канал синхронизируется только для чтения. Если отключить его здесь, он вернётся в список каналов,
                  которые можно подключить заново.
                </div>
                <div className={styles.destructiveRow}>
                  <button
                    className={`${styles.danger} ${styles.compactAction} ${styles.destructiveAction}`}
                    type="button"
                    disabled={busy}
                    onClick={() => void handleDeleteConversation()}
                  >
                    {busy ? "Отключаем канал..." : "Отключить канал"}
                  </button>
                </div>
              </div>
            ) : (
              <>
                {!canManageConversation && (
                  <div className={styles.helperNote}>
                    Фото, название и удаление чата доступны только ролям <strong>OWNER</strong> или <strong>ADMIN</strong>.
                  </div>
                )}
                <input
                  value={conversationTitle}
                  onChange={(event) => setConversationTitle(event.target.value)}
                  placeholder="Название чата"
                  disabled={!canManageConversation || busy}
                />
                <label className={`${styles.fileField} ${styles.avatarField}`}>
                  <div className={styles.avatarPreviewRow}>
                    <Avatar
                      token={token}
                      name={conversationTitle.trim() || conversation.title}
                      src={conversationAvatarPreview ?? conversation.avatarUrl}
                      size="md"
                    />
                    <div className={styles.avatarPreviewMeta}>
                      <span>Аватар чата</span>
                      <small>{conversationAvatar ? conversationAvatar.name : "Текущее фото или новый файл"}</small>
                    </div>
                  </div>
                  <input
                    type="file"
                    accept="image/*"
                    disabled={!canManageConversation || busy}
                    onChange={(event) => setConversationAvatar(event.target.files?.[0] ?? null)}
                  />
                </label>
                <div className={styles.actionRow}>
                  <button
                    className={`${styles.secondary} ${styles.compactAction}`}
                    type="button"
                    disabled={busy}
                    onClick={() => void handleToggleMute()}
                  >
                    {conversation.muted ? "Включить уведомления" : "Заглушить чат"}
                  </button>
                  <button className={styles.compactAction} type="submit" disabled={!canManageConversation || busy}>
                    Сохранить чат
                  </button>
                </div>
              </>
            )}
            {!isChannelConversation && (
              <div className={styles.destructiveRow}>
                <button
                  className={`${styles.danger} ${styles.compactAction} ${styles.destructiveAction}`}
                  type="button"
                  disabled={!canManageConversation || busy}
                  onClick={() => void handleDeleteConversation()}
                >
                  Удалить чат
                </button>
              </div>
            )}
          </form>
        )}
      </section>
    </div>
  );
}
