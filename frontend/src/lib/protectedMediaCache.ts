"use client";

import { getCacheScopeKey } from "@/lib/cacheIdentity";

const MEDIA_CACHE_VERSION = "v1";
const MEDIA_CACHE_NAME = `hermes-protected-media-${MEDIA_CACHE_VERSION}`;
const MEDIA_CACHE_DB_NAME = `${MEDIA_CACHE_NAME}-db`;
const MEDIA_CACHE_DB_VERSION = 1;
const MEDIA_CACHE_BLOB_STORE = "media-blobs";
const DEFAULT_MEDIA_CACHE_TTL_MS = 1000 * 60 * 60 * 24;
const MEDIA_CACHE_MAX_ENTRIES = 120;
const MEDIA_CACHE_META_PREFIX = `${MEDIA_CACHE_NAME}:meta:`;
const MEDIA_CACHE_CLEANUP_KEY = `${MEDIA_CACHE_NAME}:last-cleanup`;
const MEDIA_CACHE_CLEANUP_INTERVAL_MS = 1000 * 60 * 30;
const MEDIA_CACHE_SETTINGS_KEY = `${MEDIA_CACHE_NAME}:settings`;

export interface ProtectedMediaCacheSettings {
  ttlHours: number;
}

export interface ProtectedMediaCacheStats {
  entries: number;
  ttlHours: number;
  oldestCreatedAt: number | null;
  newestCreatedAt: number | null;
  totalSizeBytes: number;
}

interface CacheMetadata {
  createdAt: number;
  expiresAt: number;
  tokenFingerprint: string;
  sourceUrl: string;
  sizeBytes: number;
  pinned?: boolean;
}

function normalizeTtlHours(value: number) {
  if (!Number.isFinite(value)) {
    return 24;
  }

  return Math.min(24 * 30, Math.max(1, Math.round(value)));
}

function isBrowserReady() {
  return typeof window !== "undefined" && ("indexedDB" in window || "caches" in window);
}

function hasCacheStorage() {
  return typeof window !== "undefined" && "caches" in window;
}

function hasIndexedDb() {
  return typeof window !== "undefined" && "indexedDB" in window;
}

function metaStorageKey(cacheKey: string) {
  return `${MEDIA_CACHE_META_PREFIX}${cacheKey}`;
}

function lightweightHash(value: string) {
  let hash = 5381;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 33) ^ value.charCodeAt(index);
  }
  return (hash >>> 0).toString(16);
}

async function sha256Hex(value: string) {
  if (typeof window === "undefined" || !window.crypto?.subtle) {
    return lightweightHash(value);
  }

  try {
    const encoded = new TextEncoder().encode(value);
    const digest = await window.crypto.subtle.digest("SHA-256", encoded);
    return Array.from(new Uint8Array(digest), (part) => part.toString(16).padStart(2, "0")).join("");
  } catch {
    return lightweightHash(value);
  }
}

async function buildCacheKey(token: string, resolvedUrl: string) {
  const fingerprint = getCacheScopeKey(token);
  const urlHash = (await sha256Hex(resolvedUrl)).slice(0, 24);
  return `media:${fingerprint}:${urlHash}`;
}

async function openMediaBlobDatabase() {
  if (!hasIndexedDb()) {
    return null;
  }

  try {
    return await new Promise<IDBDatabase | null>((resolve, reject) => {
      const request = window.indexedDB.open(MEDIA_CACHE_DB_NAME, MEDIA_CACHE_DB_VERSION);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains(MEDIA_CACHE_BLOB_STORE)) {
          database.createObjectStore(MEDIA_CACHE_BLOB_STORE);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error ?? new Error("Failed to open media cache database"));
    });
  } catch {
    return null;
  }
}

async function readBlobFromIndexedDb(cacheKey: string) {
  const database = await openMediaBlobDatabase();
  if (!database) {
    return null;
  }

  try {
    return await new Promise<Blob | null>((resolve, reject) => {
      const transaction = database.transaction(MEDIA_CACHE_BLOB_STORE, "readonly");
      const store = transaction.objectStore(MEDIA_CACHE_BLOB_STORE);
      const request = store.get(cacheKey);
      request.onsuccess = () => resolve((request.result as Blob | undefined) ?? null);
      request.onerror = () => reject(request.error ?? new Error("Failed to read media blob from IndexedDB"));
      transaction.oncomplete = () => database.close();
      transaction.onerror = () => database.close();
      transaction.onabort = () => database.close();
    });
  } catch {
    database.close();
    return null;
  }
}

async function writeBlobToIndexedDb(cacheKey: string, blob: Blob) {
  const database = await openMediaBlobDatabase();
  if (!database) {
    return;
  }

  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(MEDIA_CACHE_BLOB_STORE, "readwrite");
      const store = transaction.objectStore(MEDIA_CACHE_BLOB_STORE);
      const request = store.put(blob, cacheKey);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error ?? new Error("Failed to write media blob to IndexedDB"));
      transaction.oncomplete = () => database.close();
      transaction.onerror = () => database.close();
      transaction.onabort = () => database.close();
    });
  } catch {
    database.close();
  }
}

async function deleteBlobFromIndexedDb(cacheKey: string) {
  const database = await openMediaBlobDatabase();
  if (!database) {
    return;
  }

  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(MEDIA_CACHE_BLOB_STORE, "readwrite");
      const store = transaction.objectStore(MEDIA_CACHE_BLOB_STORE);
      const request = store.delete(cacheKey);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error ?? new Error("Failed to delete media blob from IndexedDB"));
      transaction.oncomplete = () => database.close();
      transaction.onerror = () => database.close();
      transaction.onabort = () => database.close();
    });
  } catch {
    database.close();
  }
}

function toCacheRequest(cacheKey: string) {
  return new Request(new URL(`/__cache/protected-media/${encodeURIComponent(cacheKey)}`, window.location.origin).toString());
}

function readMetadata(cacheKey: string) {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(metaStorageKey(cacheKey));
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as CacheMetadata;
  } catch {
    window.localStorage.removeItem(metaStorageKey(cacheKey));
    return null;
  }
}

function writeMetadata(cacheKey: string, metadata: CacheMetadata) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(metaStorageKey(cacheKey), JSON.stringify(metadata));
}

export function getProtectedMediaCacheSettings(): ProtectedMediaCacheSettings {
  if (typeof window === "undefined") {
    return { ttlHours: 24 };
  }

  const raw = window.localStorage.getItem(MEDIA_CACHE_SETTINGS_KEY);
  if (!raw) {
    return { ttlHours: 24 };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<ProtectedMediaCacheSettings>;
    return { ttlHours: normalizeTtlHours(parsed.ttlHours ?? 24) };
  } catch {
    window.localStorage.removeItem(MEDIA_CACHE_SETTINGS_KEY);
    return { ttlHours: 24 };
  }
}

export function updateProtectedMediaCacheSettings(nextSettings: Partial<ProtectedMediaCacheSettings>) {
  if (typeof window === "undefined") {
    return { ttlHours: 24 };
  }

  const current = getProtectedMediaCacheSettings();
  const merged = {
    ttlHours: normalizeTtlHours(nextSettings.ttlHours ?? current.ttlHours),
  };
  window.localStorage.setItem(MEDIA_CACHE_SETTINGS_KEY, JSON.stringify(merged));
  return merged;
}

export async function clearProtectedMediaCache() {
  if (!isBrowserReady()) {
    return;
  }

  const cache = hasCacheStorage() ? await caches.open(MEDIA_CACHE_NAME).catch(() => null) : null;
  const entries = listMetadataEntries();
  await Promise.all(entries.map(({ cacheKey }) => deleteCachedEntry(cache, cacheKey)));
  window.localStorage.setItem(MEDIA_CACHE_CLEANUP_KEY, String(Date.now()));
}

export function inspectProtectedMediaCacheStats(): ProtectedMediaCacheStats {
  const entries = listMetadataEntries();
  const createdAtValues = entries.map((entry) => entry.metadata.createdAt).sort((left, right) => left - right);
  return {
    entries: entries.length,
    ttlHours: getProtectedMediaCacheSettings().ttlHours,
    oldestCreatedAt: createdAtValues[0] ?? null,
    newestCreatedAt: createdAtValues[createdAtValues.length - 1] ?? null,
    totalSizeBytes: entries.reduce((sum, entry) => sum + Math.max(0, entry.metadata.sizeBytes || 0), 0),
  };
}

function deleteMetadata(cacheKey: string) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(metaStorageKey(cacheKey));
}

function listMetadataEntries() {
  if (typeof window === "undefined") {
    return [];
  }

  const entries: Array<{ cacheKey: string; metadata: CacheMetadata }> = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (!key?.startsWith(MEDIA_CACHE_META_PREFIX)) {
      continue;
    }

    const cacheKey = key.slice(MEDIA_CACHE_META_PREFIX.length);
    const metadata = readMetadata(cacheKey);
    if (!metadata) {
      continue;
    }

    entries.push({ cacheKey, metadata });
  }

  return entries;
}

async function deleteCachedEntry(cache: Cache | null, cacheKey: string) {
  if (cache) {
    await cache.delete(toCacheRequest(cacheKey));
  }
  await deleteBlobFromIndexedDb(cacheKey);
  deleteMetadata(cacheKey);
}

async function pruneExpiredEntries(cache: Cache | null, now: number) {
  const entries = listMetadataEntries();
  await Promise.all(
    entries
      .filter(({ metadata }) => !metadata.pinned && metadata.expiresAt <= now)
      .map(({ cacheKey }) => deleteCachedEntry(cache, cacheKey)),
  );
}

async function pruneOverflowEntries(cache: Cache | null) {
  const entries = listMetadataEntries();
  if (entries.length <= MEDIA_CACHE_MAX_ENTRIES) {
    return;
  }

  const overflow = entries.length - MEDIA_CACHE_MAX_ENTRIES;
  const victims = [...entries]
    .filter(({ metadata }) => !metadata.pinned)
    .sort((left, right) => left.metadata.createdAt - right.metadata.createdAt)
    .slice(0, overflow);

  await Promise.all(victims.map(({ cacheKey }) => deleteCachedEntry(cache, cacheKey)));
}

export async function cleanupProtectedMediaCache(force = false) {
  if (!isBrowserReady()) {
    return;
  }

  const now = Date.now();
  const lastCleanup = Number(window.localStorage.getItem(MEDIA_CACHE_CLEANUP_KEY) ?? "0");
  if (!force && now - lastCleanup < MEDIA_CACHE_CLEANUP_INTERVAL_MS) {
    return;
  }

  const cache = hasCacheStorage() ? await caches.open(MEDIA_CACHE_NAME).catch(() => null) : null;
  await pruneExpiredEntries(cache, now);
  await pruneOverflowEntries(cache);
  window.localStorage.setItem(MEDIA_CACHE_CLEANUP_KEY, String(now));
}

export async function getProtectedMediaBlob(
  token: string,
  resolvedUrl: string,
  fetcher: () => Promise<Response>,
) {
  if (!isBrowserReady()) {
    const response = await fetcher();
    return response.blob();
  }

  await cleanupProtectedMediaCache().catch(() => undefined);

  const cache = hasCacheStorage() ? await caches.open(MEDIA_CACHE_NAME).catch(() => null) : null;
  const cacheKey = await buildCacheKey(token, resolvedUrl);
  const metadata = readMetadata(cacheKey);
  const now = Date.now();
  const ttlHours = getProtectedMediaCacheSettings().ttlHours;
  const pinned = isPinnedProtectedMediaUrl(resolvedUrl);
  const cacheScope = getCacheScopeKey(token);

  if (metadata && metadata.expiresAt > now) {
    const cachedBlob =
      (cache
        ? await cache
            .match(toCacheRequest(cacheKey))
            .then((response) => response?.blob() ?? null)
            .catch(() => null)
        : null) ?? (await readBlobFromIndexedDb(cacheKey));

    if (cachedBlob) {
      if (!metadata.sizeBytes || metadata.tokenFingerprint !== cacheScope) {
        writeMetadata(cacheKey, {
          ...metadata,
          tokenFingerprint: cacheScope,
          sizeBytes: cachedBlob.size,
        });
      }
      return cachedBlob;
    }

    deleteMetadata(cacheKey);
  }

  const response = await fetcher();
  const blob = await response.blob();
  if (cache) {
    const contentType = response.headers.get("Content-Type") ?? blob.type ?? "application/octet-stream";
    await cache
      .put(
        toCacheRequest(cacheKey),
        new Response(blob, {
          headers: {
            "Content-Type": contentType,
          },
        }),
      )
      .catch(() => undefined);
  }
  await writeBlobToIndexedDb(cacheKey, blob);
  writeMetadata(cacheKey, {
    createdAt: now,
    expiresAt: pinned ? Number.MAX_SAFE_INTEGER : now + ttlHours * 60 * 60 * 1000,
    tokenFingerprint: cacheScope,
    sourceUrl: resolvedUrl,
    sizeBytes: blob.size,
    pinned,
  });
  await pruneOverflowEntries(cache);
  return blob;
}

function isPinnedProtectedMediaUrl(resolvedUrl: string) {
  try {
    const pathname = new URL(resolvedUrl, window.location.origin).pathname;
    return /^\/api\/conversations\/\d+\/avatar$/.test(pathname);
  } catch {
    return false;
  }
}
