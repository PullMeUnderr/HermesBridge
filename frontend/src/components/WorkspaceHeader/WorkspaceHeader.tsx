"use client";

import styles from "./WorkspaceHeader.module.scss";
import type { AuthUser, ConversationSummary } from "@/types/api";

interface WorkspaceHeaderProps {
  me: AuthUser;
  conversations: ConversationSummary[];
  selectedConversation: ConversationSummary | null;
}

export function WorkspaceHeader({ me, conversations, selectedConversation }: WorkspaceHeaderProps) {
  return (
    <header className={styles.header}>
      <div>
        <div className={styles.eyebrow}>Workspace</div>
        <h2>{selectedConversation ? selectedConversation.title : "Обзор Hermes"}</h2>
        <p className={styles.muted}>
          {selectedConversation
            ? "Сообщения, участники и настройки выбранного чата."
            : "Выбери чат слева или создай новый."}
        </p>
      </div>

      <div className={styles.metrics}>
        <article className={styles.metric}>
          <span>Чатов</span>
          <strong>{conversations.length}</strong>
        </article>
        <article className={styles.metric}>
          <span>Профиль</span>
          <strong>@{me.username}</strong>
        </article>
        <article className={styles.metric}>
          <span>Синхронизация</span>
          <strong>{me.telegramLinked ? "Telegram online" : "Оффлайн"}</strong>
        </article>
      </div>
    </header>
  );
}
