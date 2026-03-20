const TOKEN_KEY = "hermes_token"

function getApiBaseUrl() {
  // return process.env.NEXT_PUBLIC_API_BASE_URL?.trim() ?? "";
  return "https://hermesbridge.space/"
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

export function readStoredToken() {
  if (typeof window === "undefined") {
    return ""
  }

  return window.localStorage.getItem(TOKEN_KEY) ?? ""
}

export function writeStoredToken(token: string) {
  if (typeof window === "undefined") {
    return
  }

  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token)
    return
  }

  window.localStorage.removeItem(TOKEN_KEY)
}

export async function apiRequest<T>(
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const isFormData = options.body instanceof FormData
  const response = await fetch(resolveUrl(path), {
    ...options,
    headers: buildHeaders(token, options.headers, isFormData),
  })

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

export async function fetchProtectedBlobUrl(token: string, src: string) {
  if (!src) {
    return null
  }

  const url =
    src.startsWith("http://") || src.startsWith("https://")
      ? src
      : resolveUrl(src)
  const response = await fetch(url, {
    headers: buildHeaders(token),
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const blob = await response.blob()
  return URL.createObjectURL(blob)
}
