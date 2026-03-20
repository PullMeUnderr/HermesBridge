"use client";

import styles from "./Sidebar.module.scss";
import { Avatar } from "@/components/ui/Avatar";
import { formatClock, renderPresenceLabel } from "@/lib/format";
import type { AuthUser, ConversationSummary } from "@/types/api";

interface SidebarProps {
  token: string;
  me: AuthUser;
  conversations: ConversationSummary[];
  selectedConversationId: number | null;
  loading: boolean;
  onLogout: () => void;
  onRefresh: () => Promise<void>;
  onSelectConversation: (conversationId: number) => void;
  onOpenCreate: () => void;
  onOpenJoin: () => void;
  onOpenProfile: () => void;
}

export function Sidebar({
  token,
  me,
  conversations,
  selectedConversationId,
  loading,
  onLogout,
  onRefresh,
  onSelectConversation,
  onOpenCreate,
  onOpenJoin,
  onOpenProfile,
}: SidebarProps) {
  return (
    <aside className={styles.sidebar}>
      <section className={styles.panel}>
        <div className={styles.head}>
          <div>
            <div className={styles.eyebrow}>Hermes Bridge</div>
            <h2>Messages</h2>
          </div>
          <button className={styles.ghostButton} type="button" onClick={onLogout}>
            Выйти
          </button>
        </div>
        <p className={styles.muted}>Чаты, участники, инвайты и Telegram sync.</p>

        <div className={styles.userCard}>
          <Avatar token={token} name={me.displayName || me.username} src={me.avatarUrl} size="lg" />
          <div className={styles.userMeta}>
            <strong>{me.displayName}</strong>
            <span>@{me.username}</span>
            <span>{renderPresenceLabel(me)}</span>
          </div>
          <button className={styles.secondaryButton} type="button" onClick={onOpenProfile}>
            Профиль
          </button>
        </div>

        <div className={styles.toolRow}>
          <button className={styles.primaryButton} type="button" onClick={onOpenCreate}>
            Создать чат
          </button>
          <button className={styles.secondaryButton} type="button" onClick={onOpenJoin}>
            Войти по коду
          </button>
        </div>
      </section>

      <section className={`${styles.panel} ${styles.listPanel}`}>
        <div className={styles.panelHead}>
          <div>
            <div className={styles.caption}>Навигация</div>
            <h3>Твои чаты</h3>
          </div>
          <button className={styles.iconButton} type="button" onClick={() => void onRefresh()}>
            ↻
          </button>
        </div>

        <div className={styles.list}>
          {conversations.map((conversation) => (
            <button
              key={conversation.id}
              type="button"
              className={`${styles.row} ${conversation.id === selectedConversationId ? styles.active : ""}`}
              onClick={() => onSelectConversation(conversation.id)}
            >
              <Avatar token={token} name={conversation.title} src={conversation.avatarUrl} size="md" />
              <div className={styles.rowCopy}>
                <div className={styles.rowTop}>
                  <strong>{conversation.title}</strong>
                  <span>{formatClock(conversation.lastMessageCreatedAt || conversation.createdAt)}</span>
                </div>
                <div className={styles.rowBottom}>
                  <span>{conversation.lastMessagePreview || "Новый чат без сообщений"}</span>
                  {conversation.unreadCount > 0 && <span className={styles.badge}>{conversation.unreadCount}</span>}
                </div>
              </div>
            </button>
          ))}

          {!loading && conversations.length === 0 && (
            <div className={styles.empty}>
              <strong>Пока пусто</strong>
              <span>Создай чат или зайди по инвайт-коду.</span>
            </div>
          )}
        </div>
      </section>
    </aside>
  );
}
