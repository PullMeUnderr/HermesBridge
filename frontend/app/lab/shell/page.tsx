"use client";

import { AppShell } from "@/components/AppShell/AppShell";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationSummary,
  PendingAttachment,
} from "@/types/api";

const me: AuthUser = {
  id: 1,
  tenantKey: "demo",
  username: "pullmeunderrr",
  displayName: "Vladislav Fatushkin",
  avatarUrl: null,
  passwordLinked: true,
  telegramLinked: true,
  telegramUserId: "1",
  telegramUsername: "PullMEunderrr",
  online: false,
  lastSeenAt: "2026-03-19T16:07:00Z",
};

const conversations: ConversationSummary[] = [
  {
    id: 1,
    tenantKey: "demo",
    title: "Квартирник",
    avatarUrl: null,
    membershipRole: "OWNER",
    createdAt: "2026-03-19T11:29:00Z",
    lastMessagePreview: "дап дап",
    lastMessageCreatedAt: "2026-03-21T11:29:00Z",
    unreadCount: 0,
    hasUnreadMention: false,
    muted: false,
  },
  {
    id: 2,
    tenantKey: "demo",
    title: "100 гаражей",
    avatarUrl: null,
    membershipRole: "OWNER",
    createdAt: "2026-03-19T10:00:00Z",
    lastMessagePreview: "Видео",
    lastMessageCreatedAt: "2026-03-21T10:38:00Z",
    unreadCount: 0,
    hasUnreadMention: false,
    muted: false,
  },
  {
    id: 3,
    tenantKey: "demo",
    title: "Тест",
    avatarUrl: null,
    membershipRole: "OWNER",
    createdAt: "2026-03-19T08:00:00Z",
    lastMessagePreview: "доброй ночи",
    lastMessageCreatedAt: "2026-03-20T19:56:00Z",
    unreadCount: 0,
    hasUnreadMention: false,
    muted: false,
  },
];

const members: ConversationMember[] = [];
const messages: ConversationMessage[] = [];

const noop = async () => {};
const noopReply = () => null;

export default function ShellLabPage() {
  return (
    <AppShell
      token=""
      me={me}
      conversations={conversations}
      selectedConversation={null}
      drawerMode={null}
      drawerConversation={null}
      messages={messages}
      members={members}
      readReceipts={[]}
      typingNames={[]}
      loadingConversations={false}
      loadingConversationData={false}
      onLogout={() => {}}
      onRefreshConversations={noop}
      onSelectConversation={() => {}}
      onOpenDrawer={() => {}}
      onCloseDrawer={() => {}}
      onCreateConversation={noop}
      onJoinConversation={noop}
      onUpdateProfile={noop}
      onUpdateConversation={noop}
      onDeleteConversation={noop}
      onCreateInvite={noop}
      onRefreshCurrentConversation={noop}
      onSendMessage={async (_payload: {
        body: string;
        attachments: PendingAttachment[];
        replyToMessageId: number | null;
        sendAsVideoNote: boolean;
      }) => {}}
      onReply={noopReply}
      onTypingStateChange={() => {}}
      onOpenPhotoViewer={() => {}}
    />
  );
}
