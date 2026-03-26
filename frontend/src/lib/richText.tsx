import { Fragment } from "react"
import type { ReactNode } from "react"

interface RichTextClassNames {
  link?: string
  mention?: string
  mentionSelf?: string
}

const urlPattern =
  /(https?:\/\/[^\s<]+|ftp:\/\/[^\s<]+|file:\/\/[^\s<]+|tg:\/\/[^\s<]+|mailto:[^\s<]+|tel:[+\d()[\]\-\s]+|sms:[+\d()[\]\-\s]+|(?:www\.|(?:t\.me|telegram\.me)\/)[^\s<]+)/gi
const markdownLinkPattern = /\[([^\]]+)\]\(([^)\s]+)\)/g

function trimTrailingPunctuation(url: string) {
  let trimmed = url
  while (/[),.!?:;]+$/.test(trimmed)) {
    trimmed = trimmed.slice(0, -1)
  }
  return trimmed
}

function normalizeHref(rawUrl: string) {
  const trimmed = trimTrailingPunctuation(rawUrl.trim())
  const lower = trimmed.toLowerCase()

  if (
    lower.startsWith("http://") ||
    lower.startsWith("https://") ||
    lower.startsWith("ftp://") ||
    lower.startsWith("file://") ||
    lower.startsWith("tg://") ||
    lower.startsWith("mailto:") ||
    lower.startsWith("tel:") ||
    lower.startsWith("sms:")
  ) {
    return trimmed
  }

  return `https://${trimmed}`
}

function pushLink(
  parts: ReactNode[],
  key: string,
  label: string,
  rawUrl: string,
  classNames: RichTextClassNames,
) {
  const href = normalizeHref(rawUrl)
  const external = !href.toLowerCase().startsWith("file://")

  parts.push(
    <a
      key={key}
      className={classNames.link}
      href={href}
      target={external ? "_blank" : undefined}
      rel={external ? "noopener noreferrer" : undefined}
    >
      {formatLinkLabel(label)}
    </a>,
  )
}

function formatLinkLabel(url: string) {
  try {
    return decodeURI(url)
  } catch {
    return url
  }
}

function joinClassNames(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(" ")
}

function renderMentionText(
  text: string,
  ownUsername: string | null,
  classNames: RichTextClassNames,
) {
  const mentionPattern = /(^|[^A-Za-z0-9_])@([A-Za-z0-9_]{1,100})/g
  const parts: ReactNode[] = []
  let cursor = 0
  let index = 0

  for (const match of text.matchAll(mentionPattern)) {
    const prefix = match[1] ?? ""
    const username = match[2] ?? ""
    const startIndex = match.index ?? 0
    const mentionStart = startIndex + prefix.length
    const mentionEnd = mentionStart + username.length + 1
    const isSelf = ownUsername && username.toLowerCase() === ownUsername.toLowerCase()

    if (startIndex > cursor) {
      parts.push(text.slice(cursor, startIndex))
    }

    parts.push(prefix)
    parts.push(
      <span
        key={`mention-${index++}`}
        className={joinClassNames(classNames.mention, isSelf && classNames.mentionSelf)}
      >
        @{username}
      </span>,
    )
    cursor = mentionEnd
  }

  if (cursor < text.length) {
    parts.push(text.slice(cursor))
  }

  return parts
}

export function renderRichText(
  body: string,
  ownUsername: string | null,
  classNames: RichTextClassNames = {},
) {
  const lines = String(body).split("\n")

  return lines.map((line, lineIndex) => {
    const parts: ReactNode[] = []
    let cursor = 0
    let index = 0

    for (const markdownMatch of line.matchAll(markdownLinkPattern)) {
      const markdownStart = markdownMatch.index ?? 0
      const markdownEnd = markdownStart + markdownMatch[0].length
      const markdownLabel = markdownMatch[1] ?? ""
      const markdownUrl = markdownMatch[2] ?? ""

      for (const urlMatch of line.slice(cursor, markdownStart).matchAll(urlPattern)) {
        const matchedUrl = urlMatch[0]
        const label = trimTrailingPunctuation(matchedUrl)
        const suffix = matchedUrl.slice(label.length)
        const startIndex = cursor + (urlMatch.index ?? 0)
        const endIndex = startIndex + matchedUrl.length

        if (startIndex > cursor) {
          parts.push(...renderMentionText(line.slice(cursor, startIndex), ownUsername, classNames))
        }

        pushLink(parts, `link-${lineIndex}-${index++}`, label, matchedUrl, classNames)
        if (suffix) {
          parts.push(suffix)
        }
        cursor = endIndex
      }

      if (markdownStart > cursor) {
        parts.push(...renderMentionText(line.slice(cursor, markdownStart), ownUsername, classNames))
      }

      pushLink(parts, `markdown-link-${lineIndex}-${index++}`, markdownLabel, markdownUrl, classNames)
      cursor = markdownEnd
    }

    for (const match of line.slice(cursor).matchAll(urlPattern)) {
      const matchedUrl = match[0]
      const label = trimTrailingPunctuation(matchedUrl)
      const suffix = matchedUrl.slice(label.length)
      const startIndex = cursor + (match.index ?? 0)
      const endIndex = startIndex + matchedUrl.length

      if (startIndex > cursor) {
        parts.push(...renderMentionText(line.slice(cursor, startIndex), ownUsername, classNames))
      }

      pushLink(parts, `link-${lineIndex}-${index++}`, label, matchedUrl, classNames)
      if (suffix) {
        parts.push(suffix)
      }
      cursor = endIndex
    }

    if (cursor < line.length) {
      parts.push(...renderMentionText(line.slice(cursor), ownUsername, classNames))
    }

    return (
      <Fragment key={`line-${lineIndex}`}>
        {parts.length > 0 ? parts : line}
        {lineIndex < lines.length - 1 && <br />}
      </Fragment>
    )
  })
}
