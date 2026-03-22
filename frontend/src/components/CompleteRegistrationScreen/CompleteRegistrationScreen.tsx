"use client";

import { FormEvent, useState } from "react";
import styles from "./CompleteRegistrationScreen.module.scss";
import type { AuthUser } from "@/types/api";

interface CompleteRegistrationScreenProps {
  me: AuthUser;
  submitting: boolean;
  onSubmit: (payload: { username: string; displayName: string; password: string }) => Promise<void>;
  onLogout: () => void;
}

export function CompleteRegistrationScreen({
  me,
  submitting,
  onSubmit,
  onLogout,
}: CompleteRegistrationScreenProps) {
  const [username, setUsername] = useState(me.username);
  const [displayName, setDisplayName] = useState(me.displayName);
  const [password, setPassword] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit({
      username: username.trim(),
      displayName: displayName.trim(),
      password,
    });
  }

  return (
    <section className={styles.layout}>
      <section className={styles.card}>
        <div className={styles.eyebrow}>Telegram-first</div>
        <h1>Заверши регистрацию в Hermes</h1>
        <p className={styles.muted}>
          Аккаунт уже создан через Telegram. Осталось подтвердить имя, выбрать Hermes username и задать пароль для
          обычного входа через сайт.
        </p>

        <div className={styles.identityBlock}>
          <strong>{me.displayName}</strong>
          <span>@{me.username}</span>
          <span>{me.telegramUsername ? `Telegram: @${me.telegramUsername}` : "Telegram уже привязан"}</span>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <label className={styles.field}>
            <span>Имя в Hermes</span>
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Hermes User" />
          </label>
          <label className={styles.field}>
            <span>Hermes username</span>
            <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="username" />
          </label>
          <label className={styles.field}>
            <span>Пароль</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Минимум 8 символов"
            />
          </label>
          <button type="submit" disabled={submitting}>
            {submitting ? "Сохраняем..." : "Завершить регистрацию"}
          </button>
        </form>

        <button className={styles.secondary} type="button" onClick={onLogout} disabled={submitting}>
          Выйти
        </button>
      </section>
    </section>
  );
}
