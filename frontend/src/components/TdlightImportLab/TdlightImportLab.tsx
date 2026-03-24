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

type TdlightDiagnostics = {
  tdlightConnectionId: number;
  telegramChannelId: string;
  telegramChannelHandle: string | null;
  channelTitle: string;
  rawFetchedPostCount: number;
  mappedPostCount: number;
  posts: Array<{
    remoteMessageId: string;
    authorDisplayName: string;
    publishedAt: string;
    rawMediaCount: number;
    importedMediaCount: number;
  }>;
};

type TdlightMigration = {
  id: number;
  status: string;
  targetConversationId: number | null;
  importedMessageCount: number;
  importedMediaCount: number;
  lastError: string | null;
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
  const [diagnostics, setDiagnostics] = useState<TdlightDiagnostics | null>(null);
  const [migration, setMigration] = useState<TdlightMigration | null>(null);
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

  async function runImportFlow() {
    const token = accessToken || (await bootstrapSession());
    const tdlightConnectionId = connectionId ?? (await ensureConnection(token));

    // Keep the lab idempotent: repeated runs should replace the imported sample,
    // not fail on duplicate Telegram source message keys.
    await apiRequest<void>(token, `${getApiBase()}/api/tdlight/cleanup`, {
      method: "POST",
    });
    setConversations([]);
    setMessages([]);
    setSelectedConversationId(null);
    setDiagnostics(null);
    setMigration(null);

    const nextReadiness = await apiRequest<TdlightReadiness>(token, `${getApiBase()}/api/tdlight/readiness`);
    setReadiness(nextReadiness);
    const nextMe = await apiRequest<AuthUser>(token, `${getApiBase()}/api/auth/me`);
    setMe(nextMe);

    const nextDiagnostics = await apiRequest<TdlightDiagnostics>(
      token,
      `${getApiBase()}/api/tdlight/diagnostics/public-channel`,
      {
        method: "POST",
        body: JSON.stringify({
          tdlightConnectionId,
          telegramChannelId: channelReference,
          telegramChannelHandle: channelReference,
        }),
      },
    );
    setDiagnostics(nextDiagnostics);

    const queued = await apiRequest<TdlightMigration>(token, `${getApiBase()}/api/tdlight/migrations`, {
      method: "POST",
      body: JSON.stringify({
        tdlightConnectionId,
        telegramChannelId: nextDiagnostics.telegramChannelId,
        telegramChannelHandle: nextDiagnostics.telegramChannelHandle,
        importMedia: false,
      }),
    });

    const processed = await apiRequest<TdlightMigration>(
      token,
      `${getApiBase()}/api/tdlight/migrations/${queued.id}/process`,
      { method: "POST" },
    );
    setMigration(processed);

    const nextConversations = await apiRequest<ConversationSummary[]>(token, `${getApiBase()}/api/conversations`);
    setConversations(nextConversations);

    if (processed.targetConversationId) {
      setSelectedConversationId(processed.targetConversationId);
      const nextMessages = await apiRequest<ConversationMessage[]>(
        token,
        `${getApiBase()}/api/conversations/${processed.targetConversationId}/messages`,
      );
      setMessages(nextMessages);
    }

    const refreshedMe = await apiRequest<AuthUser>(token, `${getApiBase()}/api/auth/me`);
    setMe(refreshedMe);
  }

  function handleRun() {
    startTransition(async () => {
      try {
        setError("");
        await runImportFlow();
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "Import flow failed");
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
            Эта страница работает поверх отдельного backend на <strong>8082</strong>, поднимает локальную сессию,
            смотрит diagnostics, запускает migration и сразу показывает, что реально появилось в Hermes.
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
                <button className={styles.primaryButton} disabled={isPending} onClick={handleRun}>
                  {isPending ? "Запускаем..." : "Resolve + Diagnostics + Import"}
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
              <h3 className={styles.panelTitle}>Diagnostics</h3>
              <pre className={styles.code}>{formatJson(diagnostics)}</pre>
            </section>
            <section className={styles.panel}>
              <h3 className={styles.panelTitle}>Migration</h3>
              <pre className={styles.code}>{formatJson(migration)}</pre>
            </section>
          </div>
        </section>

        <section className={styles.grid}>
          <div className={styles.card}>
            <h2 className={styles.cardTitle}>Conversations</h2>
            <div className={styles.list}>
              {conversations.length === 0 ? (
                <div className={styles.item}>
                  <p className={styles.itemMeta}>Пока пусто. После успешного import здесь появится Hermes conversation.</p>
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
