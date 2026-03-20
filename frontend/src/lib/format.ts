export function formatTimestamp(value: string | null | undefined) {
  if (!value) {
    return "";
  }

  try {
    return new Intl.DateTimeFormat("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch {
    return value;
  }
}

export function formatClock(value: string | null | undefined) {
  if (!value) {
    return "";
  }

  try {
    return new Intl.DateTimeFormat("ru-RU", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch {
    return value;
  }
}

export function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB"];
  let size = value;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export function getInitials(value: string | null | undefined) {
  const words = String(value ?? "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (words.length === 0) {
    return "H";
  }

  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }

  return `${words[0][0]}${words[1][0]}`.toUpperCase();
}

export function renderPresenceLabel(subject: {
  telegramLinked?: boolean;
  online?: boolean;
  lastSeenAt?: string | null;
}) {
  if (!subject.telegramLinked) {
    return "Telegram не подключен";
  }

  if (subject.online) {
    return "Telegram online";
  }

  if (!subject.lastSeenAt) {
    return "Telegram offline";
  }

  return `Был(а) в сети ${formatTimestamp(subject.lastSeenAt)}`;
}

export function isImageAttachment(mimeType: string | null, kind: string) {
  return kind === "PHOTO" || String(mimeType ?? "").startsWith("image/");
}

export function isVideoAttachment(mimeType: string | null, kind: string) {
  return kind === "VIDEO" || kind === "VIDEO_NOTE" || String(mimeType ?? "").startsWith("video/");
}

export function isAudioAttachment(mimeType: string | null, kind: string) {
  return kind === "VOICE" || String(mimeType ?? "").startsWith("audio/");
}
