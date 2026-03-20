export type DrawerMode = "create" | "join" | "profile" | "conversation" | null;

export interface AuthUser {
  id: number;
  tenantKey: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  telegramLinked: boolean;
  telegramUserId: string | null;
  telegramUsername: string | null;
  online: boolean;
  lastSeenAt: string | null;
}

export interface ConversationSummary {
  id: number;
  tenantKey: string;
  title: string;
  avatarUrl: string | null;
  membershipRole: string;
  createdAt: string;
  lastMessagePreview: string | null;
  lastMessageCreatedAt: string | null;
  unreadCount: number;
  muted: boolean;
}

export interface ConversationReplyAttachment {
  kind: string;
  fileName: string;
}

export interface ConversationReply {
  id: number;
  authorUserId: number | null;
  authorDisplayName: string;
  body: string | null;
  attachments: ConversationReplyAttachment[];
  createdAt: string;
}

export interface ConversationAttachment {
  id: number;
  kind: string;
  fileName: string;
  mimeType: string | null;
  sizeBytes: number;
  contentUrl: string;
  cacheKey: string;
}

export interface ConversationMessage {
  id: number;
  conversationId: number;
  sourceTransport: string;
  authorUserId: number | null;
  authorExternalId: string | null;
  authorDisplayName: string;
  body: string | null;
  replyTo: ConversationReply | null;
  attachments: ConversationAttachment[];
  createdAt: string;
}

export interface ConversationMember {
  userId: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  role: string;
  joinedAt: string;
  telegramLinked: boolean;
  telegramUsername: string | null;
  online: boolean;
  lastSeenAt: string | null;
}

export interface ConversationInvite {
  id: number;
  conversationId: number;
  inviteCode: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface InviteAcceptance {
  conversationId: number;
  conversationTitle: string;
  role: string;
  alreadyMember: boolean;
  joinedAt: string;
}

export interface PendingAttachment {
  id: string;
  file: File;
  source: "picker" | "voice" | "video-note";
  sendAsVideoNote?: boolean;
}

export interface ToastMessage {
  id: number;
  kind: "success" | "error" | "info";
  message: string;
}

export interface PhotoViewerState {
  items: Array<{ src: string; fileName: string }>;
  activeIndex: number;
}
