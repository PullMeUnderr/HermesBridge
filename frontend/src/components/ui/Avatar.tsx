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
  className?: string;
}

export function Avatar({ token, name, src, size = "md", className = "" }: AvatarProps) {
  const { ref, visible } = useLazyVisible<HTMLDivElement>();
  const resolvedSrc = useProtectedObjectUrl(token, src, visible || !src);
  const avatarClassName = `${styles.avatar} ${styles[size]} ${className}`.trim();

  return resolvedSrc ? (
    <div ref={ref} className={avatarClassName}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img alt={name} src={resolvedSrc} className={styles.image} loading="lazy" />
    </div>
  ) : (
    <div ref={ref} className={`${avatarClassName} ${styles.fallback}`.trim()}>{getInitials(name)}</div>
  );
}
