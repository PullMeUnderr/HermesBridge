"use client";

import { getCacheScopeKey } from "@/lib/cacheIdentity";
import type { ConversationMessage } from "@/types/api";

const CHANNEL_MESSAGE_CACHE_VERSION = "v1";
const CHANNEL_MESSAGE_CACHE_PREFIX = `hermes-channel-message-cache:${CHANNEL_MESSAGE_CACHE_VERSION}:`;
const CHANNEL_MESSAGE_CACHE_LIMIT_PER_CONVERSATION = 200;
const CHANNEL_MESSAGE_CACHE_MAX_CONVERSATIONS = 40;

interface ChannelMessageCacheEntry {
  conversationId: number;
  messages: ConversationMessage[];
  updatedAt: number;
  sizeBytes: number;
}

export interface ChannelMessageCacheStats {
  conversations: number;
  messages: number;
  newestUpdatedAt: number | null;
  totalSizeBytes: number;
}

function isBrowserReady() {
  return typeof window !== "undefined";
}

function cacheKey(scope: string, conversationId: number) {
  return `${CHANNEL_MESSAGE_CACHE_PREFIX}${scope}:${conversationId}`;
}

function listScopeEntries(scope: string) {
  if (!isBrowserReady()) {
    return [];
  }

  const prefix = `${CHANNEL_MESSAGE_CACHE_PREFIX}${scope}:`;
  const entries: ChannelMessageCacheEntry[] = [];

  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (!key?.startsWith(prefix)) {
      continue;
    }

    const raw = window.localStorage.getItem(key);
    if (!raw) {
      continue;
    }

    try {
      const parsed = JSON.parse(raw) as ChannelMessageCacheEntry;
      if (!Array.isArray(parsed.messages)) {
        window.localStorage.removeItem(key);
        continue;
      }
      entries.push(parsed);
    } catch {
      window.localStorage.removeItem(key);
    }
  }

  return entries;
}

function writeEntry(scope: string, conversationId: number, messages: ConversationMessage[]) {
  if (!isBrowserReady()) {
    return;
  }

  const trimmed = [...messages]
    .sort((left, right) => {
      if (left.createdAt === right.createdAt) {
        return left.id - right.id;
      }
      return left.createdAt.localeCompare(right.createdAt);
    })
    .slice(-CHANNEL_MESSAGE_CACHE_LIMIT_PER_CONVERSATION);

  const payload: ChannelMessageCacheEntry = {
    conversationId,
    messages: trimmed,
    updatedAt: Date.now(),
    sizeBytes: new TextEncoder().encode(JSON.stringify(trimmed)).length,
  };

  window.localStorage.setItem(cacheKey(scope, conversationId), JSON.stringify(payload));
  pruneOverflow(scope);
}

function pruneOverflow(scope: string) {
  const entries = listScopeEntries(scope);
  if (entries.length <= CHANNEL_MESSAGE_CACHE_MAX_CONVERSATIONS) {
    return;
  }

  const victims = [...entries]
    .sort((left, right) => left.updatedAt - right.updatedAt)
    .slice(0, entries.length - CHANNEL_MESSAGE_CACHE_MAX_CONVERSATIONS);

  for (const victim of victims) {
    window.localStorage.removeItem(cacheKey(scope, victim.conversationId));
  }
}

export function readChannelMessageCache(token: string, conversationId: number) {
  if (!isBrowserReady() || !token || !Number.isFinite(conversationId)) {
    return null;
  }

  const raw = window.localStorage.getItem(cacheKey(getCacheScopeKey(token), conversationId));
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as ChannelMessageCacheEntry;
    return Array.isArray(parsed.messages) ? parsed.messages : null;
  } catch {
    window.localStorage.removeItem(cacheKey(getCacheScopeKey(token), conversationId));
    return null;
  }
}

export function writeChannelMessageCache(token: string, conversationId: number, messages: ConversationMessage[]) {
  if (!token || !Number.isFinite(conversationId)) {
    return;
  }

  writeEntry(getCacheScopeKey(token), conversationId, messages);
}

export function upsertChannelMessageCache(token: string, message: ConversationMessage) {
  if (!token || !message?.conversationId) {
    return;
  }

  const scope = getCacheScopeKey(token);
  const current = readChannelMessageCache(token, message.conversationId) ?? [];
  const next = [...current.filter((item) => item.id !== message.id), message];
  writeEntry(scope, message.conversationId, next);
}

export function removeChannelMessageCache(token: string, conversationId: number) {
  if (!isBrowserReady() || !token || !Number.isFinite(conversationId)) {
    return;
  }

  window.localStorage.removeItem(cacheKey(getCacheScopeKey(token), conversationId));
}

export function clearChannelMessageCache(token: string) {
  if (!isBrowserReady() || !token) {
    return;
  }

  for (const entry of listScopeEntries(getCacheScopeKey(token))) {
    window.localStorage.removeItem(cacheKey(getCacheScopeKey(token), entry.conversationId));
  }
}

export function inspectChannelMessageCacheStats(token: string): ChannelMessageCacheStats {
  if (!token) {
    return {
      conversations: 0,
      messages: 0,
      newestUpdatedAt: null,
      totalSizeBytes: 0,
    };
  }

  const entries = listScopeEntries(getCacheScopeKey(token));
  return {
    conversations: entries.length,
    messages: entries.reduce((sum, entry) => sum + entry.messages.length, 0),
    newestUpdatedAt: entries.reduce<number | null>(
      (latest, entry) => (latest === null || entry.updatedAt > latest ? entry.updatedAt : latest),
      null,
    ),
    totalSizeBytes: entries.reduce((sum, entry) => sum + entry.sizeBytes, 0),
  };
}
