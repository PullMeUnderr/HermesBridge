"use client";

import { Fragment, useEffect } from "react";
import type { ReactNode } from "react";
import { useRef, useState } from "react";
import styles from "./MessagesList.module.scss";
import { useLazyVisible } from "@/hooks/useLazyVisible";
import { Avatar } from "@/components/ui/Avatar";
import { useProtectedObjectUrl } from "@/hooks/useProtectedObjectUrl";
import { fetchProtectedBlobUrl } from "@/lib/api";
import {
  formatBytes,
  formatClock,
  isAudioAttachment,
  isImageAttachment,
  isVideoAttachment,
  isVideoNoteAttachment,
} from "@/lib/format";
import type { AuthUser, ConversationAttachment, ConversationMessage } from "@/types/api";

interface MessagesListProps {
  token: string;
  me: AuthUser;
  conversationId: number;
  messages: ConversationMessage[];
  loading: boolean;
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

function VideoNoteCard({ token, attachment }: { token: string; attachment: ConversationAttachment }) {
  const { ref, visible } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, visible);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [posterSrc, setPosterSrc] = useState<string | null>(null);

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

  function capturePoster() {
    const video = videoRef.current;
    if (!video || posterSrc) {
      return;
    }

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth || 480;
    canvas.height = video.videoHeight || 480;
    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }

    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    setPosterSrc(canvas.toDataURL("image/jpeg", 0.82));
  }

  return (
    <div ref={ref} className={styles.videoNoteCard}>
      <button type="button" className={`${styles.videoNoteShell} ${playing ? styles.videoNoteShellPlaying : ""}`} onClick={() => void togglePlayback()}>
        {resolvedSrc ? (
          <>
            {posterSrc && !playing && (
              // eslint-disable-next-line @next/next/no-img-element
              <img alt={attachment.fileName} src={posterSrc} className={styles.videoNotePoster} />
            )}
            <video
              ref={videoRef}
              className={`${styles.videoNote} ${posterSrc && !playing ? styles.videoNoteHidden : ""}`}
              src={resolvedSrc}
              playsInline
              preload="metadata"
              onLoadedData={(event) => {
                event.currentTarget.currentTime = 0.1;
              }}
              onSeeked={() => capturePoster()}
              onPlay={() => setPlaying(true)}
              onPause={() => setPlaying(false)}
              onEnded={() => setPlaying(false)}
            />
          </>
        ) : (
          <div className={styles.videoNoteFallback} />
        )}
        <span className={styles.videoNoteToggle}>{playing ? "||" : ">"}</span>
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
  const { ref, visible } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, visible);
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
        {playing ? "||" : ">"}
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
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl, visible);

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

  if (resolvedSrc && isImageAttachment(attachment.mimeType, attachment.kind)) {
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

  if (resolvedSrc && isVideoAttachment(attachment.mimeType, attachment.kind)) {
    return (
      <div ref={ref}>
        <video className={styles.video} src={resolvedSrc} controls playsInline preload="metadata" />
      </div>
    );
  }

  if (resolvedSrc && isAudioAttachment(attachment.mimeType, attachment.kind)) {
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

function formatLinkLabel(url: string) {
  try {
    return decodeURI(url);
  } catch {
    return url;
  }
}

function renderMentionText(text: string, ownUsername: string | null) {
  const mentionPattern = /(^|[^A-Za-z0-9_])@([A-Za-z0-9_]{1,100})/g;
  const parts: ReactNode[] = [];
  let cursor = 0;
  let index = 0;

  for (const match of text.matchAll(mentionPattern)) {
    const prefix = match[1] ?? "";
    const username = match[2] ?? "";
    const startIndex = match.index ?? 0;
    const mentionStart = startIndex + prefix.length;
    const mentionEnd = mentionStart + username.length + 1;
    const isSelf = ownUsername && username.toLowerCase() === ownUsername.toLowerCase();

    if (startIndex > cursor) {
      parts.push(text.slice(cursor, startIndex));
    }

    parts.push(prefix);
    parts.push(
      <span key={`mention-${index++}`} className={`${styles.mention} ${isSelf ? styles.mentionSelf : ""}`}>
        @{username}
      </span>,
    );
    cursor = mentionEnd;
  }

  if (cursor < text.length) {
    parts.push(text.slice(cursor));
  }

  return parts;
}

function renderRichMessageText(body: string, ownUsername: string | null) {
  const urlPattern = /(https?:\/\/[^\s<]+|file:\/\/[^\s<]+|tg:\/\/[^\s<]+|mailto:[^\s<]+)/gi;
  const lines = String(body).split("\n");

  return lines
    .map((line, lineIndex) => {
      const parts: ReactNode[] = [];
      let cursor = 0;
      let index = 0;

      for (const match of line.matchAll(urlPattern)) {
        const matchedUrl = match[0];
        const startIndex = match.index ?? 0;
        const endIndex = startIndex + matchedUrl.length;

        if (startIndex > cursor) {
          parts.push(...renderMentionText(line.slice(cursor, startIndex), ownUsername));
        }

        const href = matchedUrl.trim();
        const external = !href.toLowerCase().startsWith("file://");
        parts.push(
          <a
            key={`link-${lineIndex}-${index++}`}
            className={styles.link}
            href={href}
            target={external ? "_blank" : undefined}
            rel={external ? "noopener noreferrer" : undefined}
          >
            {formatLinkLabel(href)}
          </a>,
        );
        cursor = endIndex;
      }

      if (cursor < line.length) {
        parts.push(...renderMentionText(line.slice(cursor), ownUsername));
      }

      return (
        <Fragment key={`line-${lineIndex}`}>
          {parts.length > 0 ? parts : line}
          {lineIndex < lines.length - 1 && <br />}
        </Fragment>
      );
    });
}

export function MessagesList({ token, me, conversationId, messages, loading, onReply, onOpenPhotoViewer }: MessagesListProps) {
  const listRef = useRef<HTMLDivElement | null>(null);
  const wasNearBottomRef = useRef(true);
  const previousConversationIdRef = useRef<number | null>(null);

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
        <span>Отправь первое сообщение, файл, голосовое или кружок.</span>
      </div>
    );
  }

  return (
    <div ref={listRef} className={styles.list}>
      {messages.map((message) => {
        const own = isOwnMessage(message, me);
        const transportLabel = message.sourceTransport === "TELEGRAM" ? "TELEGRAM" : "HERMES";
        const mediaOnly = !message.body && Boolean(message.attachments.length);
        return (
          <article
            key={message.id}
            className={`${styles.message} ${own ? styles.own : ""} ${mediaOnly ? styles.mediaOnlyRow : ""}`}
          >
            <Avatar
              token={token}
              name={message.authorDisplayName}
              src={message.authorUserId && !own ? `/api/auth/users/${message.authorUserId}/avatar` : undefined}
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
                  <span>{message.replyTo.body || "Ответ с вложением"}</span>
                </div>
              )}

              {message.body && <div className={styles.body}>{renderRichMessageText(message.body, me.username)}</div>}

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
              </span>
            </div>
          </article>
        );
      })}
    </div>
  );
}
