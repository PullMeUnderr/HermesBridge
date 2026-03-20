"use client";

import { FormEvent, useEffect, useState } from "react";
import styles from "./DrawerPanel.module.scss";
import type { AuthUser, ConversationSummary, DrawerMode } from "@/types/api";

interface DrawerPanelProps {
  open: boolean;
  mode: DrawerMode;
  me: AuthUser;
  conversation: ConversationSummary | null;
  onClose: () => void;
  onCreateConversation: (title: string) => Promise<void>;
  onJoinConversation: (inviteCode: string) => Promise<void>;
  onUpdateProfile: (payload: {
    displayName: string;
    username: string;
    avatar?: File | null;
    removeAvatar?: boolean;
  }) => Promise<void>;
  onUpdateConversation: (
    conversationId: number,
    payload: { title: string; avatar?: File | null; removeAvatar?: boolean; muted?: boolean },
  ) => Promise<void>;
  onDeleteConversation: (conversationId: number) => Promise<void>;
}

export function DrawerPanel({
  open,
  mode,
  me,
  conversation,
  onClose,
  onCreateConversation,
  onJoinConversation,
  onUpdateProfile,
  onUpdateConversation,
  onDeleteConversation,
}: DrawerPanelProps) {
  const [createTitle, setCreateTitle] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [profileName, setProfileName] = useState(me.displayName);
  const [profileUsername, setProfileUsername] = useState(me.username);
  const [profileAvatar, setProfileAvatar] = useState<File | null>(null);
  const [removeProfileAvatar, setRemoveProfileAvatar] = useState(false);
  const [conversationTitle, setConversationTitle] = useState(conversation?.title ?? "");
  const [conversationAvatar, setConversationAvatar] = useState<File | null>(null);
  const [removeConversationAvatar, setRemoveConversationAvatar] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setProfileName(me.displayName);
    setProfileUsername(me.username);
    setConversationTitle(conversation?.title ?? "");
  }, [conversation?.title, me.displayName, me.username]);

  if (!open || !mode) {
    return null;
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!createTitle.trim()) {
      return;
    }
    setBusy(true);
    try {
      await onCreateConversation(createTitle.trim());
      setCreateTitle("");
    } finally {
      setBusy(false);
    }
  }

  async function handleJoin(event: FormEvent) {
    event.preventDefault();
    if (!inviteCode.trim()) {
      return;
    }
    setBusy(true);
    try {
      await onJoinConversation(inviteCode.trim());
      setInviteCode("");
    } finally {
      setBusy(false);
    }
  }

  async function handleProfile(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await onUpdateProfile({
        displayName: profileName.trim(),
        username: profileUsername.trim(),
        avatar: profileAvatar,
        removeAvatar: removeProfileAvatar,
      });
      setProfileAvatar(null);
      setRemoveProfileAvatar(false);
    } finally {
      setBusy(false);
    }
  }

  async function handleConversation(event: FormEvent) {
    event.preventDefault();
    if (!conversation) {
      return;
    }
    setBusy(true);
    try {
      await onUpdateConversation(conversation.id, {
        title: conversationTitle.trim(),
        avatar: conversationAvatar,
        removeAvatar: removeConversationAvatar,
      });
      setConversationAvatar(null);
      setRemoveConversationAvatar(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop}>
      <section className={styles.drawer}>
        <div className={styles.head}>
          <div>
            <div className={styles.caption}>
              {mode === "create" && "Новый чат"}
              {mode === "join" && "Инвайт"}
              {mode === "profile" && "Профиль"}
              {mode === "conversation" && "Настройки"}
            </div>
            <h3>
              {mode === "create" && "Создать пространство"}
              {mode === "join" && "Войти по коду"}
              {mode === "profile" && "Профиль Hermes"}
              {mode === "conversation" && "Настройки чата"}
            </h3>
          </div>
          <button className={styles.close} type="button" onClick={onClose}>
            ×
          </button>
        </div>

        {mode === "create" && (
          <form className={styles.form} onSubmit={handleCreate}>
            <input value={createTitle} onChange={(event) => setCreateTitle(event.target.value)} placeholder="Название чата" />
            <button type="submit" disabled={busy}>
              Создать чат
            </button>
          </form>
        )}

        {mode === "join" && (
          <form className={styles.form} onSubmit={handleJoin}>
            <input value={inviteCode} onChange={(event) => setInviteCode(event.target.value)} placeholder="join_..." />
            <button type="submit" disabled={busy}>
              Войти в чат
            </button>
          </form>
        )}

        {mode === "profile" && (
          <form className={styles.form} onSubmit={handleProfile}>
            <input value={profileName} onChange={(event) => setProfileName(event.target.value)} placeholder="Имя в Hermes" />
            <input
              value={profileUsername}
              onChange={(event) => setProfileUsername(event.target.value)}
              placeholder="Ник в Hermes"
            />
            <label className={styles.fileField}>
              <span>Аватар</span>
              <input type="file" accept="image/*" onChange={(event) => setProfileAvatar(event.target.files?.[0] ?? null)} />
            </label>
            <label className={styles.checkbox}>
              <input
                type="checkbox"
                checked={removeProfileAvatar}
                onChange={(event) => setRemoveProfileAvatar(event.target.checked)}
              />
              <span>Удалить текущий аватар</span>
            </label>
            <button type="submit" disabled={busy}>
              Сохранить профиль
            </button>
          </form>
        )}

        {mode === "conversation" && conversation && (
          <form className={styles.form} onSubmit={handleConversation}>
            <input
              value={conversationTitle}
              onChange={(event) => setConversationTitle(event.target.value)}
              placeholder="Название чата"
            />
            <label className={styles.fileField}>
              <span>Аватар чата</span>
              <input
                type="file"
                accept="image/*"
                onChange={(event) => setConversationAvatar(event.target.files?.[0] ?? null)}
              />
            </label>
            <label className={styles.checkbox}>
              <input
                type="checkbox"
                checked={removeConversationAvatar}
                onChange={(event) => setRemoveConversationAvatar(event.target.checked)}
              />
              <span>Удалить текущий аватар</span>
            </label>
            <button
              className={styles.secondary}
              type="button"
              onClick={() =>
                void onUpdateConversation(conversation.id, {
                  title: conversationTitle.trim(),
                  muted: !conversation.muted,
                })
              }
            >
              {conversation.muted ? "Включить уведомления" : "Заглушить чат"}
            </button>
            <button type="submit" disabled={busy}>
              Сохранить чат
            </button>
            <button
              className={styles.danger}
              type="button"
              disabled={busy}
              onClick={() => void onDeleteConversation(conversation.id)}
            >
              Удалить чат
            </button>
          </form>
        )}
      </section>
    </div>
  );
}
