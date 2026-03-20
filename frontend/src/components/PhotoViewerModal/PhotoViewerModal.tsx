"use client";

import { useEffect } from "react";
import styles from "./PhotoViewerModal.module.scss";
import type { PhotoViewerState } from "@/types/api";

interface PhotoViewerModalProps {
  open: boolean;
  viewer: PhotoViewerState;
  onClose: () => void;
  onStep: (direction: number) => void;
}

export function PhotoViewerModal({ open, viewer, onClose, onStep }: PhotoViewerModalProps) {
  useEffect(() => {
    function handleKeydown(event: KeyboardEvent) {
      if (!open) {
        return;
      }

      if (event.key === "Escape") {
        onClose();
      }
      if (event.key === "ArrowLeft" && viewer.items.length > 1) {
        onStep(-1);
      }
      if (event.key === "ArrowRight" && viewer.items.length > 1) {
        onStep(1);
      }
    }

    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [onClose, onStep, open, viewer.items.length]);

  if (!open) {
    return null;
  }

  const item = viewer.items[viewer.activeIndex];

  return (
    <div className={styles.backdrop}>
      <button className={styles.dismiss} type="button" onClick={onClose} />
      <div className={styles.shell} role="dialog" aria-modal="true">
        <button className={styles.close} type="button" onClick={onClose}>
          ×
        </button>
        {viewer.items.length > 1 && (
          <button className={styles.prev} type="button" onClick={() => onStep(-1)}>
            ‹
          </button>
        )}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img alt={item.fileName} src={item.src} className={styles.image} />
        {viewer.items.length > 1 && (
          <button className={styles.next} type="button" onClick={() => onStep(1)}>
            ›
          </button>
        )}
        <div className={styles.meta}>
          <span>{item.fileName}</span>
          <span>
            {viewer.activeIndex + 1} / {viewer.items.length}
          </span>
        </div>
      </div>
    </div>
  );
}
