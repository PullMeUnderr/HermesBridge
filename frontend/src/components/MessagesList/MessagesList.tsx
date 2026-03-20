"use client";

import styles from "./MessagesList.module.scss";
import { Avatar } from "@/components/ui/Avatar";
import { useProtectedObjectUrl } from "@/hooks/useProtectedObjectUrl";
import { fetchProtectedBlobUrl } from "@/lib/api";
import { formatBytes, formatClock, isAudioAttachment, isImageAttachment, isVideoAttachment } from "@/lib/format";
import type { AuthUser, ConversationAttachment, ConversationMessage } from "@/types/api";

interface MessagesListProps {
  token: string;
  me: AuthUser;
  messages: ConversationMessage[];
  loading: boolean;
  onReply: (messageId: number) => void;
  onOpenPhotoViewer: (items: Array<{ src: string; fileName: string }>, activeIndex: number) => void;
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
  const resolvedSrc = useProtectedObjectUrl(token, attachment.contentUrl);

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
      <button
        type="button"
        className={styles.mediaButton}
        onClick={() => onOpenPhotoViewer({ src: resolvedSrc, fileName: attachment.fileName })}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img alt={attachment.fileName} src={resolvedSrc} className={styles.image} />
      </button>
    );
  }

  if (resolvedSrc && isVideoAttachment(attachment.mimeType, attachment.kind)) {
    return <video className={styles.video} src={resolvedSrc} controls playsInline preload="metadata" />;
  }

  if (resolvedSrc && isAudioAttachment(attachment.mimeType, attachment.kind)) {
    return <audio className={styles.audio} src={resolvedSrc} controls preload="metadata" />;
  }

  return (
    <button type="button" className={styles.document} onClick={() => void handleDownload()}>
      <strong>{attachment.fileName}</strong>
      <span>{formatBytes(attachment.sizeBytes)}</span>
    </button>
  );
}

export function MessagesList({ token, me, messages, loading, onReply, onOpenPhotoViewer }: MessagesListProps) {
  if (!loading && messages.length === 0) {
    return (
      <div className={styles.empty}>
        <strong>Пока без сообщений</strong>
        <span>Отправь первое сообщение, файл, голосовое или кружок.</span>
      </div>
    );
  }

  return (
    <div className={styles.list}>
      {messages.map((message) => {
        const own = message.authorUserId === me.id;
        const imageItems = message.attachments.filter((attachment) => isImageAttachment(attachment.mimeType, attachment.kind));
        return (
          <article key={message.id} className={`${styles.message} ${own ? styles.own : ""}`}>
            <Avatar token={token} name={message.authorDisplayName} size="sm" />
            <div className={styles.bubble}>
              <div className={styles.messageHead}>
                <strong>{message.authorDisplayName}</strong>
                <span>{formatClock(message.createdAt)}</span>
              </div>

              {message.replyTo && (
                <div className={styles.reply}>
                  <strong>{message.replyTo.authorDisplayName}</strong>
                  <span>{message.replyTo.body || "Ответ с вложением"}</span>
                </div>
              )}

              {message.body && <p className={styles.body}>{message.body}</p>}

              {message.attachments.length > 0 && (
                <div className={styles.attachments}>
                  {message.attachments.map((attachment, index) => (
                    <AttachmentCard
                      key={attachment.id}
                      token={token}
                      attachment={attachment}
                      onOpenPhotoViewer={(item) => onOpenPhotoViewer([item], 0)}
                    />
                  ))}
                </div>
              )}

              <button className={styles.replyButton} type="button" onClick={() => onReply(message.id)}>
                Ответить
              </button>
            </div>
          </article>
        );
      })}
    </div>
  );
}
