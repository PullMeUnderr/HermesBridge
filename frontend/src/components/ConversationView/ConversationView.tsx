"use client"

import { useMemo, useState } from "react"
import styles from "./ConversationView.module.scss"
import { Avatar } from "@/components/ui/Avatar"
import { MessagesList } from "@/components/MessagesList/MessagesList"
import { MessageComposer } from "@/components/MessageComposer/MessageComposer"
import { MembersPanel } from "@/components/MembersPanel/MembersPanel"
import { renderPresenceLabel } from "@/lib/format"
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationSummary,
  PendingAttachment,
} from "@/types/api"

interface ConversationViewProps {
  token: string
  me: AuthUser
  conversation: ConversationSummary | null
  messages: ConversationMessage[]
  members: ConversationMember[]
  loading: boolean
  onOpenSettings: () => void
  onCreateInvite: () => Promise<void>
  onRefreshConversation: () => Promise<void>
  onSendMessage: (payload: {
    body: string
    attachments: PendingAttachment[]
    replyToMessageId: number | null
    sendAsVideoNote: boolean
  }) => Promise<void>
  onReply: (messageId: number) => ConversationMessage | null
  onOpenPhotoViewer: (
    items: Array<{ src: string; fileName: string }>,
    activeIndex: number,
  ) => void
  onBackToSidebar?: () => void
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
  onBackToSidebar,
}: ConversationViewProps) {
  const [replyToMessageId, setReplyToMessageId] = useState<number | null>(null)
  const [membersOpen, setMembersOpen] = useState(false)

  const replyTarget = useMemo(
    () =>
      replyToMessageId
        ? (messages.find((message) => message.id === replyToMessageId) ?? null)
        : null,
    [messages, replyToMessageId],
  )

  if (!conversation) {
    return (
      <section className={styles.emptyState}>
        <div className={styles.eyebrow}>Hermes</div>
        <h2>Выбери чат</h2>
        <p>Здесь появятся сообщения, участники и действия выбранного чата.</p>
      </section>
    )
  }

  return (
    <section className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerMain}>
          {onBackToSidebar && (
            <button
              className={styles.backButton}
              type='button'
              onClick={onBackToSidebar}
              aria-label='Назад к чатам'
            >
              <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
                <path
                  d='M14.7 5.3a1 1 0 0 1 0 1.4L10.41 11H20a1 1 0 1 1 0 2h-9.59l4.3 4.3a1 1 0 0 1-1.42 1.4l-6-6a1 1 0 0 1 0-1.4l6-6a1 1 0 0 1 1.4 0Z'
                  fill='currentColor'
                />
              </svg>
            </button>
          )}
          <Avatar
            token={token}
            name={conversation.title}
            src={conversation.avatarUrl}
            size='md'
          />
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
          <button
            className={styles.iconButton}
            type='button'
            onClick={() => setMembersOpen(true)}
            aria-label='Участники'
          >
            <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
              <path
                d='M16 11a3 3 0 1 0-2.999-3A3 3 0 0 0 16 11Zm-8 0A3 3 0 1 0 5 8a3 3 0 0 0 3 3Zm8 2c-2.2 0-6 1.1-6 3.3V18h12v-1.7C22 14.1 18.2 13 16 13Zm-8 0c-.29 0-.62.02-.97.05C5.37 13.21 2 14.04 2 16.3V18h6v-1.7c0-1.22.64-2.3 1.72-3.12A7.73 7.73 0 0 0 8 13Z'
                fill='currentColor'
              />
            </svg>
          </button>
          {conversation.membershipRole === "OWNER" && (
            <button
              className={styles.iconButton}
              type='button'
              onClick={() => void onCreateInvite()}
              aria-label='Инвайт'
            >
              <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
                <path
                  d='M12 5a1 1 0 0 1 1 1v5h5a1 1 0 1 1 0 2h-5v5a1 1 0 1 1-2 0v-5H6a1 1 0 1 1 0-2h5V6a1 1 0 0 1 1-1Z'
                  fill='currentColor'
                />
              </svg>
            </button>
          )}
          <button
            className={styles.iconButton}
            type='button'
            onClick={() => void onRefreshConversation()}
            aria-label='Обновить'
            style={{ paddingBottom: "4px", fontSize: "1.2rem" }}
          >
            ↻
          </button>
        </div>
      </header>

      <div className={styles.columns}>
        <div className={styles.messagesPanel}>
          <MessagesList
            token={token}
            me={me}
            conversationId={conversation.id}
            messages={messages}
            loading={loading}
            onReply={(messageId) =>
              setReplyToMessageId(onReply(messageId)?.id ?? null)
            }
            onOpenPhotoViewer={onOpenPhotoViewer}
          />
          <MessageComposer
            members={members}
            replyTarget={replyTarget}
            onCancelReply={() => setReplyToMessageId(null)}
            onSend={async (payload) => {
              await onSendMessage({ ...payload, replyToMessageId })
              setReplyToMessageId(null)
            }}
          />
        </div>
      </div>

      {membersOpen && (
        <div
          className={styles.membersOverlay}
          onClick={() => setMembersOpen(false)}
        >
          <div
            className={styles.membersSheet}
            onClick={(event) => event.stopPropagation()}
          >
            <div className={styles.membersSheetHead}>
              <strong>Участники</strong>
              <button
                className={styles.closeButton}
                type='button'
                onClick={() => setMembersOpen(false)}
                aria-label='Закрыть'
              >
                ×
              </button>
            </div>
            <MembersPanel
              token={token}
              members={members}
              className={styles.membersPanelFloating}
            />
          </div>
        </div>
      )}
    </section>
  )
}
