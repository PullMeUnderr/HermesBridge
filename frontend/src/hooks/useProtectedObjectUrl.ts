"use client";

import { useEffect, useState } from "react";
import { fetchProtectedBlobUrl } from "@/lib/api";

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

export function useProtectedObjectUrl(token: string, src: string | null | undefined) {
  const [resolvedSrc, setResolvedSrc] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;

    async function hydrate() {
      if (!src) {
        setResolvedSrc(null);
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
        objectUrl = await fetchProtectedBlobUrl(token, src);
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
  }, [src, token]);

  return resolvedSrc;
}
