"use client";

import { useState, useTransition } from "react";
import { apiRequest } from "@/lib/api";
import type { AuthUser, ConversationMessage, ConversationSummary } from "@/types/api";
import styles from "./TdlightImportLab.module.scss";

const DEFAULT_BOOTSTRAP_TOKEN = "local-master-token";

type TdlightReadiness = {
  ready: boolean;
  clientMode: string;
  blockers: string[];
  hints: string[];
};

type TdlightResolvedChannel = {
  originalReference: string;
  telegramChannelId: string;
  telegramChannelHandle: string | null;
  channelTitle: string;
  normalizedReference: string;
  referenceKind: string;
  publicChannel: boolean;
  eligibility: string;
  eligibilityReason: string | null;
  eligibleForMigration: boolean;
};

type TdlightAvailableChannel = {
  telegramChannelId: string;
  telegramChannelHandle: string | null;
  channelTitle: string;
  subscribed: boolean;
  subscriptionId: number | null;
  conversationId: number | null;
};

type TdlightSubscription = {
  id: number;
  tdlightConnectionId: number;
  conversationId: number;
  telegramChannelId: string;
  telegramChannelHandle: string | null;
  channelTitle: string;
  status: string;
  subscribedAt: string;
  lastSyncedRemoteMessageId: string | null;
  lastSyncedAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
};

function getApiBase() {
  return process.env.NEXT_PUBLIC_API_BASE_URL?.trim() ?? "http://127.0.0.1:8082";
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

export function TdlightImportLab() {
  const [bootstrapToken, setBootstrapToken] = useState(DEFAULT_BOOTSTRAP_TOKEN);
  const [channelReference, setChannelReference] = useState("telegram");
  const [accessToken, setAccessToken] = useState("");
  const [connectionId, setConnectionId] = useState<number | null>(null);
  const [readiness, setReadiness] = useState<TdlightReadiness | null>(null);
  const [me, setMe] = useState<AuthUser | null>(null);
  const [resolvedChannel, setResolvedChannel] = useState<TdlightResolvedChannel | null>(null);
  const [availableChannels, setAvailableChannels] = useState<TdlightAvailableChannel[]>([]);
  const [subscriptions, setSubscriptions] = useState<TdlightSubscription[]>([]);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [isPending, startTransition] = useTransition();

  async function bootstrapSession() {
    const response = await fetch(`${getApiBase()}/api/auth/session`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ token: bootstrapToken }),
    });
    if (!response.ok) {
      throw new Error(`Bootstrap auth failed: HTTP ${response.status}`);
    }
    const session = (await response.json()) as { accessToken: string };
    setAccessToken(session.accessToken);
    return session.accessToken;
  }

  async function ensureConnection(token: string) {
    const connection = await apiRequest<{ id: number }>(token, `${getApiBase()}/api/tdlight/connections/dev`, {
      method: "POST",
      body: JSON.stringify({}),
    });
    setConnectionId(connection.id);
    return connection.id;
  }

  async function loadSyncState(token: string, tdlightConnectionId: number) {
    const [nextReadiness, nextMe, nextSubscriptions, nextChannels] = await Promise.all([
      apiRequest<TdlightReadiness>(token, `${getApiBase()}/api/tdlight/readiness`),
      apiRequest<AuthUser>(token, `${getApiBase()}/api/auth/me`),
      apiRequest<TdlightSubscription[]>(token, `${getApiBase()}/api/tdlight/subscriptions`),
      apiRequest<TdlightAvailableChannel[]>(token, `${getApiBase()}/api/tdlight/channels?tdlightConnectionId=${tdlightConnectionId}`),
    ]);
    setReadiness(nextReadiness);
    setMe(nextMe);
    setSubscriptions(nextSubscriptions);
    setAvailableChannels(nextChannels);
    return { nextSubscriptions };
  }

  async function refreshConversations(token: string, focusConversationId?: number | null) {
    const nextConversations = await apiRequest<ConversationSummary[]>(token, `${getApiBase()}/api/conversations`);
    setConversations(nextConversations);

    const conversationId = focusConversationId ?? selectedConversationId;
    if (conversationId) {
      setSelectedConversationId(conversationId);
      const nextMessages = await apiRequest<ConversationMessage[]>(
        token,
        `${getApiBase()}/api/conversations/${conversationId}/messages`,
      );
      setMessages(nextMessages);
    }
  }

  async function runSubscribeFlow() {
    const token = accessToken || (await bootstrapSession());
    const tdlightConnectionId = connectionId ?? (await ensureConnection(token));
    setResolvedChannel(null);
    const resolved = await apiRequest<TdlightResolvedChannel>(
      token,
      `${getApiBase()}/api/tdlight/resolve/public-channel`,
      {
        method: "POST",
        body: JSON.stringify({
          tdlightConnectionId,
          telegramChannelId: channelReference,
          telegramChannelHandle: channelReference,
        }),
      },
    );
    setResolvedChannel(resolved);

    const subscription = await apiRequest<TdlightSubscription>(token, `${getApiBase()}/api/tdlight/subscriptions`, {
      method: "POST",
      body: JSON.stringify({
        tdlightConnectionId,
        telegramChannelId: resolved.telegramChannelId,
        telegramChannelHandle: resolved.telegramChannelHandle,
        channelTitle: resolved.channelTitle,
      }),
    });

    await loadSyncState(token, tdlightConnectionId);
    await refreshConversations(token, subscription.conversationId);
  }

  async function handleLoadChannels() {
    const token = accessToken || (await bootstrapSession());
    const tdlightConnectionId = connectionId ?? (await ensureConnection(token));
    await loadSyncState(token, tdlightConnectionId);
  }

  function handleSubscribe() {
    startTransition(async () => {
      try {
        setError("");
        await runSubscribeFlow();
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "Subscribe flow failed");
      }
    });
  }

  function handleRefreshState() {
    startTransition(async () => {
      try {
        setError("");
        await handleLoadChannels();
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "State refresh failed");
      }
    });
  }

  function handleRefreshMessages(conversationId: number) {
    startTransition(async () => {
      try {
        if (!accessToken) {
          return;
        }
        setError("");
        setSelectedConversationId(conversationId);
        const nextMessages = await apiRequest<ConversationMessage[]>(
          accessToken,
          `${getApiBase()}/api/conversations/${conversationId}/messages`,
        );
        setMessages(nextMessages);
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "Messages refresh failed");
      }
    });
  }

  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <section className={styles.hero}>
          <div className={styles.eyebrow}>TDLight REAL Import Lab</div>
          <h1 className={styles.title}>Импорт каналов из Telegram в Hermes на живом REAL backend.</h1>
          <p className={styles.lede}>
            Эта страница работает поверх отдельного backend на <strong>8082</strong>, поднимает локальную TDLight-сессию,
            позволяет подписать выбранный канал на постоянную синхронизацию и показывает, что уже появилось в Hermes.
          </p>
        </section>

        <section className={styles.grid}>
          <div className={styles.card}>
            <h2 className={styles.cardTitle}>Управление</h2>
            <div className={styles.stack}>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="bootstrap-token">
                  Bootstrap token
                </label>
                <input
                  id="bootstrap-token"
                  className={styles.input}
                  value={bootstrapToken}
                  onChange={(event) => setBootstrapToken(event.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="channel-reference">
                  Channel handle / id
                </label>
                <input
                  id="channel-reference"
                  className={styles.input}
                  value={channelReference}
                  onChange={(event) => setChannelReference(event.target.value)}
                />
              </div>
              <div className={styles.buttonRow}>
                <button className={styles.primaryButton} disabled={isPending} onClick={handleSubscribe}>
                  {isPending ? "Подключаем..." : "Resolve + Subscribe"}
                </button>
                <button className={styles.secondaryButton} disabled={isPending} onClick={handleRefreshState}>
                  Обновить каналы
                </button>
                {selectedConversationId ? (
                  <button
                    className={styles.secondaryButton}
                    disabled={isPending}
                    onClick={() => handleRefreshMessages(selectedConversationId)}
                  >
                    Обновить сообщения
                  </button>
                ) : null}
              </div>
              <div className={styles.status}>
                API base: {getApiBase()}
                {"\n"}Access token: {accessToken ? "получен" : "нет"}
                {"\n"}Connection id: {connectionId ?? "n/a"}
                {"\n"}Mode: subscribe from current moment, no history backfill
              </div>
              {error ? <div className={`${styles.status} ${styles.statusError}`}>{error}</div> : null}
            </div>
          </div>

          <div className={styles.panelGrid}>
            <section className={styles.panel}>
              <h3 className={styles.panelTitle}>My account</h3>
              <pre className={styles.code}>{formatJson(me)}</pre>
            </section>
            <section className={styles.panel}>
              <h3 className={styles.panelTitle}>Readiness</h3>
              <pre className={styles.code}>{formatJson(readiness)}</pre>
            </section>
            <section className={styles.panel}>
              <h3 className={styles.panelTitle}>Resolved channel</h3>
              <pre className={styles.code}>{formatJson(resolvedChannel)}</pre>
            </section>
            <section className={styles.panel}>
              <h3 className={styles.panelTitle}>Subscriptions</h3>
              <pre className={styles.code}>{formatJson(subscriptions)}</pre>
            </section>
          </div>
        </section>

        <section className={styles.grid}>
          <div className={styles.card}>
            <h2 className={styles.cardTitle}>Available channels</h2>
            <div className={styles.list}>
              {availableChannels.length === 0 ? (
                <div className={styles.item}>
                  <p className={styles.itemMeta}>
                    TDLight пока не вернул список joined public channels для этого аккаунта. В этом случае можно
                    подключать канал по `@handle` через поле выше.
                  </p>
                </div>
              ) : (
                availableChannels.map((channel) => (
                  <article key={channel.telegramChannelId} className={styles.item}>
                    <p className={styles.itemTitle}>{channel.channelTitle}</p>
                    <p className={styles.itemMeta}>
                      {channel.telegramChannelHandle ? `@${channel.telegramChannelHandle}` : channel.telegramChannelId}
                    </p>
                    <p className={styles.itemBody}>
                      {channel.subscribed
                        ? `Уже подключен в Hermes, conversationId=${channel.conversationId ?? "n/a"}`
                        : "Можно подключить без затягивания старой истории."}
                    </p>
                  </article>
                ))
              )}
            </div>
          </div>

          <div className={styles.card}>
            <h2 className={styles.cardTitle}>Conversations</h2>
            <div className={styles.list}>
              {conversations.length === 0 ? (
                <div className={styles.item}>
                  <p className={styles.itemMeta}>
                    Пока пусто. После подключения канала здесь появится Hermes conversation, а новые посты начнут
                    прилетать по sync-loop.
                  </p>
                </div>
              ) : (
                conversations.map((conversation) => (
                  <button
                    key={conversation.id}
                    className={styles.item}
                    style={{ textAlign: "left", cursor: "pointer" }}
                    onClick={() => handleRefreshMessages(conversation.id)}
                  >
                    <p className={styles.itemTitle}>{conversation.title}</p>
                    <p className={styles.itemMeta}>
                      id={conversation.id} · {conversation.membershipRole} · unread={conversation.unreadCount}
                    </p>
                    <p className={styles.itemBody}>{conversation.lastMessagePreview ?? "Нет preview"}</p>
                  </button>
                ))
              )}
            </div>
          </div>

          <div className={styles.card}>
            <h2 className={styles.cardTitle}>Messages</h2>
            <div className={styles.list}>
              {messages.length === 0 ? (
                <div className={styles.item}>
                  <p className={styles.itemMeta}>Выберите conversation или сначала выполните import.</p>
                </div>
              ) : (
                messages.map((message) => (
                  <article key={message.id} className={styles.item}>
                    <p className={styles.itemTitle}>
                      {message.authorDisplayName} · {message.sourceTransport}
                    </p>
                    <p className={styles.itemMeta}>
                      messageId={message.id} · createdAt={message.createdAt}
                    </p>
                    <p className={styles.itemBody}>{message.body ?? "Без текста"}</p>
                  </article>
                ))
              )}
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
