"use client";

function decodeBase64Url(segment: string) {
  const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return atob(padded);
}

function tryReadStableTokenSubject(token: string) {
  if (!token || typeof window === "undefined") {
    return null;
  }

  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }

  try {
    const payload = JSON.parse(decodeBase64Url(parts[1])) as Record<string, unknown>;
    const subject =
      payload.sub ??
      payload.uid ??
      payload.userId ??
      payload.user_id ??
      payload.accountId ??
      payload.account_id;

    if (typeof subject === "string" || typeof subject === "number") {
      return `user:${subject}`;
    }
  } catch {
    return null;
  }

  return null;
}

function lightweightHash(value: string) {
  let hash = 5381;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 33) ^ value.charCodeAt(index);
  }
  return (hash >>> 0).toString(16);
}

export function getCacheScopeKey(token: string) {
  if (!token) {
    return "anonymous";
  }

  return tryReadStableTokenSubject(token) ?? `token:${lightweightHash(token)}`;
}
