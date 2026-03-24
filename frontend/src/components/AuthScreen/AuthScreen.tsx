"use client";

import { FormEvent, useState } from "react";
import styles from "./AuthScreen.module.scss";

interface AuthScreenProps {
  initialToken: string;
  submitting: boolean;
  onTokenSubmit: (token: string) => Promise<void>;
  onPasswordLogin: (payload: { username: string; password: string }) => Promise<void>;
  onRegister: (payload: { username: string; displayName: string; password: string }) => Promise<void>;
}

export function AuthScreen({
  initialToken,
  submitting,
  onTokenSubmit,
  onPasswordLogin,
  onRegister,
}: AuthScreenProps) {
  const [token, setToken] = useState(initialToken);
  const [mode, setMode] = useState<"token" | "login" | "register">("login");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  async function handleTokenSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onTokenSubmit(token.trim());
  }

  async function handlePasswordLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onPasswordLogin({
      username: username.trim(),
      password,
    });
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onRegister({
      username: username.trim(),
      displayName: displayName.trim(),
      password,
    });
  }

  return (
    <section className={styles.layout}>
      <section className={styles.hero}>
        <div className={styles.copy}>
          <div className={styles.eyebrow}>Hermes Bridge</div>
          <h1>Hermes. Мессенджер без VPN, где Telegram остаётся на связи.</h1>
          <p>
            Даже если Telegram ограничивают, Hermes остаётся рабочим контуром общения: сообщения,
            медиа и участники продолжают синхронизироваться с подключёнными Telegram-чатами.
          </p>
          <div className={styles.tags}>
            <span>Private chat</span>
            <span>Telegram sync</span>
            <span>Media ready</span>
          </div>
        </div>

        <div className={styles.previewGrid}>
          <article className={styles.deviceCard}>
            <div className={styles.dots}>
              <span />
              <span />
              <span />
            </div>
            <div className={styles.bubbles}>
              <div className={styles.bubble}>
                <strong>Telegram sync</strong>
                <span>Новое сообщение доставлено в Hermes.</span>
              </div>
              <div className={`${styles.bubble} ${styles.outgoing}`}>
                <strong>Hermes</strong>
                <span>Чат, доступ и медиа работают в одном контуре.</span>
              </div>
            </div>
          </article>

          <article className={styles.featureGrid}>
            <div>
              <strong>Hermes account</strong>
              <span>Регистрация через сайт без обязательного Telegram.</span>
            </div>
            <div>
              <strong>Telegram как канал</strong>
              <span>Бот остаётся рабочим входом и integration-слоем.</span>
            </div>
            <div>
              <strong>Единый account layer</strong>
              <span>Сессии сайта и identity не смешиваются между собой.</span>
            </div>
          </article>
        </div>
      </section>

      <section className={styles.card}>
        <div className={styles.eyebrow}>Вход</div>
        <h2>Открой Hermes</h2>
        <p className={styles.muted}>Теперь можно войти через Hermes account или через Telegram bootstrap token.</p>
        <div className={styles.segmented}>
          <button type="button" className={mode === "login" ? styles.segmentActive : ""} onClick={() => setMode("login")}>
            Hermes login
          </button>
          <button
            type="button"
            className={mode === "register" ? styles.segmentActive : ""}
            onClick={() => setMode("register")}
          >
            Регистрация
          </button>
          <button type="button" className={mode === "token" ? styles.segmentActive : ""} onClick={() => setMode("token")}>
            Telegram token
          </button>
        </div>

        {mode === "token" && (
          <form className={styles.form} onSubmit={handleTokenSubmit}>
            <label className={styles.field}>
              <span>Bootstrap token</span>
              <textarea
                rows={4}
                placeholder="tgc_..."
                value={token}
                onChange={(event) => setToken(event.target.value)}
              />
            </label>
            <button type="submit" disabled={submitting}>
              {submitting ? "Проверяем..." : "Войти по токену"}
            </button>
          </form>
        )}

        {mode === "login" && (
          <form className={styles.form} onSubmit={handlePasswordLogin}>
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
              {submitting ? "Проверяем..." : "Войти в Hermes"}
            </button>
          </form>
        )}

        {mode === "register" && (
          <form className={styles.form} onSubmit={handleRegister}>
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
              {submitting ? "Создаём..." : "Создать Hermes account"}
            </button>
          </form>
        )}
        <div className={styles.note}>
          <strong>Первый вход</strong>
          <p>
            Если нужен Telegram-first flow, он остаётся рабочим: открой бота, нажми <strong>/start</strong> и вставь
            выданный `tgc_...` токен. Если начинаешь с сайта, Telegram можно привязать позже.
          </p>
        </div>
      </section>
    </section>
  );
}
