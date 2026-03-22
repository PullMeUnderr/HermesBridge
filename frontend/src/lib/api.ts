import { getProtectedMediaBlob } from "@/lib/protectedMediaCache"

const ACCESS_TOKEN_KEY = "hermes_access_token"

export interface AuthSessionResponse<TUser> {
  accessToken: string
  accessTokenExpiresAt: string | null
  user: TUser
}

export interface AuthRefreshResponse {
  accessToken: string
  accessTokenExpiresAt: string | null
}

function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_BASE_URL?.trim() ?? ""
}

function shouldAttachNgrokBypassHeader(hostname: string) {
  return /\.ngrok(-free)?\.(app|dev|io|pizza)$/i.test(hostname)
}

function resolveUrl(path: string) {
  const base = getApiBaseUrl()
  if (!base) {
    return path
  }

  return new URL(path, base).toString()
}

function buildHeaders(token: string, init?: HeadersInit, isFormData?: boolean) {
  const hostname = typeof window === "undefined" ? "" : window.location.hostname
  return {
    Authorization: `Bearer ${token}`,
    ...(shouldAttachNgrokBypassHeader(hostname)
      ? { "ngrok-skip-browser-warning": "true" }
      : {}),
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
    ...(init ?? {}),
  }
}

export function readStoredAccessToken() {
  if (typeof window === "undefined") {
    return ""
  }

  return window.localStorage.getItem(ACCESS_TOKEN_KEY) ?? ""
}

export function writeStoredAccessToken(token: string) {
  if (typeof window === "undefined") {
    return
  }

  if (token) {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, token)
    return
  }

  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
}

async function readJsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const payload = await response.json()
      message = payload.message ?? message
    } catch {
      // ignore body parse failures
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return null as T
  }

  return response.json() as Promise<T>
}

export async function exchangeBootstrapToken<TUser>(token: string) {
  const response = await fetch(resolveUrl("/api/auth/session"), {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ token }),
  })

  return readJsonOrThrow<AuthSessionResponse<TUser>>(response)
}

export async function registerHermesAccount<TUser>(payload: {
  username: string
  displayName: string
  password: string
}) {
  const response = await fetch(resolveUrl("/api/auth/register"), {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  })

  return readJsonOrThrow<AuthSessionResponse<TUser>>(response)
}

export async function loginHermesAccount<TUser>(payload: {
  username: string
  password: string
}) {
  const response = await fetch(resolveUrl("/api/auth/login"), {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  })

  return readJsonOrThrow<AuthSessionResponse<TUser>>(response)
}

export async function refreshAccessToken() {
  const response = await fetch(resolveUrl("/api/auth/refresh"), {
    method: "POST",
    credentials: "include",
  })

  return readJsonOrThrow<AuthRefreshResponse>(response)
}

export async function startTelegramLink(token: string) {
  return apiRequest<{ code: string; expiresAt: string }>(token, "/api/auth/link/telegram/start", {
    method: "POST",
  })
}

export async function completeHermesRegistration<TUser>(
  token: string,
  payload: { username: string; displayName: string; password: string },
) {
  return apiRequest<TUser>(token, "/api/auth/me/complete-registration", {
    method: "POST",
    body: JSON.stringify(payload),
  })
}

export async function logoutSession() {
  const response = await fetch(resolveUrl("/api/auth/logout"), {
    method: "POST",
    credentials: "include",
  })

  return readJsonOrThrow<void>(response)
}

export async function apiRequest<T>(
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const isFormData = options.body instanceof FormData
  const response = await fetch(resolveUrl(path), {
    ...options,
    credentials: "include",
    headers: buildHeaders(token, options.headers, isFormData),
  })

  return readJsonOrThrow<T>(response)
}

export async function fetchProtectedBlobUrl(token: string, src: string) {
  if (!src) {
    return null
  }

  const url =
    src.startsWith("http://") || src.startsWith("https://")
      ? src
      : resolveUrl(src)
  const blob = await getProtectedMediaBlob(token, url, async () => {
    const response = await fetch(url, {
      credentials: "include",
      headers: buildHeaders(token),
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    return response
  })

  return URL.createObjectURL(blob)
}
