"use client";

import { useEffect, useState } from "react";
import styles from "./AppShell.module.scss";
import { Sidebar } from "@/components/Sidebar/Sidebar";
import { DrawerPanel } from "@/components/DrawerPanel/DrawerPanel";
import { ConversationView } from "@/components/ConversationView/ConversationView";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationSummary,
  DrawerMode,
  PendingAttachment,
} from "@/types/api";

interface AppShellProps {
  token: string;
  me: AuthUser;
  conversations: ConversationSummary[];
  selectedConversation: ConversationSummary | null;
  drawerMode: DrawerMode;
  drawerConversation: ConversationSummary | null;
  messages: ConversationMessage[];
  members: ConversationMember[];
  loadingConversations: boolean;
  loadingConversationData: boolean;
  onLogout: () => void;
  onRefreshConversations: () => Promise<void>;
  onSelectConversation: (conversationId: number) => void;
  onOpenDrawer: (mode: Exclude<DrawerMode, null>, conversationId?: number | null) => void;
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
  onCreateInvite: () => Promise<void>;
  onRefreshCurrentConversation: () => Promise<void>;
  onSendMessage: (payload: {
    body: string;
    attachments: PendingAttachment[];
    replyToMessageId: number | null;
    sendAsVideoNote: boolean;
  }) => Promise<void>;
  onReply: (messageId: number) => ConversationMessage | null;
  onOpenPhotoViewer: (items: Array<{ src: string; fileName: string }>, activeIndex: number) => void;
}

export function AppShell(props: AppShellProps) {
  const {
    token,
    me,
    conversations,
    selectedConversation,
    drawerMode,
    drawerConversation,
    messages,
    members,
    loadingConversations,
    loadingConversationData,
    onLogout,
    onRefreshConversations,
    onSelectConversation,
    onOpenDrawer,
    onCloseDrawer,
    onCreateConversation,
    onJoinConversation,
    onUpdateProfile,
    onUpdateConversation,
    onDeleteConversation,
    onCreateInvite,
    onRefreshCurrentConversation,
    onSendMessage,
    onReply,
    onOpenPhotoViewer,
  } = props;
  const [mobileScreen, setMobileScreen] = useState<"sidebar" | "conversation">("sidebar");
  const [mobileViewport, setMobileViewport] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const media = window.matchMedia("(max-width: 1100px)");
    const sync = () => {
      const isMobile = media.matches;
      setMobileViewport(isMobile);
      if (!isMobile) {
        return;
      }
      setMobileScreen(selectedConversation ? "conversation" : "sidebar");
    };

    sync();
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, [selectedConversation]);

  return (
    <div className={styles.page}>
      <div className={styles.ambientOne} />
      <div className={styles.ambientTwo} />
      <div className={styles.ambientThree} />

      <div className={styles.layout} data-mobile-screen={mobileScreen}>
        <Sidebar
          token={token}
          me={me}
          conversations={conversations}
          selectedConversationId={selectedConversation?.id ?? null}
          loading={loadingConversations}
          drawerMode={drawerMode}
          drawerConversation={drawerConversation}
          inlineDrawer={!mobileViewport}
          onLogout={onLogout}
          onRefresh={onRefreshConversations}
          onSelectConversation={(conversationId) => {
            onCloseDrawer();
            onSelectConversation(conversationId);
            if (mobileViewport) {
              setMobileScreen("conversation");
            }
          }}
          onOpenCreate={() => onOpenDrawer("create")}
          onOpenJoin={() => onOpenDrawer("join")}
          onOpenProfile={() => onOpenDrawer("profile")}
          onOpenConversationSettings={(conversationId) => onOpenDrawer("conversation", conversationId)}
          onCloseDrawer={onCloseDrawer}
          onCreateConversation={onCreateConversation}
          onJoinConversation={onJoinConversation}
          onUpdateProfile={onUpdateProfile}
          onUpdateConversation={onUpdateConversation}
          onDeleteConversation={onDeleteConversation}
        />

        <main className={styles.workspace}>
          <ConversationView
            token={token}
            me={me}
            conversation={selectedConversation}
            messages={messages}
            members={members}
            loading={loadingConversationData}
            onOpenSettings={() => selectedConversation && onOpenDrawer("conversation", selectedConversation.id)}
            onCreateInvite={onCreateInvite}
            onRefreshConversation={onRefreshCurrentConversation}
            onSendMessage={onSendMessage}
            onReply={onReply}
            onOpenPhotoViewer={onOpenPhotoViewer}
            onBackToSidebar={mobileViewport ? () => setMobileScreen("sidebar") : undefined}
          />
        </main>
      </div>

      {mobileViewport && (
        <DrawerPanel
          open={drawerMode !== null}
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
      )}
    </div>
  );
}
