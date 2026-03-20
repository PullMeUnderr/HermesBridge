"use client";

import styles from "./ToastViewport.module.scss";
import type { ToastMessage } from "@/types/api";

interface ToastViewportProps {
  toasts: ToastMessage[];
}

export function ToastViewport({ toasts }: ToastViewportProps) {
  return (
    <div className={styles.viewport} aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`${styles.toast} ${styles[toast.kind]}`}>
          {toast.message}
        </div>
      ))}
    </div>
  );
}
