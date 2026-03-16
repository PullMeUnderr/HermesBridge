const state = {
  token: localStorage.getItem("hermes_token") || "",
  me: null,
  conversations: [],
  conversationsSignature: "",
  selectedConversationId: null,
  currentMessages: [],
  currentMessagesSignature: "",
  currentMembers: [],
  currentMembersSignature: "",
  pollHandle: null,
  lastSyncAt: null,
  sidebarDrawerMode: null,
  attachmentObjectUrls: new Map(),
  messageSubmitInFlight: false,
  voiceRecorder: null,
  voiceRecorderStream: null,
  voiceRecorderChunks: [],
  voiceRecorderMimeType: "",
  discardRecordedVoiceOnStop: false,
  recordedVoiceFile: null,
  recordedVoiceObjectUrl: null,
  isRecordingVoice: false,
  sendAsVideoNote: false,
};

const elements = {
  loginView: document.getElementById("loginView"),
  appView: document.getElementById("appView"),
  loginForm: document.getElementById("loginForm"),
  tokenInput: document.getElementById("tokenInput"),
  userCard: document.getElementById("userCard"),
  logoutButton: document.getElementById("logoutButton"),
  openCreateDrawerButton: document.getElementById("openCreateDrawerButton"),
  openJoinDrawerButton: document.getElementById("openJoinDrawerButton"),
  sidebarDrawer: document.getElementById("sidebarDrawer"),
  drawerEyebrow: document.getElementById("drawerEyebrow"),
  drawerTitle: document.getElementById("drawerTitle"),
  drawerCreateView: document.getElementById("drawerCreateView"),
  drawerJoinView: document.getElementById("drawerJoinView"),
  closeDrawerButton: document.getElementById("closeDrawerButton"),
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
  messageAttachmentsInput: document.getElementById("messageAttachmentsInput"),
  attachmentSelectionLabel: document.getElementById("attachmentSelectionLabel"),
  sendAsVideoNoteButton: document.getElementById("sendAsVideoNoteButton"),
  recordVoiceButton: document.getElementById("recordVoiceButton"),
  recordedVoicePreview: document.getElementById("recordedVoicePreview"),
  recordedVoiceStatus: document.getElementById("recordedVoiceStatus"),
  recordedVoiceMeta: document.getElementById("recordedVoiceMeta"),
  recordedVoiceAudio: document.getElementById("recordedVoiceAudio"),
  clearRecordedVoiceButton: document.getElementById("clearRecordedVoiceButton"),
  sendMessageButton: document.getElementById("sendMessageButton"),
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
  state.conversationsSignature = "";
  state.selectedConversationId = null;
  state.currentMessages = [];
  state.currentMessagesSignature = "";
  state.currentMembers = [];
  state.currentMembersSignature = "";
  state.lastSyncAt = null;
  state.sidebarDrawerMode = null;
  state.messageSubmitInFlight = false;
  state.sendAsVideoNote = false;
  discardRecordedVoice();
  resetAttachmentObjectUrls();
  stopPolling();
  render();
});

elements.openCreateDrawerButton.addEventListener("click", () => {
  toggleDrawer("create");
});

elements.openJoinDrawerButton.addEventListener("click", () => {
  toggleDrawer("join");
});

elements.closeDrawerButton.addEventListener("click", () => {
  closeDrawer();
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
    closeDrawer();
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
    closeDrawer();
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

elements.messageAttachmentsInput.addEventListener("change", () => {
  normalizeVideoNoteSelectionState();
  renderSelectedAttachments();
});

elements.sendAsVideoNoteButton.addEventListener("click", () => {
  if (!canSendSelectedFileAsVideoNote() || state.messageSubmitInFlight) {
    return;
  }

  state.sendAsVideoNote = !state.sendAsVideoNote;
  renderSelectedAttachments();
  renderComposerState();
});

elements.recordVoiceButton.addEventListener("click", async () => {
  if (state.isRecordingVoice) {
    stopVoiceRecording();
    return;
  }

  await startVoiceRecording();
});

elements.clearRecordedVoiceButton.addEventListener("click", () => {
  if (state.isRecordingVoice) {
    stopVoiceRecording(true);
    return;
  }

  discardRecordedVoice();
});

elements.messageForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.selectedConversationId || state.messageSubmitInFlight) {
    return;
  }

  const body = elements.messageInput.value.trim();
  if (state.isRecordingVoice) {
    showToast("Сначала останови запись голосового.", "error");
    return;
  }

  const files = getComposerFiles();
  if (!body && files.length === 0) {
    return;
  }

  state.messageSubmitInFlight = true;
  renderComposerState();

  if ((elements.messageAttachmentsInput.files || []).length > 0) {
    elements.messageAttachmentsInput.value = "";
    renderSelectedAttachments();
  }

  try {
    if (files.length > 0) {
      const formData = new FormData();
      if (body) {
        formData.append("body", body);
      }
      if (state.sendAsVideoNote) {
        formData.append("sendAsVideoNote", "true");
      }
      files.forEach((file) => formData.append("files", file));

      await api(`/api/conversations/${state.selectedConversationId}/messages/upload`, {
        method: "POST",
        body: formData,
      });
    } else {
      await api(`/api/conversations/${state.selectedConversationId}/messages`, {
        method: "POST",
        body: JSON.stringify({ body }),
      });
    }

    resetComposer();
    await loadMessages(state.selectedConversationId);
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    state.messageSubmitInFlight = false;
    renderComposerState();
  }
});

elements.messageInput.addEventListener("keydown", async (event) => {
  if (event.key !== "Enter" || event.shiftKey) {
    return;
  }

  event.preventDefault();
  elements.messageForm.requestSubmit();
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
    discardRecordedVoice();
    localStorage.removeItem("hermes_token");
    state.token = "";
    state.me = null;
    state.conversationsSignature = "";
    state.currentMessagesSignature = "";
    state.currentMembersSignature = "";
    render();
    showToast(error.message, "error");
  }
}

async function loadConversations() {
  const conversations = await api("/api/conversations");
  const conversationsSignature = buildConversationsSignature(conversations);
  const selectedConversationMissing =
    state.selectedConversationId &&
    !conversations.some((conversation) => conversation.id === state.selectedConversationId);

  state.conversations = conversations;
  if (
    selectedConversationMissing
  ) {
    state.selectedConversationId = null;
    state.currentMessages = [];
    state.currentMessagesSignature = "";
    state.currentMembers = [];
    state.currentMembersSignature = "";
    resetAttachmentObjectUrls();
  }

  const conversationsChanged = conversationsSignature !== state.conversationsSignature;
  if (!conversationsChanged && !selectedConversationMissing) {
    return;
  }

  state.conversationsSignature = conversationsSignature;
  touchSync();
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

  const messagesSignature = buildMessagesSignature(messages);
  const membersSignature = buildMembersSignature(members);
  const messagesChanged = messagesSignature !== state.currentMessagesSignature;
  const membersChanged = membersSignature !== state.currentMembersSignature;

  if (!messagesChanged && !membersChanged) {
    return;
  }

  if (messagesChanged) {
    resetAttachmentObjectUrls();
    state.currentMessages = messages;
    state.currentMessagesSignature = messagesSignature;
    renderMessages();
  }

  if (membersChanged) {
    state.currentMembers = members;
    state.currentMembersSignature = membersSignature;
    renderMembers(members);
  }

  touchSync();
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
  state.currentMessagesSignature = "";
  state.currentMembers = [];
  state.currentMembersSignature = "";
  resetAttachmentObjectUrls();
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
    <div class="user-shell">
      <div class="user-avatar">${escapeHtml(getInitials(state.me.displayName || state.me.username))}</div>
      <div class="user-meta">
        <strong class="user-name">${escapeHtml(state.me.displayName)}</strong>
        <span class="muted">@${escapeHtml(state.me.username)}</span>
        <span class="muted">Tenant: ${escapeHtml(state.me.tenantKey)}</span>
      </div>
    </div>
    <div class="user-bridge">${state.me.telegramLinked ? "Telegram connected" : "Telegram not linked"}</div>
  `;
  renderConversationList();
  renderConversationHeader();
  renderWorkspaceHeader();
  renderDrawer();
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
          <span class="conversation-avatar">${escapeHtml(getInitials(conversation.title))}</span>
          <span class="conversation-copy">
            <span class="conversation-kicker">Chat #${conversation.id}</span>
            <span class="title">${escapeHtml(conversation.title)}</span>
            <span class="conversation-footer">
              <span class="mini-pill">${escapeHtml(conversation.membershipRole)}</span>
              <span class="meta">${conversation.createdAt ? escapeHtml(formatTimestamp(conversation.createdAt)) : "Готов к синку"}</span>
            </span>
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

function toggleDrawer(mode) {
  state.sidebarDrawerMode = state.sidebarDrawerMode === mode ? null : mode;
  renderDrawer();

  if (state.sidebarDrawerMode === "create") {
    window.setTimeout(() => elements.newChatTitle.focus(), 180);
  }

  if (state.sidebarDrawerMode === "join") {
    window.setTimeout(() => elements.inviteCodeInput.focus(), 180);
  }
}

function closeDrawer() {
  state.sidebarDrawerMode = null;
  renderDrawer();
}

function renderDrawer() {
  const isCreate = state.sidebarDrawerMode === "create";
  const isJoin = state.sidebarDrawerMode === "join";
  const isOpen = isCreate || isJoin;

  elements.sidebarDrawer.dataset.open = String(isOpen);
  elements.sidebarDrawer.setAttribute("aria-hidden", String(!isOpen));
  elements.drawerCreateView.classList.toggle("hidden", !isCreate);
  elements.drawerJoinView.classList.toggle("hidden", !isJoin);
  elements.openCreateDrawerButton.classList.toggle("active", isCreate);
  elements.openJoinDrawerButton.classList.toggle("active", isJoin);

  if (isCreate) {
    elements.drawerEyebrow.textContent = "Новый чат";
    elements.drawerTitle.textContent = "Создать пространство";
  } else if (isJoin) {
    elements.drawerEyebrow.textContent = "Invite flow";
    elements.drawerTitle.textContent = "Войти по коду";
  } else {
    elements.drawerEyebrow.textContent = "Панель действий";
    elements.drawerTitle.textContent = "Выбери действие";
  }
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
  renderComposerState();

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
        <div class="message-row ${mine ? "mine" : ""}">
          <div class="message-avatar">${escapeHtml(getInitials(author))}</div>
          <article class="message-card ${mine ? "mine" : ""}">
            <div class="message-topline">
              <div class="message-author">${escapeHtml(author)}</div>
              <div class="transport-pill">${escapeHtml(transport)}</div>
            </div>
            ${renderMessageAttachments(message.attachments || [])}
            ${renderMessageBody(message.body)}
            <div class="message-meta">${formatTimestamp(message.createdAt)}</div>
          </article>
        </div>
      `;
    })
    .join("");

  bindAttachmentActions();
  hydrateAttachmentPreviews();
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
          <div class="member-shell">
            <div class="member-avatar">${escapeHtml(getInitials(member.displayName || member.username))}</div>
            <div>
              <strong class="member-name">${escapeHtml(member.displayName)}</strong>
              <span class="muted">@${escapeHtml(member.username)}</span><br>
              <span class="member-role">${escapeHtml(member.role)}</span>
            </div>
          </div>
        </article>
      `
    )
    .join("");
}

function startPolling() {
  stopPolling();
  state.pollHandle = window.setInterval(() => {
    if (!state.me || document.hidden || shouldPausePolling()) {
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

function shouldPausePolling() {
  return state.messageSubmitInFlight || state.isRecordingVoice;
}

async function guardedAction(action) {
  try {
    await action();
  } catch (error) {
    showToast(error.message, "error");
  }
}

async function api(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  const headers = {
    Authorization: `Bearer ${state.token}`,
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
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

function renderMessageBody(body) {
  if (!body) {
    return "";
  }

  return `<div class="message-body">${escapeHtml(body)}</div>`;
}

function renderMessageAttachments(attachments) {
  if (!attachments || attachments.length === 0) {
    return "";
  }

  return `
    <div class="message-attachments ${attachments.length > 1 ? "is-album" : ""}">
      ${attachments.map(renderAttachmentCard).join("")}
    </div>
  `;
}

function renderAttachmentCard(attachment) {
  if (attachment.kind === "PHOTO") {
    return `
      <figure class="attachment-card attachment-card-photo">
        <img
          class="attachment-image hidden"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
          alt="${escapeHtml(attachment.fileName)}"
        >
        <figcaption class="attachment-caption">
          <span>${escapeHtml(attachment.fileName)}</span>
          <button
            class="attachment-link"
            type="button"
            data-download-attachment-id="${attachment.id}"
            data-file-name="${escapeHtml(attachment.fileName)}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
          >
            Скачать
          </button>
        </figcaption>
      </figure>
    `;
  }

  if (attachment.kind === "VIDEO") {
    return `
      <figure class="attachment-card attachment-card-video">
        <video
          class="attachment-video hidden"
          controls
          preload="metadata"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
        ></video>
        <figcaption class="attachment-caption">
          <span>${escapeHtml(attachment.fileName)}</span>
          <button
            class="attachment-link"
            type="button"
            data-download-attachment-id="${attachment.id}"
            data-file-name="${escapeHtml(attachment.fileName)}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
          >
            Скачать
          </button>
        </figcaption>
      </figure>
    `;
  }

  if (attachment.kind === "VIDEO_NOTE") {
    return `
      <figure class="attachment-card attachment-card-video-note">
        <div class="video-note-shell">
          <video
            class="attachment-video-note hidden"
            controls
            playsinline
            preload="metadata"
            data-attachment-preview-id="${attachment.id}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
          ></video>
        </div>
        <figcaption class="attachment-caption">
          <span>${escapeHtml(attachment.fileName)}</span>
          <button
            class="attachment-link"
            type="button"
            data-download-attachment-id="${attachment.id}"
            data-file-name="${escapeHtml(attachment.fileName)}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
          >
            Скачать
          </button>
        </figcaption>
      </figure>
    `;
  }

  if (attachment.kind === "VOICE") {
    return `
      <div class="attachment-card attachment-card-voice">
        <audio
          class="attachment-audio hidden"
          controls
          preload="metadata"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
        ></audio>
        <div class="attachment-caption">
          <span>${escapeHtml(attachment.fileName)}</span>
          <button
            class="attachment-link"
            type="button"
            data-download-attachment-id="${attachment.id}"
            data-file-name="${escapeHtml(attachment.fileName)}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
          >
            Скачать
          </button>
        </div>
      </div>
    `;
  }

  return `
    <div class="attachment-card attachment-card-document">
      <div>
        <strong>${escapeHtml(attachment.fileName)}</strong>
        <div class="muted">${formatBytes(attachment.sizeBytes)} · ${escapeHtml(attachment.mimeType || "document")}</div>
      </div>
      <button
        class="attachment-link"
        type="button"
        data-download-attachment-id="${attachment.id}"
        data-file-name="${escapeHtml(attachment.fileName)}"
        data-content-url="${escapeHtml(attachment.contentUrl)}"
      >
        Скачать
      </button>
    </div>
  `;
}

function bindAttachmentActions() {
  elements.messagesList.querySelectorAll("[data-download-attachment-id]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        await downloadAttachment(button.dataset.contentUrl, button.dataset.fileName);
      } catch (error) {
        showToast(error.message, "error");
      }
    });
  });
}

async function hydrateAttachmentPreviews() {
  const mediaElements = Array.from(elements.messagesList.querySelectorAll("[data-attachment-preview-id]"));
  await Promise.all(
    mediaElements.map(async (mediaElement) => {
      try {
        const objectUrl = await ensureAttachmentObjectUrl(
          mediaElement.dataset.attachmentPreviewId,
          mediaElement.dataset.contentUrl
        );
        mediaElement.src = objectUrl;
        mediaElement.classList.remove("hidden");
      } catch (error) {
        mediaElement.remove();
      }
    })
  );
}

async function ensureAttachmentObjectUrl(attachmentId, contentUrl) {
  const cacheKey = `${attachmentId}:${contentUrl}`;
  if (state.attachmentObjectUrls.has(cacheKey)) {
    return state.attachmentObjectUrls.get(cacheKey);
  }

  const response = await fetch(contentUrl, {
    headers: {
      Authorization: `Bearer ${state.token}`,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`Не удалось загрузить вложение (${response.status})`);
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  state.attachmentObjectUrls.set(cacheKey, objectUrl);
  return objectUrl;
}

async function downloadAttachment(contentUrl, fileName) {
  const response = await fetch(contentUrl, {
    headers: {
      Authorization: `Bearer ${state.token}`,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`Не удалось скачать вложение (${response.status})`);
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName || "attachment";
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 500);
}

function renderSelectedAttachments() {
  const files = Array.from(elements.messageAttachmentsInput.files || []);
  const selectedFilesSize = files.reduce((sum, file) => sum + file.size, 0);
  const parts = [];

  if (files.length > 0) {
    parts.push(`${files.length} файл(ов) · ${formatBytes(selectedFilesSize)}`);
  }

  if (state.sendAsVideoNote && canSendSelectedFileAsVideoNote()) {
    parts.push("режим кружка");
  }

  if (state.recordedVoiceFile) {
    parts.push(`голосовое · ${formatBytes(state.recordedVoiceFile.size)}`);
  }

  if (state.isRecordingVoice) {
    parts.push("идет запись голосового");
  }

  elements.attachmentSelectionLabel.textContent = parts.length > 0 ? parts.join(" + ") : "Без вложений";
  renderRecordedVoicePreview();
}

function resetComposer() {
  elements.messageInput.value = "";
  elements.messageAttachmentsInput.value = "";
  state.sendAsVideoNote = false;
  discardRecordedVoice();
  renderSelectedAttachments();
}

function renderComposerState() {
  const disabled = !state.selectedConversationId || state.messageSubmitInFlight;
  const canSendVideoNote = canSendSelectedFileAsVideoNote();
  elements.messageInput.disabled = disabled;
  elements.messageAttachmentsInput.disabled = disabled;
  elements.sendAsVideoNoteButton.classList.toggle("hidden", !canSendVideoNote);
  elements.sendAsVideoNoteButton.disabled = disabled || !canSendVideoNote;
  elements.sendAsVideoNoteButton.classList.toggle("active", state.sendAsVideoNote && canSendVideoNote);
  elements.sendAsVideoNoteButton.textContent = state.sendAsVideoNote ? "Кружок: вкл" : "Кружок";
  elements.recordVoiceButton.disabled =
    disabled || (!state.isRecordingVoice && !browserSupportsVoiceRecording());
  elements.clearRecordedVoiceButton.disabled =
    disabled || (!state.isRecordingVoice && !state.recordedVoiceFile);
  elements.sendMessageButton.disabled = disabled;
  elements.recordVoiceButton.textContent = state.isRecordingVoice ? "Стоп" : "Голосовое";
  elements.recordVoiceButton.classList.toggle("recording", state.isRecordingVoice);
  elements.clearRecordedVoiceButton.textContent = state.isRecordingVoice ? "Отмена" : "Удалить";
  elements.sendMessageButton.textContent = state.messageSubmitInFlight ? "Отправляем..." : "Отправить";
  renderRecordedVoicePreview();
}

function resetAttachmentObjectUrls() {
  state.attachmentObjectUrls.forEach((url) => URL.revokeObjectURL(url));
  state.attachmentObjectUrls.clear();
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

function formatBytes(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB"];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function buildConversationsSignature(conversations) {
  if (!Array.isArray(conversations) || conversations.length === 0) {
    return "empty";
  }

  return conversations
    .map((conversation) =>
      [
        conversation.id,
        conversation.title,
        conversation.membershipRole,
        conversation.createdAt,
      ].join("|")
    )
    .join("::");
}

function buildMessagesSignature(messages) {
  if (!Array.isArray(messages) || messages.length === 0) {
    return "empty";
  }

  return messages
    .map((message) => {
      const attachmentSignature = (message.attachments || [])
        .map((attachment) =>
          [
            attachment.id,
            attachment.kind,
            attachment.fileName,
            attachment.mimeType,
            attachment.sizeBytes,
            attachment.cacheKey || attachment.contentUrl,
          ].join("|")
        )
        .join(",");

      return [
        message.id,
        message.sourceTransport,
        message.authorUserId,
        message.authorExternalId,
        message.authorDisplayName,
        message.body,
        message.createdAt,
        attachmentSignature,
      ].join("|");
    })
    .join("::");
}

function buildMembersSignature(members) {
  if (!Array.isArray(members) || members.length === 0) {
    return "empty";
  }

  return members
    .map((member) =>
      [
        member.userId,
        member.username,
        member.displayName,
        member.role,
      ].join("|")
    )
    .join("::");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function getInitials(value) {
  const words = String(value ?? "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (words.length === 0) {
    return "H";
  }

  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }

  return `${words[0][0]}${words[1][0]}`.toUpperCase();
}

function getComposerFiles() {
  const files = Array.from(elements.messageAttachmentsInput.files || []);
  if (state.recordedVoiceFile) {
    files.push(state.recordedVoiceFile);
  }
  return files;
}

function normalizeVideoNoteSelectionState() {
  if (!canSendSelectedFileAsVideoNote()) {
    state.sendAsVideoNote = false;
  }
}

function renderRecordedVoicePreview() {
  const visible = state.isRecordingVoice || Boolean(state.recordedVoiceFile);
  elements.recordedVoicePreview.classList.toggle("hidden", !visible);
  if (!visible) {
    elements.recordedVoiceAudio.pause();
    elements.recordedVoiceAudio.removeAttribute("src");
    elements.recordedVoiceAudio.classList.add("hidden");
    return;
  }

  if (state.isRecordingVoice) {
    elements.recordedVoiceStatus.textContent = "Запись...";
    elements.recordedVoiceMeta.textContent = "Микрофон активен. Нажми «Стоп», чтобы подготовить голосовое к отправке.";
    elements.recordedVoiceAudio.pause();
    elements.recordedVoiceAudio.removeAttribute("src");
    elements.recordedVoiceAudio.classList.add("hidden");
    return;
  }

  elements.recordedVoiceStatus.textContent = "Голосовое";
  elements.recordedVoiceMeta.textContent = `Готово к отправке · ${formatBytes(state.recordedVoiceFile?.size || 0)}`;
  if (state.recordedVoiceObjectUrl) {
    elements.recordedVoiceAudio.src = state.recordedVoiceObjectUrl;
    elements.recordedVoiceAudio.classList.remove("hidden");
  } else {
    elements.recordedVoiceAudio.pause();
    elements.recordedVoiceAudio.removeAttribute("src");
    elements.recordedVoiceAudio.classList.add("hidden");
  }
}

async function startVoiceRecording() {
  if (state.messageSubmitInFlight) {
    return;
  }

  if (!browserSupportsVoiceRecording()) {
    showToast("Этот браузер не поддерживает запись голосовых.", "error");
    return;
  }

  discardRecordedVoice();

  let stream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (error) {
    showToast("Не удалось получить доступ к микрофону.", "error");
    return;
  }

  const preferredFormat = selectPreferredVoiceRecordingFormat();
  let recorder;
  try {
    recorder = preferredFormat
      ? new MediaRecorder(stream, { mimeType: preferredFormat.mimeType })
      : new MediaRecorder(stream);
  } catch (error) {
    stopMediaStream(stream);
    showToast("Не удалось запустить запись в этом браузере.", "error");
    return;
  }

  state.voiceRecorder = recorder;
  state.voiceRecorderStream = stream;
  state.voiceRecorderChunks = [];
  state.voiceRecorderMimeType = recorder.mimeType || preferredFormat?.mimeType || "audio/webm";
  state.discardRecordedVoiceOnStop = false;
  state.isRecordingVoice = true;

  recorder.addEventListener("dataavailable", handleVoiceRecordingChunk);
  recorder.addEventListener("stop", handleVoiceRecordingStopped, { once: true });
  recorder.start();
  renderSelectedAttachments();
  renderComposerState();
}

function stopVoiceRecording(discard = false) {
  if (!state.voiceRecorder || state.voiceRecorder.state === "inactive") {
    return;
  }

  state.discardRecordedVoiceOnStop = discard;
  state.isRecordingVoice = false;
  state.voiceRecorder.stop();
  renderSelectedAttachments();
  renderComposerState();
}

function handleVoiceRecordingChunk(event) {
  if (event.data && event.data.size > 0) {
    state.voiceRecorderChunks.push(event.data);
  }
}

function handleVoiceRecordingStopped() {
  const recorder = state.voiceRecorder;
  const blob = new Blob(state.voiceRecorderChunks, {
    type: state.voiceRecorderMimeType || recorder?.mimeType || "audio/webm",
  });
  const shouldDiscard = state.discardRecordedVoiceOnStop;
  cleanupVoiceRecorder();

  if (shouldDiscard || blob.size === 0) {
    renderSelectedAttachments();
    renderComposerState();
    if (!shouldDiscard) {
      showToast("Не удалось записать голосовое.", "error");
    }
    return;
  }

  const mimeType = blob.type || "audio/webm";
  const fileName = `voice-${createRecordingTimestamp()}.${resolveVoiceRecordingExtension(mimeType)}`;
  state.recordedVoiceFile = new File([blob], fileName, {
    type: mimeType,
    lastModified: Date.now(),
  });
  state.recordedVoiceObjectUrl = URL.createObjectURL(blob);
  renderSelectedAttachments();
  renderComposerState();
}

function cleanupVoiceRecorder() {
  if (state.voiceRecorderStream) {
    stopMediaStream(state.voiceRecorderStream);
  }
  state.voiceRecorder = null;
  state.voiceRecorderStream = null;
  state.voiceRecorderChunks = [];
  state.voiceRecorderMimeType = "";
  state.discardRecordedVoiceOnStop = false;
  state.isRecordingVoice = false;
}

function discardRecordedVoice() {
  if (state.isRecordingVoice) {
    stopVoiceRecording(true);
    return;
  }

  if (state.recordedVoiceObjectUrl) {
    URL.revokeObjectURL(state.recordedVoiceObjectUrl);
  }
  state.recordedVoiceObjectUrl = null;
  state.recordedVoiceFile = null;
  renderSelectedAttachments();
  renderComposerState();
}

function stopMediaStream(stream) {
  stream.getTracks().forEach((track) => track.stop());
}

function browserSupportsVoiceRecording() {
  return Boolean(navigator.mediaDevices?.getUserMedia && window.MediaRecorder);
}

function canSendSelectedFileAsVideoNote() {
  if (state.recordedVoiceFile) {
    return false;
  }

  const files = Array.from(elements.messageAttachmentsInput.files || []);
  return files.length === 1 && isVideoLikeFile(files[0]);
}

function isVideoLikeFile(file) {
  if (!file) {
    return false;
  }

  const normalizedType = String(file.type || "").toLowerCase();
  if (normalizedType.startsWith("video/")) {
    return true;
  }
  if (normalizedType.startsWith("audio/")) {
    return false;
  }

  const normalizedName = String(file.name || "").toLowerCase();
  return normalizedName.endsWith(".mp4")
    || normalizedName.endsWith(".mov")
    || normalizedName.endsWith(".m4v")
    || normalizedName.endsWith(".webm")
    || normalizedName.endsWith(".mkv")
    || normalizedName.endsWith(".avi");
}

function selectPreferredVoiceRecordingFormat() {
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return null;
  }

  const candidates = [
    { mimeType: "audio/mp4;codecs=mp4a.40.2" },
    { mimeType: "audio/mp4" },
    { mimeType: "audio/ogg;codecs=opus" },
    { mimeType: "audio/ogg" },
    { mimeType: "audio/webm;codecs=opus" },
    { mimeType: "audio/webm" },
  ];

  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate.mimeType)) || null;
}

function resolveVoiceRecordingExtension(mimeType) {
  const normalized = String(mimeType || "").toLowerCase();
  if (normalized.includes("mp4")) {
    return "m4a";
  }
  if (normalized.includes("mpeg")) {
    return "mp3";
  }
  if (normalized.includes("ogg")) {
    return "ogg";
  }
  if (normalized.includes("webm")) {
    return "webm";
  }
  return "ogg";
}

function createRecordingTimestamp() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, "0");
  return [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    "-",
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
  ].join("");
}

render();
renderSelectedAttachments();
if (state.token) {
  bootstrapSession();
}
