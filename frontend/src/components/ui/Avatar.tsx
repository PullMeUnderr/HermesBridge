"use client";

import styles from "./Avatar.module.scss";
import { useProtectedObjectUrl } from "@/hooks/useProtectedObjectUrl";
import { getInitials } from "@/lib/format";

interface AvatarProps {
  token: string;
  name: string;
  src?: string | null;
  size?: "sm" | "md" | "lg";
}

export function Avatar({ token, name, src, size = "md" }: AvatarProps) {
  const resolvedSrc = useProtectedObjectUrl(token, src);

  return resolvedSrc ? (
    <div className={`${styles.avatar} ${styles[size]}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img alt={name} src={resolvedSrc} className={styles.image} />
    </div>
  ) : (
    <div className={`${styles.avatar} ${styles[size]} ${styles.fallback}`}>{getInitials(name)}</div>
  );
}
