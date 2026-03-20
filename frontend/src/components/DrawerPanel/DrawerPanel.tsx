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
  inline?: boolean;
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
  inline = false,
}: DrawerPanelProps) {
  const [createTitle, setCreateTitle] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [profileName, setProfileName] = useState(me.displayName);
  const [profileUsername, setProfileUsername] = useState(me.username);
  const [profileAvatar, setProfileAvatar] = useState<File | null>(null);
  const [removeProfileAvatar, setRemoveProfileAvatar] = useState(false);
  const [conversationTitle, setConversationTitle] = useState(conversation?.title ?? "");
  const [conversationAvatar, setConversationAvatar] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    setProfileName(me.displayName);
    setProfileUsername(me.username);
    setConversationTitle(conversation?.title ?? "");
    setErrorMessage("");
  }, [conversation?.title, me.displayName, me.username, mode, open]);

  if (!open || !mode) {
    return null;
  }

  const canManageConversation =
    conversation?.membershipRole === "OWNER" || conversation?.membershipRole === "ADMIN";

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!createTitle.trim()) {
      return;
    }
    setBusy(true);
    try {
      setErrorMessage("");
      await onCreateConversation(createTitle.trim());
      setCreateTitle("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось создать чат.");
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
      setErrorMessage("");
      await onJoinConversation(inviteCode.trim());
      setInviteCode("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось войти по коду.");
    } finally {
      setBusy(false);
    }
  }

  async function handleProfile(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateProfile({
        displayName: profileName.trim(),
        username: profileUsername.trim(),
        avatar: profileAvatar,
        removeAvatar: removeProfileAvatar,
      });
      setProfileAvatar(null);
      setRemoveProfileAvatar(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить профиль.");
    } finally {
      setBusy(false);
    }
  }

  async function handleConversation(event: FormEvent) {
    event.preventDefault();
    if (!conversation || !canManageConversation) {
      if (!canManageConversation) {
        setErrorMessage("Только owner или admin могут менять фото и название чата.");
      }
      return;
    }
    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateConversation(conversation.id, {
        title: conversationTitle.trim(),
        avatar: conversationAvatar,
      });
      setConversationAvatar(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить чат.");
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleMute() {
    if (!conversation) {
      return;
    }

    setBusy(true);
    try {
      setErrorMessage("");
      await onUpdateConversation(conversation.id, {
        title: conversationTitle.trim(),
        muted: !conversation.muted,
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось обновить уведомления.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteConversation() {
    if (!conversation || !canManageConversation) {
      setErrorMessage("Только owner или admin могут удалить чат.");
      return;
    }

    setBusy(true);
    try {
      setErrorMessage("");
      await onDeleteConversation(conversation.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Не удалось удалить чат.");
    } finally {
      setBusy(false);
    }
  }

  const rootClassName = inline ? styles.inlineRoot : styles.backdrop;
  const drawerClassName = inline ? `${styles.drawer} ${styles.inlineDrawer}` : styles.drawer;

  return (
    <div className={rootClassName}>
      <section className={drawerClassName}>
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

        {errorMessage && <div className={styles.errorBanner}>{errorMessage}</div>}

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
            {!canManageConversation && (
              <div className={styles.helperNote}>
                Фото, название и удаление чата доступны только ролям <strong>OWNER</strong> или <strong>ADMIN</strong>.
              </div>
            )}
            <input
              value={conversationTitle}
              onChange={(event) => setConversationTitle(event.target.value)}
              placeholder="Название чата"
              disabled={!canManageConversation || busy}
            />
            <label className={styles.fileField}>
              <span>Аватар чата</span>
              <input
                type="file"
                accept="image/*"
                disabled={!canManageConversation || busy}
                onChange={(event) => setConversationAvatar(event.target.files?.[0] ?? null)}
              />
            </label>
            <button
              className={styles.secondary}
              type="button"
              disabled={busy}
              onClick={() => void handleToggleMute()}
            >
              {conversation.muted ? "Включить уведомления" : "Заглушить чат"}
            </button>
            <button type="submit" disabled={!canManageConversation || busy}>
              Сохранить чат
            </button>
            <button
              className={styles.danger}
              type="button"
              disabled={!canManageConversation || busy}
              onClick={() => void handleDeleteConversation()}
            >
              Удалить чат
            </button>
          </form>
        )}
      </section>
    </div>
  );
}
