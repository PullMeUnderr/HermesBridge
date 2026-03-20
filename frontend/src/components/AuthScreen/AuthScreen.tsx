"use client";

import { FormEvent, useState } from "react";
import styles from "./AuthScreen.module.scss";

interface AuthScreenProps {
  initialToken: string;
  submitting: boolean;
  onSubmit: (token: string) => Promise<void>;
}

export function AuthScreen({ initialToken, submitting, onSubmit }: AuthScreenProps) {
  const [token, setToken] = useState(initialToken);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit(token.trim());
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
              <strong>Регистрация через бота</strong>
              <span>Вход по токену без ручной настройки.</span>
            </div>
            <div>
              <strong>Синхронизация чатов</strong>
              <span>Один диалог для Hermes и Telegram.</span>
            </div>
            <div>
              <strong>Медиа и роли</strong>
              <span>Фото, видео, голосовые и доступ по ролям.</span>
            </div>
          </article>
        </div>
      </section>

      <section className={styles.card}>
        <div className={styles.eyebrow}>Вход</div>
        <h2>Открой Hermes</h2>
        <p className={styles.muted}>
          Вставь токен от <strong>@HermesBridgeBot</strong>, чтобы открыть клиент.
        </p>
        <form className={styles.form} onSubmit={handleSubmit}>
          <label className={styles.field}>
            <span>Токен доступа</span>
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
        <div className={styles.note}>
          <strong>Первый вход</strong>
          <p>
            Если токена ещё нет, открой бота, нажми <strong>/start</strong> и вставь выданный ключ сюда.
          </p>
        </div>
      </section>
    </section>
  );
}
