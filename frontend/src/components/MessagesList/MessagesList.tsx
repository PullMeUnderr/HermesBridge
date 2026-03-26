"use client";

import { useEffect } from "react";
import { useRef, useState } from "react";
import styles from "./MessagesList.module.scss";
import { useLazyVisible } from "@/hooks/useLazyVisible";
import { Avatar } from "@/components/ui/Avatar";
import { useProtectedObjectUrl } from "@/hooks/useProtectedObjectUrl";
import { fetchProtectedBlobUrl } from "@/lib/api";
import {
  formatBytes,
  formatClock,
  formatTimestamp,
  isAudioAttachment,
  isImageAttachment,
  isVideoAttachment,
  isVideoNoteAttachment,
} from "@/lib/format";
import { renderRichText } from "@/lib/richText";
import type {
  AuthUser,
  ConversationAttachment,
  ConversationMessage,
  ConversationReadPayload,
} from "@/types/api";

interface MessagesListProps {
  token: string;
  me: AuthUser;
  conversationId: number;
  conversationTitle: string;
  conversationAvatarUrl?: string | null;
  messages: ConversationMessage[];
  currentUserLastReadAt: string | null;
  readReceipts: ConversationReadPayload[];
  loading: boolean;
  emptyStateVariant?: "chat" | "channel";
  onReply: (messageId: number) => void;
  onOpenPhotoViewer: (items: Array<{ src: string; fileName: string }>, activeIndex: number) => void;
}

function normalizeIdentity(value: string | null | undefined) {
  return String(value ?? "").trim().toLowerCase();
}

function isOwnMessage(message: ConversationMessage, me: AuthUser) {
  if (message.authorUserId !== null && message.authorUserId === me.id) {
    return true;
  }

  const authorName = normalizeIdentity(message.authorDisplayName);
  return authorName !== "" && [normalizeIdentity(me.displayName), normalizeIdentity(me.username)].includes(authorName);
}

function messageMentionsUser(message: ConversationMessage, username: string) {
  const body = String(message.body ?? "");
  if (!body || !username) {
    return false;
  }

  const mentionPattern = /(^|[^A-Za-z0-9_])@([A-Za-z0-9_]{1,100})/g;
  for (const match of body.matchAll(mentionPattern)) {
    const mentionedUsername = (match[2] ?? "").toLowerCase();
    if (mentionedUsername === username.toLowerCase()) {
      return true;
    }
  }

  return false;
}

function VideoNoteCard({ token, attachment }: { token: string; attachment: ConversationAttachment }) {
  const { ref } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, true, attachment);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [playing, setPlaying] = useState(false);

  async function handleDownload() {
    const objectUrl = await fetchProtectedBlobUrl(token, attachment.contentUrl);
    if (!objectUrl) {
      return;
    }
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = attachment.fileName;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  async function togglePlayback() {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    if (video.paused || video.ended) {
      await video.play();
      return;
    }

    video.pause();
  }

  return (
    <div ref={ref} className={styles.videoNoteCard}>
      <button
        type="button"
        className={styles.videoNoteShell}
        onClick={() => void togglePlayback()}
        aria-label={playing ? "Остановить кружок" : "Запустить кружок"}
      >
        {resolvedSrc ? (
          <video
            ref={videoRef}
            className={styles.videoNote}
            src={resolvedSrc}
            playsInline
            preload="metadata"
            onPlay={() => setPlaying(true)}
            onPause={() => setPlaying(false)}
            onEnded={() => setPlaying(false)}
          />
        ) : (
          <div className={styles.videoNoteFallback} />
        )}
      </button>
      <div className={styles.videoNoteCaption}>
        <span>{attachment.fileName}</span>
        <button type="button" className={styles.downloadButton} onClick={() => void handleDownload()}>
          Скачать
        </button>
      </div>
    </div>
  );
}

function formatAudioTime(value: number) {
  if (!Number.isFinite(value) || value < 0) {
    return "00:00";
  }

  const totalSeconds = Math.floor(value);
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function VoiceCard({ token, attachment }: { token: string; attachment: ConversationAttachment }) {
  const { ref } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, true, attachment);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  async function handleDownload() {
    const objectUrl = await fetchProtectedBlobUrl(token, attachment.contentUrl);
    if (!objectUrl) {
      return;
    }
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = attachment.fileName;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  async function togglePlayback() {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }

    if (audio.paused || audio.ended) {
      await audio.play();
      return;
    }

    audio.pause();
  }

  return (
    <div ref={ref} className={styles.voiceCard}>
      <button type="button" className={styles.voiceToggle} onClick={() => void togglePlayback()}>
        <span className={styles.voiceToggleIcon} aria-hidden="true">
          {playing ? "||" : "▶"}
        </span>
      </button>
      <div className={styles.voiceMeta}>
        <strong>Голосовое</strong>
        <span>{attachment.fileName}</span>
      </div>
      <div className={styles.voiceTimeline}>
        <div className={styles.voiceProgressTrack}>
          <div
            className={styles.voiceProgressValue}
            style={{ width: `${duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0}%` }}
          />
        </div>
        <span>{playing ? `${formatAudioTime(currentTime)} / ${formatAudioTime(duration)}` : formatAudioTime(duration)}</span>
      </div>
      {resolvedSrc && (
        <audio
          ref={audioRef}
          className={styles.voiceAudio}
          src={resolvedSrc}
          preload="metadata"
          onLoadedMetadata={(event) => setDuration(event.currentTarget.duration)}
          onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => {
            setPlaying(false);
            setCurrentTime(0);
          }}
        />
      )}
      <button type="button" className={styles.downloadButton} onClick={() => void handleDownload()}>
        Скачать
      </button>
    </div>
  );
}

function AttachmentCard({
  token,
  attachment,
  onOpenPhotoViewer,
}: {
  token: string;
  attachment: ConversationAttachment;
  onOpenPhotoViewer: (item: { src: string; fileName: string }) => void;
}) {
  const { ref, visible } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, true, attachment);
  const treatAsMedia =
    isImageAttachment(attachment.mimeType, attachment.kind, attachment.fileName) ||
    isVideoNoteAttachment(attachment.kind) ||
    isVideoAttachment(attachment.mimeType, attachment.kind, attachment.fileName) ||
    isAudioAttachment(attachment.mimeType, attachment.kind, attachment.fileName);

  if (!resolvedSrc && treatAsMedia) {
    return (
      <div ref={ref}>
        <div className={styles.mediaLoading}>
          <span className={styles.mediaLoadingSpinner} />
          <strong>{attachment.fileName}</strong>
          <span>Загружаем предпросмотр...</span>
        </div>
      </div>
    );
  }

  async function handleDownload() {
    const objectUrl = await fetchProtectedBlobUrl(token, attachment.contentUrl);
    if (!objectUrl) {
      return;
    }
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = attachment.fileName;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
  }

  if (resolvedSrc && isImageAttachment(attachment.mimeType, attachment.kind, attachment.fileName)) {
    return (
      <div ref={ref}>
        <button
          type="button"
          className={styles.mediaButton}
          onClick={() => onOpenPhotoViewer({ src: resolvedSrc, fileName: attachment.fileName })}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img alt={attachment.fileName} src={resolvedSrc} className={styles.image} loading="lazy" />
        </button>
      </div>
    );
  }

  if (resolvedSrc && isVideoNoteAttachment(attachment.kind)) {
    return <VideoNoteCard token={token} attachment={attachment} />;
  }

  if (resolvedSrc && isVideoAttachment(attachment.mimeType, attachment.kind, attachment.fileName)) {
    return (
      <div ref={ref}>
        <video className={styles.video} src={resolvedSrc} controls playsInline preload="metadata" />
      </div>
    );
  }

  if (resolvedSrc && isAudioAttachment(attachment.mimeType, attachment.kind, attachment.fileName)) {
    return <VoiceCard token={token} attachment={attachment} />;
  }

  return (
    <div ref={ref}>
      <button type="button" className={styles.document} onClick={() => void handleDownload()}>
        <strong>{attachment.fileName}</strong>
        <span>{formatBytes(attachment.sizeBytes)}</span>
      </button>
    </div>
  );
}

export function MessagesList({
  token,
  me,
  conversationId,
  conversationTitle,
  conversationAvatarUrl,
  messages,
  currentUserLastReadAt,
  readReceipts,
  loading,
  emptyStateVariant = "chat",
  onReply,
  onOpenPhotoViewer,
}: MessagesListProps) {
  const listRef = useRef<HTMLDivElement | null>(null);
  const wasNearBottomRef = useRef(true);
  const previousConversationIdRef = useRef<number | null>(null);
  const [readDetails, setReadDetails] = useState<{
    messageId: number;
    readers: ConversationReadPayload[];
    anchorTop: number;
    anchorLeft: number;
  } | null>(null);

  useEffect(() => {
    const list = listRef.current;
    if (!list) {
      return;
    }

    const handleScroll = () => {
      const remaining = list.scrollHeight - list.scrollTop - list.clientHeight;
      wasNearBottomRef.current = remaining < 72;
    };

    handleScroll();
    list.addEventListener("scroll", handleScroll);
    return () => list.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    const list = listRef.current;
    if (!list) {
      return;
    }

    requestAnimationFrame(() => {
      const conversationChanged = previousConversationIdRef.current !== conversationId;
      if (conversationChanged || wasNearBottomRef.current) {
        list.scrollTop = list.scrollHeight;
      }
      previousConversationIdRef.current = conversationId;
    });
  }, [conversationId, messages]);

  if (!loading && messages.length === 0) {
    return (
      <div className={styles.empty}>
        <strong>Пока без сообщений</strong>
        <span>
          {emptyStateVariant === "channel"
            ? "Постов после подключения пока не было."
            : "Отправь первое сообщение, файл, голосовое или кружок."}
        </span>
      </div>
    );
  }

  return (
    <div ref={listRef} className={styles.list}>
      {messages.map((message) => {
        const own = isOwnMessage(message, me);
        const transportLabel = message.sourceTransport === "TELEGRAM" ? "TELEGRAM" : "HERMES";
        const mediaOnly = !message.body && Boolean(message.attachments.length);
        const isUnreadMention =
          !own &&
          messageMentionsUser(message, me.username) &&
          (currentUserLastReadAt == null || message.createdAt > currentUserLastReadAt);
        const readers = own
          ? readReceipts.filter((receipt) => receipt.userId !== me.id && receipt.readAt >= message.createdAt)
          : [];
        const isRead = own && readers.length > 0;
        const statusLabel = own ? (isRead ? "Прочитано" : "Отправлено") : null;
        return (
          <article
            key={message.id}
            className={`${styles.message} ${own ? styles.own : ""} ${mediaOnly ? styles.mediaOnlyRow : ""} ${isUnreadMention ? styles.mentionHighlightedMessage : ""}`}
          >
            <Avatar
              className={styles.avatarSlot}
              token={token}
              name={
                !own && message.sourceTransport === "TELEGRAM" && !message.authorUserId
                  ? conversationTitle
                  : message.authorDisplayName
              }
              src={
                own
                  ? me.avatarUrl
                  : message.authorUserId
                    ? `/api/auth/users/${message.authorUserId}/avatar`
                    : message.sourceTransport === "TELEGRAM"
                      ? conversationAvatarUrl
                      : undefined
              }
              size="sm"
            />
            <div className={`${styles.bubble} ${mediaOnly ? styles.mediaOnlyBubble : ""}`}>
              <div className={styles.messageHead}>
                {!own && <strong className={styles.author}>{message.authorDisplayName}</strong>}
                <button className={styles.replyButton} type="button" onClick={() => onReply(message.id)} aria-label="Ответить">
                  ↩
                </button>
                <span className={styles.transportPill}>{transportLabel}</span>
              </div>

              {message.replyTo && (
                <div className={styles.reply}>
                  <strong>{message.replyTo.authorDisplayName}</strong>
                  <span>
                    {message.replyTo.body
                      ? renderRichText(message.replyTo.body, me.username, {
                          link: styles.link,
                          mention: styles.mention,
                          mentionSelf: styles.mentionSelf,
                        })
                      : "Ответ с вложением"}
                  </span>
                </div>
              )}

              {message.body && (
                <div className={styles.body}>
                  {renderRichText(message.body, me.username, {
                    link: styles.link,
                    mention: styles.mention,
                    mentionSelf: styles.mentionSelf,
                  })}
                </div>
              )}

              {message.attachments.length > 0 && (
                <div className={styles.attachments}>
                  {message.attachments.map((attachment) => (
                    <AttachmentCard
                      key={attachment.id}
                      token={token}
                      attachment={attachment}
                      onOpenPhotoViewer={(item) => onOpenPhotoViewer([item], 0)}
                    />
                  ))}
                </div>
              )}

              <span className={`${styles.timestamp} ${message.attachments.length > 0 ? styles.timestampOnMedia : ""}`}>
                {formatClock(message.createdAt)}
                {own && (
                  isRead ? (
                    <button
                      type="button"
                      className={`${styles.deliveryStatus} ${styles.deliveryStatusButton} ${styles.deliveryStatusRead}`}
                      aria-label="Открыть информацию о прочтении"
                      onClick={(event) => {
                        event.stopPropagation();
                        const rect = event.currentTarget.getBoundingClientRect();
                        setReadDetails({
                          messageId: message.id,
                          readers,
                          anchorTop: rect.top - 8,
                          anchorLeft: rect.left - 252,
                        });
                      }}
                    >
                      ✓✓
                    </button>
                  ) : (
                    <span
                      className={styles.deliveryStatus}
                      aria-label={statusLabel ?? undefined}
                      title={statusLabel ?? undefined}
                    >
                      ✓
                    </span>
                  )
                )}
              </span>
            </div>
          </article>
        );
      })}

      {readDetails && (
        <div className={styles.readDetailsOverlay} onClick={() => setReadDetails(null)}>
          <div
            className={styles.readDetailsSheet}
            role="dialog"
            aria-modal="true"
            aria-label="Информация о прочтении"
            style={{
              top: `${Math.max(12, readDetails.anchorTop)}px`,
              left: `${Math.max(12, readDetails.anchorLeft)}px`,
            }}
            onClick={(event) => event.stopPropagation()}
          >
            <div className={styles.readDetailsHead}>
              <strong>Прочитали сообщение</strong>
              <button
                type="button"
                className={styles.readDetailsClose}
                onClick={() => setReadDetails(null)}
                aria-label="Закрыть"
              >
                ×
              </button>
            </div>
            <div className={styles.readDetailsList}>
              {readDetails.readers.map((reader) => (
                <div key={`${readDetails.messageId}-${reader.userId}`} className={styles.readDetailsRow}>
                  <strong>{reader.displayName}</strong>
                  <span>{formatTimestamp(reader.readAt)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
