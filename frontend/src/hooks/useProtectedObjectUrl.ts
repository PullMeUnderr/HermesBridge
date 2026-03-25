"use client";

import { useEffect, useState } from "react";
import { fetchProtectedBlob } from "@/lib/api";
import { inferAttachmentMimeType } from "@/lib/format";

function isProtectedMediaUrl(url: string | null | undefined) {
  if (!url) {
    return false;
  }

  if (url.startsWith("/api/")) {
    return true;
  }

  try {
    return new URL(url).pathname.startsWith("/api/");
  } catch {
    return false;
  }
}

export function useProtectedObjectUrl(
  token: string,
  src: string | null | undefined,
  enabled = true,
  mediaHint?: {
    fileName?: string | null;
    mimeType?: string | null;
    kind?: string | null;
  },
) {
  const [resolvedSrc, setResolvedSrc] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;

    async function hydrate() {
      if (!src) {
        setResolvedSrc(null);
        return;
      }

      if (!enabled) {
        return;
      }

      if (!isProtectedMediaUrl(src)) {
        setResolvedSrc(src);
        return;
      }

      if (!token) {
        setResolvedSrc(null);
        return;
      }

      try {
        const blob = await fetchProtectedBlob(token, src);
        if (!blob) {
          if (active) {
            setResolvedSrc(null);
          }
          return;
        }

        const normalizedMimeType = inferAttachmentMimeType(
          String(mediaHint?.kind ?? ""),
          mediaHint?.fileName,
          mediaHint?.mimeType ?? blob.type,
        );
        const normalizedBlob =
          normalizedMimeType && normalizedMimeType !== blob.type
            ? new Blob([blob], { type: normalizedMimeType })
            : blob;
        objectUrl = URL.createObjectURL(normalizedBlob);
        if (active) {
          setResolvedSrc(objectUrl);
        }
      } catch {
        if (active) {
          setResolvedSrc(null);
        }
      }
    }

    hydrate();

    return () => {
      active = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [enabled, mediaHint?.fileName, mediaHint?.kind, mediaHint?.mimeType, src, token]);

  return resolvedSrc;
}
