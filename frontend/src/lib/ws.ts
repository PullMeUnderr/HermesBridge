import type {
  ConversationMessage,
  ConversationReadPayload,
  ConversationSocketEvent,
  ConversationSocketSummaryPayload,
  ConversationTypingPayload,
} from "@/types/api";

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isConversationSocketEvent(
  value: unknown,
): value is ConversationSocketEvent<
  ConversationMessage | ConversationReadPayload | ConversationSocketSummaryPayload | ConversationTypingPayload
> {
  if (!isObjectRecord(value)) {
    return false;
  }

  return (
    typeof value.type === "string" &&
    typeof value.eventId === "string" &&
    typeof value.occurredAt === "string" &&
    typeof value.conversationId === "number" &&
    "payload" in value
  );
}

function getWebSocketUrl() {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  if (baseUrl) {
    const url = new URL("/ws", baseUrl);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    return url.toString();
  }

  if (typeof window === "undefined") {
    return "ws://localhost:8080/ws";
  }

  const url = new URL("/ws", window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

interface ConversationSubscriptionOptions {
  token: string;
  userId: number;
  conversationIds: number[];
  onEvent: (
    event: ConversationSocketEvent<
      ConversationMessage | ConversationReadPayload | ConversationSocketSummaryPayload | ConversationTypingPayload
    >,
  ) => void;
  onConnectionChange?: (connected: boolean) => void;
  onSessionReady?: (session: ConversationSocketSession | null) => void;
}

export interface ConversationSocketSession {
  sendConversationRead: (conversationId: number) => boolean;
  sendTypingState: (conversationId: number, active: boolean) => boolean;
}

export function subscribeToConversationMessages({
  token,
  userId,
  conversationIds,
  onEvent,
  onConnectionChange,
  onSessionReady,
}: ConversationSubscriptionOptions) {
  let socket: WebSocket | null = null;
  let reconnectTimer: number | null = null;
  let heartbeatTimer: number | null = null;
  let stopped = false;
  let buffer = "";

  const clearReconnectTimer = () => {
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const clearHeartbeatTimer = () => {
    if (heartbeatTimer !== null) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  };

  const sendFrame = (command: string, headers: Record<string, string> = {}, body = "") => {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
    const frame = [command, ...headerLines, "", body].join("\n");
    socket.send(`${frame}\0`);
  };

  const session: ConversationSocketSession = {
    sendConversationRead(conversationId: number) {
      if (!Number.isFinite(conversationId) || conversationId <= 0) {
        return false;
      }

      if (!socket || socket.readyState !== WebSocket.OPEN) {
        return false;
      }

      sendFrame(
        "SEND",
        {
          destination: `/app/conversations/${conversationId}/read`,
          "content-type": "application/json",
        },
        "{}",
      );
      return true;
    },
    sendTypingState(conversationId: number, active: boolean) {
      if (!Number.isFinite(conversationId) || conversationId <= 0) {
        return false;
      }

      if (!socket || socket.readyState !== WebSocket.OPEN) {
        return false;
      }

      sendFrame(
        "SEND",
        {
          destination: `/app/conversations/${conversationId}/typing`,
          "content-type": "application/json",
        },
        JSON.stringify({ active }),
      );
      return true;
    },
  };

  const processFrame = (rawFrame: string) => {
    const normalizedFrame = rawFrame.replace(/^\n+/, "");
    if (!normalizedFrame.trim()) {
      return;
    }

    const [headerBlock, ...bodyParts] = normalizedFrame.split("\n\n");
    const headerLines = headerBlock.split("\n");
    const command = headerLines[0]?.trim();
    const body = bodyParts.join("\n\n");

    if (command === "CONNECTED") {
      onConnectionChange?.(true);
      onSessionReady?.(session);
      clearHeartbeatTimer();
      heartbeatTimer = window.setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) {
          socket.send("\n");
        }
      }, 20000);
      sendFrame("SUBSCRIBE", {
        id: `user-conversations-${userId}`,
        destination: `/topic/users/${userId}/conversations`,
      });
      for (const conversationId of conversationIds) {
        sendFrame("SUBSCRIBE", {
          id: `conversation-${conversationId}`,
          destination: `/topic/conversations/${conversationId}`,
        });
      }
      return;
    }

    if (command === "MESSAGE") {
      try {
        const parsed = JSON.parse(body);
        if (!isConversationSocketEvent(parsed)) {
          return;
        }

        onEvent(parsed);
      } catch {
        return;
      }
      return;
    }

    if (command === "ERROR") {
      onConnectionChange?.(false);
      onSessionReady?.(null);
    }
  };

  const drainBuffer = () => {
    while (true) {
      const delimiterIndex = buffer.indexOf("\0");
      if (delimiterIndex < 0) {
        return;
      }

      const frame = buffer.slice(0, delimiterIndex);
      buffer = buffer.slice(delimiterIndex + 1);
      processFrame(frame);
    }
  };

  const scheduleReconnect = () => {
    if (stopped || reconnectTimer !== null) {
      return;
    }

    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, 5000);
  };

  const connect = () => {
    if (stopped) {
      return;
    }

    socket = new WebSocket(getWebSocketUrl());
    buffer = "";

    socket.addEventListener("open", () => {
      sendFrame("CONNECT", {
        Authorization: `Bearer ${token}`,
        "accept-version": "1.2,1.1,1.0",
        "heart-beat": "20000,20000",
      });
    });

    socket.addEventListener("message", (event) => {
      if (typeof event.data !== "string") {
        return;
      }

      if (!event.data.includes("\0") && !event.data.trim()) {
        return;
      }

      buffer += event.data;
      drainBuffer();
    });

    socket.addEventListener("close", () => {
      onConnectionChange?.(false);
      onSessionReady?.(null);
      clearHeartbeatTimer();
      scheduleReconnect();
    });

    socket.addEventListener("error", () => {
      onConnectionChange?.(false);
      onSessionReady?.(null);
      clearHeartbeatTimer();
    });
  };

  connect();

  return () => {
    stopped = true;
    onConnectionChange?.(false);
    onSessionReady?.(null);
    clearReconnectTimer();
    clearHeartbeatTimer();
    if (socket && socket.readyState === WebSocket.OPEN) {
      sendFrame("DISCONNECT");
    }
    socket?.close();
  };
}
