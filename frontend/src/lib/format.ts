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

export function isTelegramConversationTitle(value: string | null | undefined) {
  return String(value ?? "").startsWith("Telegram: ");
}

export function displayConversationTitle(value: string | null | undefined) {
  const normalized = String(value ?? "");
  return isTelegramConversationTitle(normalized) ? normalized.slice("Telegram: ".length).trim() : normalized;
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

export function fileExtension(value: string | null | undefined) {
  const normalized = String(value ?? "").trim().toLowerCase();
  const lastDotIndex = normalized.lastIndexOf(".");
  if (lastDotIndex < 0 || lastDotIndex === normalized.length - 1) {
    return "";
  }

  return normalized.slice(lastDotIndex + 1);
}

export function inferAttachmentMimeType(
  kind: string,
  fileName: string | null | undefined,
  mimeType: string | null | undefined,
) {
  const normalizedMimeType = String(mimeType ?? "").trim().toLowerCase();
  if (normalizedMimeType && normalizedMimeType !== "application/octet-stream") {
    return normalizedMimeType;
  }

  const extension = fileExtension(fileName);
  switch (extension) {
    case "jpg":
    case "jpeg":
      return "image/jpeg";
    case "png":
      return "image/png";
    case "gif":
      return "image/gif";
    case "webp":
      return "image/webp";
    case "bmp":
      return "image/bmp";
    case "svg":
      return "image/svg+xml";
    case "heic":
      return "image/heic";
    case "heif":
      return "image/heif";
    case "mp4":
    case "m4v":
      return "video/mp4";
    case "mov":
      return "video/quicktime";
    case "webm":
      return kind === "VOICE" ? "audio/webm" : "video/webm";
    case "mkv":
      return "video/x-matroska";
    case "avi":
      return "video/x-msvideo";
    case "ogg":
    case "oga":
    case "opus":
      return "audio/ogg";
    case "mp3":
      return "audio/mpeg";
    case "m4a":
      return "audio/mp4";
    case "aac":
      return "audio/aac";
    case "wav":
      return "audio/wav";
    default:
      break;
  }

  if (kind === "PHOTO") {
    return "image/jpeg";
  }
  if (kind === "VIDEO" || kind === "VIDEO_NOTE") {
    return "video/mp4";
  }
  if (kind === "VOICE") {
    return "audio/ogg";
  }

  return normalizedMimeType || "application/octet-stream";
}

export function isImageAttachment(mimeType: string | null, kind: string, fileName?: string | null) {
  return (
    kind === "PHOTO" ||
    String(mimeType ?? "").startsWith("image/") ||
    ["jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg"].includes(fileExtension(fileName))
  );
}

export function isVideoAttachment(mimeType: string | null, kind: string, fileName?: string | null) {
  return (
    kind === "VIDEO" ||
    kind === "VIDEO_NOTE" ||
    String(mimeType ?? "").startsWith("video/") ||
    ["mp4", "mov", "m4v", "webm", "mkv", "avi"].includes(fileExtension(fileName))
  );
}

export function isAudioAttachment(mimeType: string | null, kind: string, fileName?: string | null) {
  return (
    kind === "VOICE" ||
    String(mimeType ?? "").startsWith("audio/") ||
    ["mp3", "m4a", "ogg", "oga", "wav", "aac", "flac"].includes(fileExtension(fileName))
  );
}

export function isVideoNoteAttachment(kind: string) {
  return kind === "VIDEO_NOTE";
}
