"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import styles from "./MessageComposer.module.scss";
import type { ConversationMember, ConversationMessage, PendingAttachment } from "@/types/api";

interface MessageComposerProps {
  members: ConversationMember[];
  replyTarget: ConversationMessage | null;
  onCancelReply: () => void;
  onSend: (payload: {
    body: string;
    attachments: PendingAttachment[];
    sendAsVideoNote: boolean;
  }) => Promise<void>;
}

export function MessageComposer({ members, replyTarget, onCancelReply, onSend }: MessageComposerProps) {
  const [body, setBody] = useState("");
  const [attachments, setAttachments] = useState<PendingAttachment[]>([]);
  const [sendAsVideoNote, setSendAsVideoNote] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [recordingVoice, setRecordingVoice] = useState(false);
  const [recordingVideo, setRecordingVideo] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [mentionIndex, setMentionIndex] = useState(0);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const videoRecorderRef = useRef<MediaRecorder | null>(null);
  const videoStreamRef = useRef<MediaStream | null>(null);
  const videoPreviewRef = useRef<HTMLVideoElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  const mentionState = useMemo(() => {
    const textarea = textareaRef.current;
    const cursor = textarea?.selectionStart ?? body.length;
    const beforeCursor = body.slice(0, cursor);
    const match = beforeCursor.match(/(?:^|[\s(])@([A-Za-z0-9_]*)$/);
    if (!match) {
      return { visible: false, suggestions: [], range: null as { start: number; end: number } | null };
    }

    const query = match[1].toLowerCase();
    const suggestions = members
      .filter((member) => member.username.toLowerCase().includes(query) || member.displayName.toLowerCase().includes(query))
      .slice(0, 6);

    return {
      visible: suggestions.length > 0,
      suggestions,
      range: {
        start: cursor - query.length - 1,
        end: cursor,
      },
    };
  }, [body, members]);

  useEffect(() => {
    if (!cameraOpen || !videoPreviewRef.current || !videoStreamRef.current) {
      return;
    }
    videoPreviewRef.current.srcObject = videoStreamRef.current;
  }, [cameraOpen]);

  useEffect(() => {
    return () => {
      mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
      videoStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  function appendFiles(files: File[], source: PendingAttachment["source"], forceVideoNote = false) {
    setAttachments((current) => [
      ...current,
      ...files.map((file, index) => ({
        id: `${source}-${file.name}-${file.size}-${Date.now()}-${index}`,
        file,
        source,
        sendAsVideoNote: forceVideoNote,
      })),
    ]);
    if (!forceVideoNote && files.length !== 1) {
      setSendAsVideoNote(false);
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    appendFiles(files, "picker");
    event.target.value = "";
  }

  async function toggleVoiceRecording() {
    if (recordingVoice) {
      mediaRecorderRef.current?.stop();
      setRecordingVoice(false);
      return;
    }

    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    mediaStreamRef.current = stream;
    const chunks: Blob[] = [];
    const recorder = new MediaRecorder(stream);
    mediaRecorderRef.current = recorder;
    recorder.addEventListener("dataavailable", (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    });
    recorder.addEventListener("stop", () => {
      const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
      const file = new File([blob], `voice-${Date.now()}.webm`, { type: blob.type });
      appendFiles([file], "voice");
      stream.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    });
    recorder.start();
    setRecordingVoice(true);
  }

  async function openCamera() {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user" },
      audio: true,
    });
    videoStreamRef.current?.getTracks().forEach((track) => track.stop());
    videoStreamRef.current = stream;
    setCameraOpen(true);
    if (videoPreviewRef.current) {
      videoPreviewRef.current.srcObject = stream;
    }
  }

  async function toggleVideoRecording() {
    if (recordingVideo) {
      videoRecorderRef.current?.stop();
      setRecordingVideo(false);
      return;
    }

    if (!videoStreamRef.current) {
      await openCamera();
    }

    if (!videoStreamRef.current) {
      return;
    }

    const chunks: Blob[] = [];
    const recorder = new MediaRecorder(videoStreamRef.current);
    videoRecorderRef.current = recorder;
    recorder.addEventListener("dataavailable", (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    });
    recorder.addEventListener("stop", () => {
      const blob = new Blob(chunks, { type: recorder.mimeType || "video/webm" });
      const file = new File([blob], `video-note-${Date.now()}.webm`, { type: blob.type });
      appendFiles([file], "video-note", true);
      setSendAsVideoNote(true);
    });
    recorder.start();
    setRecordingVideo(true);
  }

  function applyMention(username: string) {
    if (!mentionState.range) {
      return;
    }
    const nextValue = `${body.slice(0, mentionState.range.start)}@${username} ${body.slice(mentionState.range.end)}`;
    setBody(nextValue);
    setMentionIndex(0);
    requestAnimationFrame(() => {
      const cursor = mentionState.range!.start + username.length + 2;
      textareaRef.current?.focus();
      textareaRef.current?.setSelectionRange(cursor, cursor);
    });
  }

  async function submitCurrentMessage() {
    if (!body.trim() && attachments.length === 0) {
      return;
    }

    setSubmitting(true);
    try {
      await onSend({
        body,
        attachments,
        sendAsVideoNote: sendAsVideoNote || attachments.some((attachment) => attachment.sendAsVideoNote),
      });
      setBody("");
      setAttachments([]);
      setSendAsVideoNote(false);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await submitCurrentMessage();
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      {replyTarget && (
        <div className={styles.replyPreview}>
          <div>
            <strong>{replyTarget.authorDisplayName}</strong>
            <span>{replyTarget.body || "Ответ с вложением"}</span>
          </div>
          <button type="button" onClick={onCancelReply}>
            ×
          </button>
        </div>
      )}

      <div className={styles.tools}>
        <label className={styles.filePicker}>
          <input type="file" multiple onChange={handleFiles} />
          <span>Добавить файлы</span>
        </label>
        <button type="button" className={styles.toolButton} onClick={() => void toggleVoiceRecording()}>
          {recordingVoice ? "Стоп голосового" : "Голосовое"}
        </button>
        <button type="button" className={styles.toolButton} onClick={() => void openCamera()}>
          Камера
        </button>
        <button type="button" className={styles.toolButton} onClick={() => void toggleVideoRecording()}>
          {recordingVideo ? "Стоп кружка" : "Кружок"}
        </button>
        {attachments.length === 1 && (
          <label className={styles.inlineCheck}>
            <input type="checkbox" checked={sendAsVideoNote} onChange={(event) => setSendAsVideoNote(event.target.checked)} />
            <span>Отправить как кружок</span>
          </label>
        )}
      </div>

      {cameraOpen && (
        <div className={styles.cameraShell}>
          <video ref={videoPreviewRef} className={styles.cameraPreview} autoPlay muted playsInline />
        </div>
      )}

      {attachments.length > 0 && (
        <div className={styles.pending}>
          {attachments.map((attachment) => (
            <div key={attachment.id} className={styles.pendingItem}>
              <strong>{attachment.file.name}</strong>
              <span>{attachment.source}</span>
              <button type="button" onClick={() => setAttachments((current) => current.filter((item) => item.id !== attachment.id))}>
                Убрать
              </button>
            </div>
          ))}
        </div>
      )}

      <textarea
        ref={textareaRef}
        rows={3}
        value={body}
        placeholder="Напиши сообщение..."
        onChange={(event) => setBody(event.target.value)}
        onKeyDown={(event) => {
          if (!mentionState.visible) {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              void submitCurrentMessage();
            }
            return;
          }

          if (event.key === "ArrowDown") {
            event.preventDefault();
            setMentionIndex((current) => (current + 1) % mentionState.suggestions.length);
          }
          if (event.key === "ArrowUp") {
            event.preventDefault();
            setMentionIndex((current) => (current - 1 + mentionState.suggestions.length) % mentionState.suggestions.length);
          }
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            applyMention(mentionState.suggestions[mentionIndex].username);
          }
        }}
      />

      {mentionState.visible && (
        <div className={styles.mentions}>
          {mentionState.suggestions.map((member, index) => (
            <button
              key={member.userId}
              type="button"
              className={`${styles.mentionButton} ${index === mentionIndex ? styles.activeMention : ""}`}
              onMouseDown={(event) => {
                event.preventDefault();
                applyMention(member.username);
              }}
            >
              <strong>{member.displayName}</strong>
              <span>@{member.username}</span>
            </button>
          ))}
        </div>
      )}

      <div className={styles.footer}>
        <span>Enter отправляет, Shift+Enter переносит строку</span>
        <button type="submit" disabled={submitting}>
          {submitting ? "Отправляем..." : "Отправить"}
        </button>
      </div>
    </form>
  );
}
