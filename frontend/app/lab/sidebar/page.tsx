"use client";

import { Sidebar } from "@/components/Sidebar/Sidebar";
import type { AuthUser, ConversationSummary } from "@/types/api";

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

const noop = async () => {};

export default function SidebarLabPage() {
  return (
    <main
      style={{
        minHeight: "100vh",
        padding: "24px",
        background: "linear-gradient(180deg, #0a0f17 0%, #0f1722 100%)",
      }}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "540px",
          height: "calc(100vh - 48px)",
          borderRadius: "28px",
          overflow: "hidden",
          border: "1px solid rgba(255,255,255,0.08)",
          background: "rgba(21, 28, 39, 0.94)",
          boxShadow: "0 26px 70px rgba(0,0,0,0.42)",
        }}
      >
        <Sidebar
          token=""
          me={me}
          conversations={conversations}
          selectedConversationId={1}
          loading={false}
          drawerMode={null}
          drawerConversation={null}
          inlineDrawer
          onLogout={() => {}}
          onRefresh={noop}
          onSelectConversation={() => {}}
          onOpenCreate={() => {}}
          onOpenJoin={() => {}}
          onOpenProfile={() => {}}
          onOpenConversationSettings={() => {}}
          onCloseDrawer={() => {}}
          onCreateConversation={noop}
          onJoinConversation={noop}
          onUpdateProfile={noop}
          onUpdateConversation={noop}
          onDeleteConversation={noop}
        />
      </div>
    </main>
  );
}
