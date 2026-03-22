"use client";

import styles from "./Sidebar.module.scss";
import { DrawerPanel } from "@/components/DrawerPanel/DrawerPanel";
import { Avatar } from "@/components/ui/Avatar";
import { formatClock, renderPresenceLabel } from "@/lib/format";
import type { AuthUser, ConversationSummary, DrawerMode } from "@/types/api";

interface SidebarProps {
  token: string;
  me: AuthUser;
  conversations: ConversationSummary[];
  selectedConversationId: number | null;
  loading: boolean;
  drawerMode: DrawerMode;
  drawerConversation: ConversationSummary | null;
  inlineDrawer: boolean;
  onLogout: () => void;
  onRefresh: () => Promise<void>;
  onSelectConversation: (conversationId: number) => void;
  onOpenCreate: () => void;
  onOpenJoin: () => void;
  onOpenProfile: () => void;
  onOpenConversationSettings: (conversationId: number) => void;
  onCloseDrawer: () => void;
  onCreateConversation: (title: string) => Promise<void>;
  onJoinConversation: (inviteCode: string) => Promise<void>;
  onUpdateProfile: (payload: {
    displayName: string;
    username: string;
    avatar?: File | null;
    removeAvatar?: boolean;
  }) => Promise<void>;
  onUpdateConversation: (
    conversationId: number,
    payload: { title: string; avatar?: File | null; removeAvatar?: boolean; muted?: boolean },
  ) => Promise<void>;
  onDeleteConversation: (conversationId: number) => Promise<void>;
}

export function Sidebar({
  token,
  me,
  conversations,
  selectedConversationId,
  loading,
  drawerMode,
  drawerConversation,
  inlineDrawer,
  onLogout,
  onRefresh,
  onSelectConversation,
  onOpenCreate,
  onOpenJoin,
  onOpenProfile,
  onOpenConversationSettings,
  onCloseDrawer,
  onCreateConversation,
  onJoinConversation,
  onUpdateProfile,
  onUpdateConversation,
  onDeleteConversation,
}: SidebarProps) {
  const drawerOpen = inlineDrawer && drawerMode !== null;

  return (
    <aside className={styles.sidebar} data-drawer-open={drawerOpen}>
      <div className={styles.sidebarScaleFrame}>
        <div className={styles.sidebarScaleContent}>
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
              <div className={styles.userIdentity}>
                <Avatar token={token} name={me.displayName || me.username} src={me.avatarUrl} size="lg" />
                <div className={styles.userMeta}>
                  <strong>{me.displayName}</strong>
                  <span>@{me.username}</span>
                  <span>{renderPresenceLabel(me)}</span>
                </div>
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

          {inlineDrawer && drawerMode && (
            <section className={`${styles.panel} ${styles.drawerPanel}`}>
              <DrawerPanel
                token={token}
                open={Boolean(drawerMode)}
                inline
                mode={drawerMode}
                me={me}
                conversation={drawerConversation}
                onClose={onCloseDrawer}
                onCreateConversation={onCreateConversation}
                onJoinConversation={onJoinConversation}
                onUpdateProfile={onUpdateProfile}
                onUpdateConversation={onUpdateConversation}
                onDeleteConversation={onDeleteConversation}
              />
            </section>
          )}

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
                <div
                  key={conversation.id}
                  className={`${styles.row} ${conversation.id === selectedConversationId ? styles.active : ""}`}
                >
                  <button type="button" className={styles.rowMain} onClick={() => onSelectConversation(conversation.id)}>
                    <Avatar token={token} name={conversation.title} src={conversation.avatarUrl} size="md" />
                    <div className={styles.rowCopy}>
                      <div className={styles.rowTop}>
                        <strong>{conversation.title}</strong>
                        <span>{formatClock(conversation.lastMessageCreatedAt || conversation.createdAt)}</span>
                      </div>
                      <div className={styles.rowBottom}>
                        <span>{conversation.lastMessagePreview || "Новый чат без сообщений"}</span>
                        {conversation.unreadCount > 0 && (
                          <span
                            className={`${styles.badge} ${conversation.hasUnreadMention ? styles.badgeMention : ""}`}
                          >
                            {conversation.unreadCount}
                          </span>
                        )}
                      </div>
                      <div className={styles.rowMeta}>
                        <span className={styles.role}>{conversation.membershipRole}</span>
                      </div>
                    </div>
                  </button>
                  <div className={styles.rowActions}>
                    <button
                      type="button"
                      className={styles.menuButton}
                      aria-label={`Открыть настройки чата ${conversation.title}`}
                      onClick={() => onOpenConversationSettings(conversation.id)}
                    >
                      …
                    </button>
                  </div>
                </div>
              ))}

              {!loading && conversations.length === 0 && (
                <div className={styles.empty}>
                  <strong>Пока пусто</strong>
                  <span>Создай чат или зайди по инвайт-коду.</span>
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </aside>
  );
}
