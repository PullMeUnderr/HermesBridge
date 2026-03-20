"use client";

import styles from "./Avatar.module.scss";
import { useLazyVisible } from "@/hooks/useLazyVisible";
import { useProtectedObjectUrl } from "@/hooks/useProtectedObjectUrl";
import { getInitials } from "@/lib/format";

interface AvatarProps {
  token: string;
  name: string;
  src?: string | null;
  size?: "sm" | "md" | "lg";
}

export function Avatar({ token, name, src, size = "md" }: AvatarProps) {
  const { ref, visible } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, src, visible || !src);

  return resolvedSrc ? (
    <div ref={ref} className={`${styles.avatar} ${styles[size]}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img alt={name} src={resolvedSrc} className={styles.image} loading="lazy" />
    </div>
  ) : (
    <div ref={ref} className={`${styles.avatar} ${styles[size]} ${styles.fallback}`}>{getInitials(name)}</div>
  );
}
