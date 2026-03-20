"use client";

const MEDIA_CACHE_VERSION = "v1";
const MEDIA_CACHE_NAME = `hermes-protected-media-${MEDIA_CACHE_VERSION}`;
const MEDIA_CACHE_TTL_MS = 1000 * 60 * 60 * 24;
const MEDIA_CACHE_MAX_ENTRIES = 120;
const MEDIA_CACHE_META_PREFIX = `${MEDIA_CACHE_NAME}:meta:`;
const MEDIA_CACHE_CLEANUP_KEY = `${MEDIA_CACHE_NAME}:last-cleanup`;
const MEDIA_CACHE_CLEANUP_INTERVAL_MS = 1000 * 60 * 30;

interface CacheMetadata {
  createdAt: number;
  expiresAt: number;
  tokenFingerprint: string;
  sourceUrl: string;
}

function isBrowserReady() {
  return typeof window !== "undefined" && "caches" in window;
}

function metaStorageKey(cacheKey: string) {
  return `${MEDIA_CACHE_META_PREFIX}${cacheKey}`;
}

async function sha256Hex(value: string) {
  const encoded = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return Array.from(new Uint8Array(digest), (part) => part.toString(16).padStart(2, "0")).join("");
}

async function tokenFingerprint(token: string) {
  if (!token) {
    return "anonymous";
  }

  const digest = await sha256Hex(token);
  return digest.slice(0, 24);
}

async function buildCacheKey(token: string, resolvedUrl: string) {
  const fingerprint = await tokenFingerprint(token);
  const urlHash = (await sha256Hex(resolvedUrl)).slice(0, 24);
  return `media:${fingerprint}:${urlHash}`;
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

async function deleteCachedEntry(cache: Cache, cacheKey: string) {
  await cache.delete(toCacheRequest(cacheKey));
  deleteMetadata(cacheKey);
}

async function pruneExpiredEntries(cache: Cache, now: number) {
  const entries = listMetadataEntries();
  await Promise.all(
    entries
      .filter(({ metadata }) => metadata.expiresAt <= now)
      .map(({ cacheKey }) => deleteCachedEntry(cache, cacheKey)),
  );
}

async function pruneOverflowEntries(cache: Cache) {
  const entries = listMetadataEntries();
  if (entries.length <= MEDIA_CACHE_MAX_ENTRIES) {
    return;
  }

  const overflow = entries.length - MEDIA_CACHE_MAX_ENTRIES;
  const victims = [...entries]
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

  const cache = await caches.open(MEDIA_CACHE_NAME);
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

  await cleanupProtectedMediaCache();

  const cache = await caches.open(MEDIA_CACHE_NAME);
  const cacheKey = await buildCacheKey(token, resolvedUrl);
  const metadata = readMetadata(cacheKey);
  const now = Date.now();

  if (metadata && metadata.expiresAt > now) {
    const cachedResponse = await cache.match(toCacheRequest(cacheKey));
    if (cachedResponse) {
      return cachedResponse.blob();
    }

    deleteMetadata(cacheKey);
  }

  const response = await fetcher();
  const responseForCache = response.clone();
  await cache.put(toCacheRequest(cacheKey), responseForCache);
  writeMetadata(cacheKey, {
    createdAt: now,
    expiresAt: now + MEDIA_CACHE_TTL_MS,
    tokenFingerprint: await tokenFingerprint(token),
    sourceUrl: resolvedUrl,
  });
  await pruneOverflowEntries(cache);
  return response.blob();
}
