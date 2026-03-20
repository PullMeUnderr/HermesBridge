"use client";

import { useMemo, useState } from "react";
import styles from "./ConversationView.module.scss";
import { Avatar } from "@/components/ui/Avatar";
import { MessagesList } from "@/components/MessagesList/MessagesList";
import { MessageComposer } from "@/components/MessageComposer/MessageComposer";
import { MembersPanel } from "@/components/MembersPanel/MembersPanel";
import { renderPresenceLabel } from "@/lib/format";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationSummary,
  PendingAttachment,
} from "@/types/api";

interface ConversationViewProps {
  token: string;
  me: AuthUser;
  conversation: ConversationSummary | null;
  messages: ConversationMessage[];
  members: ConversationMember[];
  loading: boolean;
  onOpenSettings: () => void;
  onCreateInvite: () => Promise<void>;
  onRefreshConversation: () => Promise<void>;
  onSendMessage: (payload: {
    body: string;
    attachments: PendingAttachment[];
    replyToMessageId: number | null;
    sendAsVideoNote: boolean;
  }) => Promise<void>;
  onReply: (messageId: number) => ConversationMessage | null;
  onOpenPhotoViewer: (items: Array<{ src: string; fileName: string }>, activeIndex: number) => void;
}

export function ConversationView({
  token,
  me,
  conversation,
  messages,
  members,
  loading,
  onOpenSettings,
  onCreateInvite,
  onRefreshConversation,
  onSendMessage,
  onReply,
  onOpenPhotoViewer,
}: ConversationViewProps) {
  const [replyToMessageId, setReplyToMessageId] = useState<number | null>(null);

  const replyTarget = useMemo(
    () => (replyToMessageId ? messages.find((message) => message.id === replyToMessageId) ?? null : null),
    [messages, replyToMessageId],
  );

  if (!conversation) {
    return (
      <section className={styles.emptyState}>
        <div className={styles.eyebrow}>Hermes</div>
        <h2>Выбери чат</h2>
        <p>Здесь появятся сообщения, участники и действия выбранного чата.</p>
      </section>
    );
  }

  return (
    <section className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerMain}>
          <Avatar token={token} name={conversation.title} src={conversation.avatarUrl} size="lg" />
          <div>
            <div className={styles.topline}>
              <span className={styles.role}>{conversation.membershipRole}</span>
              <span className={styles.online}>Telegram sync</span>
            </div>
            <h2>{conversation.title}</h2>
            <p className={styles.meta}>
              {members.length} участников, профиль {renderPresenceLabel(me)}
            </p>
          </div>
        </div>

        <div className={styles.actions}>
          <button className={styles.secondaryButton} type="button" onClick={() => void onCreateInvite()}>
            Инвайт
          </button>
          <button className={styles.ghostButton} type="button" onClick={() => void onRefreshConversation()}>
            Обновить
          </button>
          <button className={styles.ghostButton} type="button" onClick={onOpenSettings}>
            Настройки
          </button>
        </div>
      </header>

      <div className={styles.columns}>
        <div className={styles.messagesPanel}>
          <MessagesList
            token={token}
            me={me}
            messages={messages}
            loading={loading}
            onReply={(messageId) => setReplyToMessageId(onReply(messageId)?.id ?? null)}
            onOpenPhotoViewer={onOpenPhotoViewer}
          />
          <MessageComposer
            members={members}
            replyTarget={replyTarget}
            onCancelReply={() => setReplyToMessageId(null)}
            onSend={async (payload) => {
              await onSendMessage({ ...payload, replyToMessageId });
              setReplyToMessageId(null);
            }}
          />
        </div>

        <MembersPanel token={token} members={members} />
      </div>
    </section>
  );
}
