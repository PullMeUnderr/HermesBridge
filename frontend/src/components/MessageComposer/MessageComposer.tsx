"use client"

import {
  ChangeEvent,
  FormEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react"
import styles from "./MessageComposer.module.scss"
import type {
  ConversationMember,
  ConversationMessage,
  PendingAttachment,
} from "@/types/api"

function selectPreferredVoiceRecordingFormat() {
  if (
    typeof MediaRecorder === "undefined" ||
    typeof MediaRecorder.isTypeSupported !== "function"
  ) {
    return null
  }

  const candidates = [
    { mimeType: "audio/mp4;codecs=mp4a.40.2" },
    { mimeType: "audio/mp4" },
    { mimeType: "audio/ogg;codecs=opus" },
    { mimeType: "audio/ogg" },
    { mimeType: "audio/webm;codecs=opus" },
    { mimeType: "audio/webm" },
  ]

  return candidates.find((candidate) =>
    MediaRecorder.isTypeSupported(candidate.mimeType),
  ) ?? null
}

function selectPreferredVideoNoteRecordingFormat() {
  if (
    typeof MediaRecorder === "undefined" ||
    typeof MediaRecorder.isTypeSupported !== "function"
  ) {
    return null
  }

  const candidates = [
    { mimeType: "video/mp4;codecs=avc1.42E01E,mp4a.40.2" },
    { mimeType: "video/mp4" },
    { mimeType: "video/webm;codecs=vp9,opus" },
    { mimeType: "video/webm;codecs=vp8,opus" },
    { mimeType: "video/webm" },
  ]

  return candidates.find((candidate) =>
    MediaRecorder.isTypeSupported(candidate.mimeType),
  ) ?? null
}

function resolveVoiceRecordingExtension(mimeType: string) {
  const normalized = String(mimeType || "").toLowerCase()
  if (normalized.includes("mp4")) {
    return "m4a"
  }
  if (normalized.includes("ogg")) {
    return "ogg"
  }
  return "webm"
}

function resolveVideoRecordingExtension(mimeType: string) {
  const normalized = String(mimeType || "").toLowerCase()
  if (normalized.includes("mp4")) {
    return "mp4"
  }
  return "webm"
}

function formatRecordingTimer(startedAt: number | null) {
  if (!startedAt) {
    return "00:00"
  }

  const elapsedSeconds = Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
  const minutes = String(Math.floor(elapsedSeconds / 60)).padStart(2, "0")
  const seconds = String(elapsedSeconds % 60).padStart(2, "0")
  return `${minutes}:${seconds}`
}

function PlusIcon() {
  return (
    <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
      <path
        d='M11 5a1 1 0 0 1 2 0v6h6a1 1 0 1 1 0 2h-6v6a1 1 0 1 1-2 0v-6H5a1 1 0 1 1 0-2h6z'
        fill='currentColor'
      />
    </svg>
  )
}

function VideoNoteIcon() {
  return (
    <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
      <circle
        cx='12'
        cy='12'
        r='7.5'
        fill='none'
        stroke='currentColor'
        strokeWidth='1.8'
      />
      <circle cx='12' cy='12' r='3.4' fill='currentColor' />
    </svg>
  )
}

function VoiceIcon() {
  return (
    <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
      <path
        d='M12 15.2a3.6 3.6 0 0 0 3.6-3.6V7.6a3.6 3.6 0 1 0-7.2 0v4a3.6 3.6 0 0 0 3.6 3.6Zm-5-3.6a1 1 0 1 1 2 0 3 3 0 0 0 6 0 1 1 0 1 1 2 0 5 5 0 0 1-4 4.9V19h2a1 1 0 1 1 0 2H9a1 1 0 1 1 0-2h2v-2.5a5 5 0 0 1-4-4.9Z'
        fill='currentColor'
      />
    </svg>
  )
}

function SendIcon() {
  return (
    <svg viewBox='0 0 24 24' focusable='false' aria-hidden='true'>
      <path
        d='M3.4 11.3 19.8 4.4a1 1 0 0 1 1.34 1.22l-4.3 13.77a1 1 0 0 1-1.75.3l-3.24-4.12-3.86 3.47a1 1 0 0 1-1.67-.82l.48-5.49-3.76-1.47a1 1 0 0 1-.05-1.86Z'
        fill='#000'
      />
    </svg>
  )
}

interface MessageComposerProps {
  currentUserId: number
  members: ConversationMember[]
  replyTarget: ConversationMessage | null
  onCancelReply: () => void
  onTypingStateChange?: (active: boolean) => void
  onSend: (payload: {
    body: string
    attachments: PendingAttachment[]
    sendAsVideoNote: boolean
  }) => Promise<void>
}

export function MessageComposer({
  currentUserId,
  members,
  replyTarget,
  onCancelReply,
  onTypingStateChange,
  onSend,
}: MessageComposerProps) {
  const [body, setBody] = useState("")
  const [attachments, setAttachments] = useState<PendingAttachment[]>([])
  const [sendAsVideoNote, setSendAsVideoNote] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [recordingVoice, setRecordingVoice] = useState(false)
  const [processingVoice, setProcessingVoice] = useState(false)
  const [recordedVoiceFile, setRecordedVoiceFile] = useState<File | null>(null)
  const [recordedVoiceUrl, setRecordedVoiceUrl] = useState("")
  const [voiceRecordingStartedAt, setVoiceRecordingStartedAt] = useState<number | null>(null)
  const [voiceRecordingTick, setVoiceRecordingTick] = useState(0)
  const [recordingVideo, setRecordingVideo] = useState(false)
  const [cameraOpen, setCameraOpen] = useState(false)
  const [recordedVideoFile, setRecordedVideoFile] = useState<File | null>(null)
  const [recordedVideoUrl, setRecordedVideoUrl] = useState("")
  const [cameraFacingMode, setCameraFacingMode] = useState<"user" | "environment">("user")
  const [videoRecordingStartedAt, setVideoRecordingStartedAt] = useState<number | null>(null)
  const [composerError, setComposerError] = useState("")
  const [mentionIndex, setMentionIndex] = useState(0)
  const [videoRecordingTick, setVideoRecordingTick] = useState(0)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const videoRecorderRef = useRef<MediaRecorder | null>(null)
  const videoStreamRef = useRef<MediaStream | null>(null)
  const discardVideoOnStopRef = useRef(false)
  const videoPreviewRef = useRef<HTMLVideoElement | null>(null)
  const recordedVideoPreviewRef = useRef<HTMLVideoElement | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const typingActiveRef = useRef(false)
  const typingKeepAliveRef = useRef<number | null>(null)
  const [previewVideoPlaying, setPreviewVideoPlaying] = useState(false)

  const mentionState = useMemo(() => {
    const match = body.match(/(?:^|[\s(])@([A-Za-z0-9_]*)$/)
    if (!match) {
      return {
        visible: false,
        suggestions: [],
        range: null as { start: number; end: number } | null,
      }
    }

    const query = match[1].toLowerCase()
    const suggestions = members
      .filter(
        (member) =>
          member.userId !== currentUserId &&
          ((member.username || "").toLowerCase().includes(query) ||
            (member.displayName || "").toLowerCase().includes(query)),
      )
      .slice(0, 6)
    const mentionToken = `@${match[1]}`
    const mentionEnd = body.length
    const mentionStart = mentionEnd - mentionToken.length

    return {
      visible: suggestions.length > 0,
      suggestions,
      range: {
        start: mentionStart,
        end: mentionEnd,
      },
    }
  }, [body, currentUserId, members])

  useEffect(() => {
    if (!cameraOpen || !videoPreviewRef.current || !videoStreamRef.current) {
      return
    }
    videoPreviewRef.current.srcObject = videoStreamRef.current
  }, [cameraOpen])

  useEffect(() => {
    return () => {
      if (typingKeepAliveRef.current !== null) {
        window.clearTimeout(typingKeepAliveRef.current)
      }
      if (typingActiveRef.current) {
        onTypingStateChange?.(false)
      }
      mediaStreamRef.current?.getTracks().forEach((track) => track.stop())
      videoStreamRef.current?.getTracks().forEach((track) => track.stop())
      if (recordedVoiceUrl) {
        URL.revokeObjectURL(recordedVoiceUrl)
      }
      if (recordedVideoUrl) {
        URL.revokeObjectURL(recordedVideoUrl)
      }
    }
  }, [onTypingStateChange, recordedVideoUrl, recordedVoiceUrl])

  function emitTypingState(active: boolean, force = false) {
    if (!force && typingActiveRef.current === active) {
      return
    }
    typingActiveRef.current = active
    onTypingStateChange?.(active)
  }

  function scheduleTypingKeepAlive() {
    if (typingKeepAliveRef.current !== null) {
      window.clearTimeout(typingKeepAliveRef.current)
    }
    typingKeepAliveRef.current = window.setTimeout(() => {
      emitTypingState(false)
      typingKeepAliveRef.current = null
    }, 3000)
  }

  useEffect(() => {
    const hasText = body.length > 0
    if (!hasText) {
      if (typingKeepAliveRef.current !== null) {
        window.clearTimeout(typingKeepAliveRef.current)
        typingKeepAliveRef.current = null
      }
      emitTypingState(false)
      return
    }

    emitTypingState(true, true)
    scheduleTypingKeepAlive()
  }, [body, onTypingStateChange])

  useEffect(() => {
    if (!replyTarget) {
      return
    }
    textareaRef.current?.focus()
  }, [replyTarget])

  const videoRecordingTimer = useMemo(() => {
    return formatRecordingTimer(videoRecordingStartedAt)
  }, [videoRecordingStartedAt, recordingVideo, videoRecordingTick])

  const voiceRecordingTimer = useMemo(() => {
    return formatRecordingTimer(voiceRecordingStartedAt)
  }, [voiceRecordingStartedAt, voiceRecordingTick])

  useEffect(() => {
    if (!recordingVoice || !voiceRecordingStartedAt) {
      return
    }

    const handle = window.setInterval(() => {
      setVoiceRecordingTick((value) => value + 1)
    }, 1000)

    return () => window.clearInterval(handle)
  }, [recordingVoice, voiceRecordingStartedAt])

  useEffect(() => {
    if (!recordingVideo || !videoRecordingStartedAt) {
      return
    }

    const handle = window.setInterval(() => {
      setVideoRecordingTick((value) => value + 1)
    }, 1000)

    return () => window.clearInterval(handle)
  }, [recordingVideo, videoRecordingStartedAt])

  function appendFiles(
    files: File[],
    source: PendingAttachment["source"],
    forceVideoNote = false,
  ) {
    setAttachments((current) => [
      ...current,
      ...files.map((file, index) => ({
        id: `${source}-${file.name}-${file.size}-${Date.now()}-${index}`,
        file,
        source,
        sendAsVideoNote: forceVideoNote,
      })),
    ])
    if (!forceVideoNote && files.length !== 1) {
      setSendAsVideoNote(false)
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    appendFiles(files, "picker")
    event.target.value = ""
  }

  async function toggleVoiceRecording() {
    if (recordingVoice) {
      setProcessingVoice(true)
      try {
        mediaRecorderRef.current?.requestData?.()
      } catch {
        // best effort
      }
      mediaRecorderRef.current?.stop()
      setRecordingVoice(false)
      setVoiceRecordingStartedAt(null)
      return
    }

    setComposerError("")
    try {
      if (recordedVoiceUrl) {
        URL.revokeObjectURL(recordedVoiceUrl)
      }
      setRecordedVoiceUrl("")
      setRecordedVoiceFile(null)
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      mediaStreamRef.current = stream
      const preferredFormat = selectPreferredVoiceRecordingFormat()
      const recorder = preferredFormat
        ? new MediaRecorder(stream, { mimeType: preferredFormat.mimeType })
        : new MediaRecorder(stream)
      const chunks: Blob[] = []
      mediaRecorderRef.current = recorder
      recorder.addEventListener("dataavailable", (event) => {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data)
        }
      })
      recorder.addEventListener("stop", () => {
        const mimeType =
          recorder.mimeType || preferredFormat?.mimeType || "audio/webm"
        const blob = new Blob(chunks, { type: mimeType })
        stream.getTracks().forEach((track) => track.stop())
        mediaStreamRef.current = null
        mediaRecorderRef.current = null
        setProcessingVoice(false)

        if (blob.size === 0) {
          setComposerError("Не удалось записать голосовое.")
          return
        }

        const objectUrl = URL.createObjectURL(blob)
        const file = new File(
          [blob],
          `voice-${Date.now()}.${resolveVoiceRecordingExtension(mimeType)}`,
          {
            type: mimeType,
            lastModified: Date.now(),
          },
        )
        setRecordedVoiceUrl(objectUrl)
        setRecordedVoiceFile(file)
      })
      recorder.start(250)
      setRecordingVoice(true)
      setVoiceRecordingStartedAt(Date.now())
      setVoiceRecordingTick(0)
    } catch {
      setProcessingVoice(false)
      setComposerError("Не удалось получить доступ к микрофону.")
    }
  }

  function discardRecordedVoice() {
    if (recordingVoice) {
      setProcessingVoice(false)
      mediaRecorderRef.current?.stop()
      setRecordingVoice(false)
      setVoiceRecordingStartedAt(null)
      return
    }

    if (recordedVoiceUrl) {
      URL.revokeObjectURL(recordedVoiceUrl)
    }
    setRecordedVoiceUrl("")
    setRecordedVoiceFile(null)
    setVoiceRecordingStartedAt(null)
    setVoiceRecordingTick(0)
  }

  async function openCamera() {
    try {
      if (recordedVideoUrl) {
        URL.revokeObjectURL(recordedVideoUrl)
      }
      setRecordedVideoUrl("")
      setRecordedVideoFile(null)
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: cameraFacingMode },
        audio: true,
      })
      videoStreamRef.current?.getTracks().forEach((track) => track.stop())
      videoStreamRef.current = stream
      setCameraOpen(true)
      setComposerError("")
      if (videoPreviewRef.current) {
        videoPreviewRef.current.srcObject = stream
      }
    } catch {
      setComposerError("Не удалось открыть камеру.")
    }
  }

  async function switchCamera() {
    const nextMode = cameraFacingMode === "user" ? "environment" : "user"
    setCameraFacingMode(nextMode)
    if (!cameraOpen) {
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: nextMode },
        audio: true,
      })
      videoStreamRef.current?.getTracks().forEach((track) => track.stop())
      videoStreamRef.current = stream
      if (videoPreviewRef.current) {
        videoPreviewRef.current.srcObject = stream
      }
    } catch {
      setComposerError("Не удалось переключить камеру.")
      setCameraFacingMode(cameraFacingMode)
    }
  }

  function cancelVideoNotePreview() {
    discardVideoOnStopRef.current = true

    if (recordingVideo) {
      try {
        videoRecorderRef.current?.requestData?.()
      } catch {
        // best effort
      }
      videoRecorderRef.current?.stop()
      setRecordingVideo(false)
    } else {
      videoStreamRef.current?.getTracks().forEach((track) => track.stop())
      videoStreamRef.current = null
      videoRecorderRef.current = null
      setCameraOpen(false)
      setVideoRecordingStartedAt(null)
      if (recordedVideoUrl) {
        URL.revokeObjectURL(recordedVideoUrl)
      }
      setRecordedVideoUrl("")
      setRecordedVideoFile(null)
    }
  }

  async function sendRecordedVideoNote() {
    if (!recordedVideoFile) {
      return
    }

    setSubmitting(true)
    try {
      setComposerError("")
      await onSend({
        body: "",
        attachments: [
          {
            id: `video-note-${recordedVideoFile.name}-${recordedVideoFile.size}-${Date.now()}`,
            file: recordedVideoFile,
            source: "video-note",
            sendAsVideoNote: true,
          },
        ],
        sendAsVideoNote: true,
      })
      if (recordedVideoUrl) {
        URL.revokeObjectURL(recordedVideoUrl)
      }
      setRecordedVideoUrl("")
      setRecordedVideoFile(null)
      setPreviewVideoPlaying(false)
    } catch {
      setComposerError("Не удалось отправить кружок.")
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleVideoRecording() {
    if (recordingVideo) {
      try {
        videoRecorderRef.current?.requestData?.()
      } catch {
        // best effort
      }
      videoRecorderRef.current?.stop()
      setRecordingVideo(false)
      setVideoRecordingStartedAt(null)
      return
    }

    if (!videoStreamRef.current) {
      return
    }

    setComposerError("")
    discardVideoOnStopRef.current = false
    const preferredFormat = selectPreferredVideoNoteRecordingFormat()
    try {
      const chunks: Blob[] = []
      const recorder = preferredFormat
        ? new MediaRecorder(videoStreamRef.current, {
            mimeType: preferredFormat.mimeType,
          })
        : new MediaRecorder(videoStreamRef.current)
      videoRecorderRef.current = recorder
      recorder.addEventListener("dataavailable", (event) => {
        if (event.data && event.data.size > 0) {
          chunks.push(event.data)
        }
      })
      recorder.addEventListener("stop", async () => {
        const mimeType =
          recorder.mimeType || preferredFormat?.mimeType || "video/webm"
        const blob = new Blob(chunks, { type: mimeType })
        const shouldDiscard = discardVideoOnStopRef.current
        discardVideoOnStopRef.current = false
        videoRecorderRef.current = null
        videoStreamRef.current?.getTracks().forEach((track) => track.stop())
        videoStreamRef.current = null
        setCameraOpen(false)
        setVideoRecordingStartedAt(null)

        if (shouldDiscard) {
          return
        }

        if (blob.size === 0) {
          setComposerError("Кружок получился пустым.")
          return
        }

        const file = new File(
          [blob],
          `video-note-${Date.now()}.${resolveVideoRecordingExtension(mimeType)}`,
          {
            type: mimeType,
            lastModified: Date.now(),
          },
        )
        const objectUrl = URL.createObjectURL(blob)
        setRecordedVideoUrl(objectUrl)
        setRecordedVideoFile(file)
        setPreviewVideoPlaying(false)
      })
      recorder.start(250)
      setRecordingVideo(true)
      setVideoRecordingStartedAt(Date.now())
    } catch {
      setComposerError("Не удалось запустить запись кружка.")
    }
  }

  function applyMention(username: string) {
    if (!mentionState.range) {
      return
    }
    const nextValue = `${body.slice(0, mentionState.range.start)}@${username} ${body.slice(mentionState.range.end)}`
    const nextCursor = mentionState.range.start + username.length + 2
    setBody(nextValue)
    setMentionIndex(0)
    requestAnimationFrame(() => {
      textareaRef.current?.focus()
      textareaRef.current?.setSelectionRange(nextCursor, nextCursor)
    })
  }

  async function submitCurrentMessage() {
    const nextAttachments = recordedVoiceFile
      ? [
          ...attachments,
          {
            id: `voice-${recordedVoiceFile.name}-${recordedVoiceFile.size}`,
            file: recordedVoiceFile,
            source: "voice" as const,
          },
        ]
      : attachments

    if (!body.trim() && nextAttachments.length === 0) {
      return
    }

    if (recordingVoice || processingVoice || recordingVideo) {
      setComposerError("Сначала заверши запись.")
      return
    }

    setSubmitting(true)
    try {
      setComposerError("")
      emitTypingState(false)
      if (typingKeepAliveRef.current !== null) {
        window.clearTimeout(typingKeepAliveRef.current)
        typingKeepAliveRef.current = null
      }
      await onSend({
        body,
        attachments: nextAttachments,
        sendAsVideoNote:
          sendAsVideoNote ||
          nextAttachments.some((attachment) => attachment.sendAsVideoNote),
      })
      setBody("")
      setAttachments([])
      setSendAsVideoNote(false)
      if (recordedVoiceUrl) {
        URL.revokeObjectURL(recordedVoiceUrl)
      }
      setRecordedVoiceUrl("")
      setRecordedVoiceFile(null)
      setVoiceRecordingStartedAt(null)
      setVoiceRecordingTick(0)
    } catch {
      setComposerError("Не удалось отправить сообщение.")
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleRecordedVideoPreviewPlayback() {
    const video = recordedVideoPreviewRef.current
    if (!video) {
      return
    }

    if (video.paused || video.ended) {
      await video.play()
      return
    }

    video.pause()
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await submitCurrentMessage()
  }

  const attachmentSummary =
    attachments.length > 0
      ? `${attachments.length} файл(ов)`
      : sendAsVideoNote
        ? "режим кружка"
        : "Без вложений"

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      {replyTarget && (
        <div className={styles.replyPreview}>
          <div>
            <strong>{replyTarget.authorDisplayName}</strong>
            <span>{replyTarget.body || "Ответ с вложением"}</span>
          </div>
          <button type='button' onClick={onCancelReply}>
            ×
          </button>
        </div>
      )}

      <div className={styles.tools}>
        <label className={styles.filePicker}>
          <input type='file' multiple onChange={handleFiles} />
          <span className={styles.toolIcon} aria-hidden='true'>
            <PlusIcon />
          </span>
          <span className={styles.srOnly}>Добавить файлы</span>
        </label>
        <button
          type='button'
          className={`${styles.toolButton} ${styles.voiceButton} ${recordingVoice ? styles.recording : ""}`}
          onClick={() => void toggleVoiceRecording()}
          aria-label={
            recordingVoice ? "Остановить голосовое" : "Записать голосовое"
          }
        >
          <span className={styles.toolIcon} aria-hidden='true'>
            <VoiceIcon />
          </span>
          <span className={styles.srOnly}>
            {recordingVoice ? "Стоп голосового" : "Голосовое"}
          </span>
        </button>
        <button
          type='button'
          className={`${styles.toolButton} ${styles.videoButton} ${recordingVideo ? styles.recording : ""}`}
          onClick={() => void openCamera()}
          aria-label='Открыть запись кружка'
        >
          <span className={styles.toolIcon} aria-hidden='true'>
            <VideoNoteIcon />
          </span>
          <span className={styles.srOnly}>
            Кружок
          </span>
        </button>
        {attachments.length === 1 && (
          <label className={styles.inlineCheck}>
            <input
              type='checkbox'
              checked={sendAsVideoNote}
              onChange={(event) => setSendAsVideoNote(event.target.checked)}
            />
            <span>Отправить как кружок</span>
          </label>
        )}
      </div>

      {cameraOpen && (
        <div className={styles.videoNotePreview}>
          <div className={styles.videoNotePreviewHead}>
            <div>
              <strong className={styles.videoNoteTitle}>Кружок · камера</strong>
              <span className={styles.videoNoteMeta}>
                {recordingVideo
                  ? "Идет запись кружка"
                  : "Выбери ракурс, при необходимости переключи камеру и нажми запись"}
              </span>
            </div>
            <div className={styles.videoNoteActions}>
              <button
                type='button'
                className={styles.videoNoteGhostButton}
                onClick={() => void switchCamera()}
                disabled={recordingVideo}
              >
                Камера
              </button>
              <button
                type='button'
                className={styles.videoNotePrimaryButton}
                onClick={() => void toggleVideoRecording()}
              >
                {recordingVideo ? "Стоп" : "Запись"}
              </button>
              <button
                type='button'
                className={styles.videoNoteGhostButton}
                onClick={cancelVideoNotePreview}
              >
                Отмена
              </button>
              {recordingVideo && (
                <div className={styles.videoRecordingBadge}>
                  <span className={styles.videoRecordingDot} />
                  <span>{videoRecordingTimer}</span>
                </div>
              )}
            </div>
          </div>
          <div className={styles.videoNoteShell}>
            <video
              ref={videoPreviewRef}
              className={styles.videoNoteCamera}
              autoPlay
              muted
              playsInline
            />
          </div>
        </div>
      )}

      {recordedVideoUrl && !cameraOpen && (
        <div className={styles.videoNotePreview}>
          <div className={styles.videoNotePreviewHead}>
            <div>
              <strong className={styles.videoNoteTitle}>Кружок · предпросмотр</strong>
              <span className={styles.videoNoteMeta}>
                Проверь запись и реши, отправлять ее или перезаписать.
              </span>
            </div>
            <div className={styles.videoNoteActions}>
              <button
                type='button'
                className={styles.videoNoteGhostButton}
                onClick={() => void openCamera()}
                disabled={submitting}
              >
                Перезаписать
              </button>
              <button
                type='button'
                className={styles.videoNotePrimaryButton}
                onClick={() => void sendRecordedVideoNote()}
                disabled={submitting}
              >
                Отправить
              </button>
              <button
                type='button'
                className={styles.videoNoteGhostButton}
                onClick={cancelVideoNotePreview}
                disabled={submitting}
              >
                Удалить
              </button>
            </div>
          </div>
          <div className={styles.videoNoteShell}>
            <button
              type='button'
              className={styles.videoNotePreviewButton}
              onClick={() => void toggleRecordedVideoPreviewPlayback()}
              aria-label={previewVideoPlaying ? "Остановить предпросмотр кружка" : "Запустить предпросмотр кружка"}
            >
              <video
                ref={recordedVideoPreviewRef}
                className={styles.videoNoteCamera}
                src={recordedVideoUrl}
                playsInline
                preload='metadata'
                onPlay={() => setPreviewVideoPlaying(true)}
                onPause={() => setPreviewVideoPlaying(false)}
                onEnded={() => setPreviewVideoPlaying(false)}
              />
              <span className={styles.videoNotePreviewHint}>
                {previewVideoPlaying ? "Пауза" : "Нажми для просмотра"}
              </span>
            </button>
          </div>
        </div>
      )}

      {(recordingVoice || processingVoice || recordedVoiceFile) && (
        <div className={styles.recordedVoicePreview}>
          <div>
            <strong>
              {recordingVoice
                ? "Запись..."
                : processingVoice
                  ? "Подготавливаем..."
                  : "Голосовое"}
            </strong>
            <span>
              {recordingVoice
                ? "Микрофон активен. Нажми еще раз, чтобы остановить запись."
                : processingVoice
                  ? "Собираем запись перед отправкой."
                  : `Готово к отправке${recordedVoiceFile ? ` · ${Math.max(1, Math.round(recordedVoiceFile.size / 1024))} КБ` : ""}`}
            </span>
          </div>
          {recordingVoice && (
            <div className={styles.voiceRecordingBadge}>
              <span className={styles.voiceRecordingDot} />
              <strong>Идет запись</strong>
              <span>{voiceRecordingTimer}</span>
            </div>
          )}
          {recordedVoiceUrl && !recordingVoice && !processingVoice && (
            <audio controls src={recordedVoiceUrl} className={styles.recordedVoiceAudio} />
          )}
          <button type='button' onClick={discardRecordedVoice}>
            {recordingVoice ? "Отмена" : "Удалить"}
          </button>
        </div>
      )}

      {attachments.length > 0 && (
        <div className={styles.pending}>
          {attachments.map((attachment) => (
            <div key={attachment.id} className={styles.pendingItem}>
              <strong>{attachment.file.name}</strong>
              <span>{attachment.source}</span>
              <button
                type='button'
                onClick={() =>
                  setAttachments((current) =>
                    current.filter((item) => item.id !== attachment.id),
                  )
                }
              >
                Убрать
              </button>
            </div>
          ))}
        </div>
      )}

      {composerError && <div className={styles.errorBanner}>{composerError}</div>}

      <textarea
        ref={textareaRef}
        rows={3}
        value={body}
        placeholder='Напиши сообщение...'
        onChange={(event) => setBody(event.target.value)}
        onBlur={() => {
          if (typingKeepAliveRef.current !== null) {
            window.clearTimeout(typingKeepAliveRef.current)
            typingKeepAliveRef.current = null
          }
          emitTypingState(false)
        }}
        onKeyDown={(event) => {
          if (!mentionState.visible) {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault()
              void submitCurrentMessage()
            }
            return
          }

          if (event.key === "ArrowDown") {
            event.preventDefault()
            setMentionIndex(
              (current) => (current + 1) % mentionState.suggestions.length,
            )
          }
          if (event.key === "ArrowUp") {
            event.preventDefault()
            setMentionIndex(
              (current) =>
                (current - 1 + mentionState.suggestions.length) %
                mentionState.suggestions.length,
            )
          }
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault()
            applyMention(mentionState.suggestions[mentionIndex].username)
          }
        }}
      />

      {mentionState.visible && (
        <div className={styles.mentions}>
          {mentionState.suggestions.map((member, index) => (
            <button
              key={member.userId}
              type='button'
              className={`${styles.mentionButton} ${index === mentionIndex ? styles.activeMention : ""}`}
              onMouseDown={(event) => {
                event.preventDefault()
                applyMention(member.username)
              }}
            >
              <strong>{member.displayName}</strong>
              <span>@{member.username}</span>
            </button>
          ))}
        </div>
      )}

      <div className={styles.footer}>
        <span>{attachmentSummary}</span>
        <button
          type='submit'
          className={styles.submitButton}
          disabled={submitting}
        >
          <span className={styles.sendIcon} aria-hidden='true'>
            <SendIcon />
          </span>
          <span className={styles.srOnly}>
            {submitting ? "Отправляем..." : "Отправить"}
          </span>
        </button>
      </div>
    </form>
  )
}
