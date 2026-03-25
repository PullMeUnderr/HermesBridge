"use client";

import { useCallback, useEffect, useState } from "react";
import styles from "./AppShell.module.scss";
import { Sidebar } from "@/components/Sidebar/Sidebar";
import { DrawerPanel } from "@/components/DrawerPanel/DrawerPanel";
import { ConversationView } from "@/components/ConversationView/ConversationView";
import type {
  AuthUser,
  ConversationMember,
  ConversationMessage,
  ConversationReadPayload,
  ConversationSummary,
  DrawerMode,
  PendingAttachment,
  TdlightAvailableChannel,
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
  readReceipts: ConversationReadPayload[];
  typingNames: string[];
  loadingConversations: boolean;
  loadingConversationData: boolean;
  availableChannels: TdlightAvailableChannel[];
  loadingAvailableChannels: boolean;
  onLogout: () => void;
  onRefreshConversations: () => Promise<void>;
  onRefreshAvailableChannels: () => Promise<void>;
  onSubscribeChannel: (channel: TdlightAvailableChannel) => Promise<void>;
  onSubscribeChannels: (channels: TdlightAvailableChannel[]) => Promise<void>;
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
  onRefreshSessionState: () => Promise<void>;
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
  onTypingStateChange: (conversationId: number, active: boolean) => void;
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
    readReceipts,
    typingNames,
    loadingConversations,
    loadingConversationData,
    availableChannels,
    loadingAvailableChannels,
    onLogout,
    onRefreshConversations,
    onRefreshAvailableChannels,
    onSubscribeChannel,
    onSubscribeChannels,
    onSelectConversation,
    onOpenDrawer,
    onCloseDrawer,
    onCreateConversation,
    onJoinConversation,
    onUpdateProfile,
    onRefreshSessionState,
    onUpdateConversation,
    onDeleteConversation,
    onCreateInvite,
    onRefreshCurrentConversation,
    onSendMessage,
    onReply,
    onTypingStateChange,
    onOpenPhotoViewer,
  } = props;
  const [mobileScreen, setMobileScreen] = useState<"sidebar" | "conversation">("sidebar");
  const [mobileViewport, setMobileViewport] = useState(false);
  const profileOverlayOpen = drawerMode === "profile";
  const inlineDrawerEnabled = !mobileViewport && drawerMode !== "profile";
  const handleTypingStateChange = useCallback(
    (active: boolean) => {
      if (!selectedConversation) {
        return;
      }
      onTypingStateChange(selectedConversation.id, active);
    },
    [onTypingStateChange, selectedConversation],
  );

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
          inlineDrawer={inlineDrawerEnabled}
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
          onRefreshSessionState={onRefreshSessionState}
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
            readReceipts={readReceipts}
            typingNames={typingNames}
            loading={loadingConversationData}
            onOpenSettings={() => selectedConversation && onOpenDrawer("conversation", selectedConversation.id)}
            onCreateInvite={onCreateInvite}
            onRefreshConversation={onRefreshCurrentConversation}
            onSendMessage={onSendMessage}
            onReply={onReply}
            onTypingStateChange={handleTypingStateChange}
            onOpenPhotoViewer={onOpenPhotoViewer}
            onBackToSidebar={mobileViewport ? () => setMobileScreen("sidebar") : undefined}
          />
        </main>
      </div>

      {(mobileViewport || profileOverlayOpen) && (
        <DrawerPanel
          token={token}
          open={drawerMode !== null}
          mode={drawerMode}
          me={me}
          conversation={drawerConversation}
          onClose={onCloseDrawer}
          onCreateConversation={onCreateConversation}
          onJoinConversation={onJoinConversation}
          onUpdateProfile={onUpdateProfile}
          onRefreshSessionState={onRefreshSessionState}
          availableChannels={availableChannels}
          loadingAvailableChannels={loadingAvailableChannels}
          onRefreshAvailableChannels={onRefreshAvailableChannels}
          onSubscribeChannel={onSubscribeChannel}
          onSubscribeChannels={onSubscribeChannels}
          onUpdateConversation={onUpdateConversation}
          onDeleteConversation={onDeleteConversation}
        />
      )}
    </div>
  );
}
