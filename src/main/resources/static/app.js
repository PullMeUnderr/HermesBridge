const state = {
  token: localStorage.getItem("hermes_token") || "",
  me: null,
  conversations: [],
  selectedConversationId: null,
  currentMessages: [],
  currentMembers: [],
  pollHandle: null,
  lastSyncAt: null,
};

const elements = {
  loginView: document.getElementById("loginView"),
  appView: document.getElementById("appView"),
  loginForm: document.getElementById("loginForm"),
  tokenInput: document.getElementById("tokenInput"),
  userCard: document.getElementById("userCard"),
  logoutButton: document.getElementById("logoutButton"),
  createChatForm: document.getElementById("createChatForm"),
  newChatTitle: document.getElementById("newChatTitle"),
  joinInviteForm: document.getElementById("joinInviteForm"),
  inviteCodeInput: document.getElementById("inviteCodeInput"),
  refreshChatsButton: document.getElementById("refreshChatsButton"),
  conversationList: document.getElementById("conversationList"),
  emptyState: document.getElementById("emptyState"),
  conversationView: document.getElementById("conversationView"),
  conversationTitle: document.getElementById("conversationTitle"),
  conversationRole: document.getElementById("conversationRole"),
  conversationMeta: document.getElementById("conversationMeta"),
  workspaceTitle: document.getElementById("workspaceTitle"),
  workspaceSummary: document.getElementById("workspaceSummary"),
  conversationCountValue: document.getElementById("conversationCountValue"),
  userHandleValue: document.getElementById("userHandleValue"),
  syncStateValue: document.getElementById("syncStateValue"),
  createInviteButton: document.getElementById("createInviteButton"),
  refreshMessagesButton: document.getElementById("refreshMessagesButton"),
  messagesList: document.getElementById("messagesList"),
  membersList: document.getElementById("membersList"),
  messageForm: document.getElementById("messageForm"),
  messageInput: document.getElementById("messageInput"),
  toast: document.getElementById("toast"),
};

elements.tokenInput.value = state.token;

elements.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = elements.tokenInput.value.trim();
  if (!token) {
    showToast("Вставь bearer token от бота.", "error");
    return;
  }
  state.token = token;
  localStorage.setItem("hermes_token", token);
  await bootstrapSession();
});

elements.logoutButton.addEventListener("click", () => {
  localStorage.removeItem("hermes_token");
  state.token = "";
  state.me = null;
  state.conversations = [];
  state.selectedConversationId = null;
  state.currentMessages = [];
  state.currentMembers = [];
  state.lastSyncAt = null;
  stopPolling();
  render();
});

elements.createChatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const title = elements.newChatTitle.value.trim();
  if (!title) {
    showToast("Нужно название чата.", "error");
    return;
  }

  try {
    const created = await api("/api/conversations", {
      method: "POST",
      body: JSON.stringify({ title }),
    });
    elements.newChatTitle.value = "";
    await loadConversations();
    selectConversation(created.id);
    showToast(`Чат "${created.title}" создан.`, "success");
  } catch (error) {
    showToast(error.message, "error");
  }
});

elements.joinInviteForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const inviteCode = elements.inviteCodeInput.value.trim();
  if (!inviteCode) {
    showToast("Вставь invite-код.", "error");
    return;
  }

  try {
    const joined = await api(`/api/invites/${encodeURIComponent(inviteCode)}/accept`, {
      method: "POST",
    });
    elements.inviteCodeInput.value = "";
    await loadConversations();
    selectConversation(joined.conversationId);
    showToast(`Вошел в чат "${joined.conversationTitle}".`, "success");
  } catch (error) {
    showToast(error.message, "error");
  }
});

elements.refreshChatsButton.addEventListener("click", async () => {
  await guardedAction(loadConversations);
});

elements.refreshMessagesButton.addEventListener("click", async () => {
  await guardedAction(refreshCurrentConversation);
});

elements.createInviteButton.addEventListener("click", async () => {
  if (!state.selectedConversationId) {
    return;
  }

  try {
    const invite = await api(`/api/conversations/${state.selectedConversationId}/invites`, {
      method: "POST",
    });
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(invite.inviteCode);
      } catch (error) {
        // Best effort only.
      }
    }
    showToast(`Invite создан: ${invite.inviteCode}`, "success", 6500);
  } catch (error) {
    showToast(error.message, "error");
  }
});

elements.messageForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.selectedConversationId) {
    return;
  }

  const body = elements.messageInput.value.trim();
  if (!body) {
    return;
  }

  try {
    await api(`/api/conversations/${state.selectedConversationId}/messages`, {
      method: "POST",
      body: JSON.stringify({ body }),
    });
    elements.messageInput.value = "";
    await loadMessages(state.selectedConversationId);
  } catch (error) {
    showToast(error.message, "error");
  }
});

async function bootstrapSession() {
  try {
    state.me = await api("/api/auth/me");
    await loadConversations();
    render();
    startPolling();
    if (state.conversations.length > 0) {
      selectConversation(state.selectedConversationId || state.conversations[0].id);
    }
  } catch (error) {
    localStorage.removeItem("hermes_token");
    state.token = "";
    state.me = null;
    render();
    showToast(error.message, "error");
  }
}

async function loadConversations() {
  state.conversations = await api("/api/conversations");
  touchSync();
  if (
    state.selectedConversationId &&
    !state.conversations.some((conversation) => conversation.id === state.selectedConversationId)
  ) {
    state.selectedConversationId = null;
    state.currentMessages = [];
    state.currentMembers = [];
  }
  renderConversationList();
  renderWorkspaceHeader();
  renderConversationHeader();
}

async function loadMessages(conversationId) {
  const [messages, members] = await Promise.all([
    api(`/api/conversations/${conversationId}/messages`),
    api(`/api/conversations/${conversationId}/members`),
  ]);

  if (conversationId !== state.selectedConversationId) {
    return;
  }

  state.currentMessages = messages;
  state.currentMembers = members;
  touchSync();
  renderMessages();
  renderMembers(members);
  renderConversationHeader();
  renderWorkspaceHeader();
}

async function refreshCurrentConversation() {
  if (!state.selectedConversationId) {
    return;
  }
  await loadConversations();
  await loadMessages(state.selectedConversationId);
}

function selectConversation(conversationId) {
  state.selectedConversationId = conversationId;
  state.currentMessages = [];
  state.currentMembers = [];
  renderConversationList();
  renderConversationHeader();
  renderWorkspaceHeader();
  loadMessages(conversationId).catch((error) => showToast(error.message, "error"));
}

function render() {
  const loggedIn = Boolean(state.me && state.token);
  elements.loginView.classList.toggle("hidden", loggedIn);
  elements.appView.classList.toggle("hidden", !loggedIn);
  elements.tokenInput.value = state.token;

  if (!loggedIn) {
    elements.userCard.innerHTML = "";
    renderConversationList();
    renderConversationHeader();
    renderWorkspaceHeader();
    return;
  }

  elements.userCard.innerHTML = `
    <strong class="user-name">${escapeHtml(state.me.displayName)}</strong>
    <span class="muted">@${escapeHtml(state.me.username)}</span><br>
    <span class="muted">Tenant: ${escapeHtml(state.me.tenantKey)}</span><br>
    <span class="muted">Telegram: ${state.me.telegramLinked ? "подключен" : "не подключен"}</span>
  `;
  renderConversationList();
  renderConversationHeader();
  renderWorkspaceHeader();
}

function renderConversationList() {
  if (!state.me) {
    elements.conversationList.innerHTML = "";
    return;
  }

  if (state.conversations.length === 0) {
    elements.conversationList.innerHTML = `
      <div class="empty-inline">
        <strong>Чатов пока нет.</strong>
        <p class="muted">Создай первый чат слева или зайди по invite-коду.</p>
      </div>
    `;
    return;
  }

  elements.conversationList.innerHTML = state.conversations
    .map((conversation) => {
      const active = conversation.id === state.selectedConversationId ? "active" : "";
      return `
        <button class="conversation-item ${active}" data-conversation-id="${conversation.id}">
          <span class="conversation-kicker">Chat #${conversation.id}</span>
          <span class="title">${escapeHtml(conversation.title)}</span>
          <span class="conversation-footer">
            <span class="mini-pill">${escapeHtml(conversation.membershipRole)}</span>
            <span class="meta">${conversation.createdAt ? escapeHtml(formatTimestamp(conversation.createdAt)) : "Готов к синку"}</span>
          </span>
        </button>
      `;
    })
    .join("");

  elements.conversationList.querySelectorAll("[data-conversation-id]").forEach((button) => {
    button.addEventListener("click", () => {
      selectConversation(Number(button.dataset.conversationId));
    });
  });
}

function renderWorkspaceHeader() {
  if (!state.me) {
    elements.workspaceTitle.textContent = "Hermes workspace";
    elements.workspaceSummary.textContent =
      "Создавай чаты, подключай участников и постепенно превращай bridge в самостоятельный клиент.";
    elements.conversationCountValue.textContent = "0";
    elements.userHandleValue.textContent = "-";
    elements.syncStateValue.textContent = "Оффлайн";
    return;
  }

  const selectedConversation = state.conversations.find(
    (conversation) => conversation.id === state.selectedConversationId
  );

  elements.workspaceTitle.textContent = selectedConversation
    ? selectedConversation.title
    : "Hermes workspace";
  elements.workspaceSummary.textContent = selectedConversation
    ? `Открыт чат #${selectedConversation.id}. Здесь сходятся внутренние сообщения, инвайты и Telegram bridge.`
    : "Выбери чат слева или создай новый. Эта панель станет основой будущего клиента Hermes.";
  elements.conversationCountValue.textContent = String(state.conversations.length);
  elements.userHandleValue.textContent = `@${state.me.username}`;
  elements.syncStateValue.textContent = state.lastSyncAt
    ? `Обновлено ${formatClock(state.lastSyncAt)}`
    : "Ожидает синк";
}

function renderConversationHeader() {
  const selectedConversation = state.conversations.find(
    (conversation) => conversation.id === state.selectedConversationId
  );

  const hasSelection = Boolean(selectedConversation);
  elements.emptyState.classList.toggle("hidden", hasSelection);
  elements.conversationView.classList.toggle("hidden", !hasSelection);
  elements.createInviteButton.disabled = !hasSelection;
  elements.refreshMessagesButton.disabled = !hasSelection;

  if (!hasSelection) {
    elements.messagesList.innerHTML = "";
    elements.membersList.innerHTML = "";
    return;
  }

  elements.conversationTitle.textContent = selectedConversation.title;
  elements.conversationRole.textContent = selectedConversation.membershipRole;
  elements.conversationMeta.textContent = `Чат #${selectedConversation.id} · ${state.currentMembers.length || "?"} участников`;
}

function renderMessages() {
  if (state.currentMessages.length === 0) {
    elements.messagesList.innerHTML = `
      <div class="empty-inline">
        <strong>Сообщений пока нет.</strong>
        <p class="muted">Напиши первое сообщение здесь или отправь его из привязанной Telegram-группы.</p>
      </div>
    `;
    return;
  }

  elements.messagesList.innerHTML = state.currentMessages
    .map((message) => {
      const mine = message.authorUserId && state.me && message.authorUserId === state.me.id;
      const author = message.authorDisplayName || "Unknown";
      const transport = message.sourceTransport === "INTERNAL" ? "Hermes" : message.sourceTransport;
      return `
        <article class="message-card ${mine ? "mine" : ""}">
          <div class="message-topline">
            <div class="message-author">${escapeHtml(author)}</div>
            <div class="transport-pill">${escapeHtml(transport)}</div>
          </div>
          <div class="message-body">${escapeHtml(message.body)}</div>
          <div class="message-meta">${formatTimestamp(message.createdAt)}</div>
        </article>
      `;
    })
    .join("");

  elements.messagesList.scrollTop = elements.messagesList.scrollHeight;
}

function renderMembers(members) {
  if (!members || members.length === 0) {
    elements.membersList.innerHTML = `
      <div class="empty-inline">
        <strong>Участников пока нет.</strong>
        <p class="muted">Добавь людей через invite-код, чтобы чат стал общим.</p>
      </div>
    `;
    return;
  }

  elements.membersList.innerHTML = members
    .map(
      (member) => `
        <article class="member-card">
          <strong>${escapeHtml(member.displayName)}</strong><br>
          <span class="muted">@${escapeHtml(member.username)}</span><br>
          <span class="member-role">${escapeHtml(member.role)}</span>
        </article>
      `
    )
    .join("");
}

function startPolling() {
  stopPolling();
  state.pollHandle = window.setInterval(() => {
    if (!state.me) {
      return;
    }
    loadConversations().catch(() => {});
    if (state.selectedConversationId) {
      loadMessages(state.selectedConversationId).catch(() => {});
    }
  }, 4000);
}

function stopPolling() {
  if (state.pollHandle) {
    window.clearInterval(state.pollHandle);
    state.pollHandle = null;
  }
}

function touchSync() {
  state.lastSyncAt = new Date().toISOString();
}

async function guardedAction(action) {
  try {
    await action();
  } catch (error) {
    showToast(error.message, "error");
  }
}

async function api(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${state.token}`,
    ...(options.headers || {}),
  };

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch (error) {
      // ignore JSON parse failure
    }
    if (response.status === 401) {
      localStorage.removeItem("hermes_token");
      state.token = "";
      state.me = null;
      stopPolling();
      render();
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function showToast(message, kind = "success", duration = 4200) {
  elements.toast.textContent = message;
  elements.toast.className = `toast ${kind}`;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => {
    elements.toast.className = "toast hidden";
  }, duration);
}

function formatTimestamp(value) {
  try {
    return new Intl.DateTimeFormat("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch (error) {
    return value;
  }
}

function formatClock(value) {
  try {
    return new Intl.DateTimeFormat("ru-RU", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  } catch (error) {
    return value;
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

render();
if (state.token) {
  bootstrapSession();
}
