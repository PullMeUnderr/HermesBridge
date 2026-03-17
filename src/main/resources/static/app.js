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
  videoNoteRecorder: null,
  videoNoteRecorderStream: null,
  videoNoteRecorderChunks: [],
  videoNoteRecorderMimeType: "",
  isRecordingVideoNote: false,
  activeVideoNotePointerId: null,
  videoNoteTargetConversationId: null,
  discardVideoNoteOnStop: false,
  videoNoteRecordingStartedAt: null,
  videoNoteRecordingTimerHandle: null,
  profileAvatarPreviewUrl: null,
  profileAvatarRemoveRequested: false,
  profileSaveInFlight: false,
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
  drawerProfileView: document.getElementById("drawerProfileView"),
  closeDrawerButton: document.getElementById("closeDrawerButton"),
  createChatForm: document.getElementById("createChatForm"),
  newChatTitle: document.getElementById("newChatTitle"),
  joinInviteForm: document.getElementById("joinInviteForm"),
  inviteCodeInput: document.getElementById("inviteCodeInput"),
  profileForm: document.getElementById("profileForm"),
  profileDisplayNameInput: document.getElementById("profileDisplayNameInput"),
  profileUsernameInput: document.getElementById("profileUsernameInput"),
  profileAvatarInput: document.getElementById("profileAvatarInput"),
  profileAvatarPreview: document.getElementById("profileAvatarPreview"),
  profileTelegramHint: document.getElementById("profileTelegramHint"),
  clearProfileAvatarButton: document.getElementById("clearProfileAvatarButton"),
  saveProfileButton: document.getElementById("saveProfileButton"),
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
  recordVideoNoteButton: document.getElementById("recordVideoNoteButton"),
  recordVoiceButton: document.getElementById("recordVoiceButton"),
  recordedVoicePreview: document.getElementById("recordedVoicePreview"),
  recordedVoiceStatus: document.getElementById("recordedVoiceStatus"),
  recordedVoiceMeta: document.getElementById("recordedVoiceMeta"),
  recordedVoiceAudio: document.getElementById("recordedVoiceAudio"),
  clearRecordedVoiceButton: document.getElementById("clearRecordedVoiceButton"),
  videoNoteRecordingPreview: document.getElementById("videoNoteRecordingPreview"),
  videoNoteRecordingStatus: document.getElementById("videoNoteRecordingStatus"),
  videoNoteRecordingMeta: document.getElementById("videoNoteRecordingMeta"),
  videoNoteRecordingTimer: document.getElementById("videoNoteRecordingTimer"),
  videoNoteRecordingVideo: document.getElementById("videoNoteRecordingVideo"),
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
  state.profileSaveInFlight = false;
  state.profileAvatarRemoveRequested = false;
  state.sendAsVideoNote = false;
  state.videoNoteTargetConversationId = null;
  revokeProfileAvatarPreviewUrl();
  elements.profileAvatarInput.value = "";
  cancelVideoNoteRecording(true);
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

elements.profileForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.me || state.profileSaveInFlight) {
    return;
  }

  const displayName = elements.profileDisplayNameInput.value.trim();
  const username = elements.profileUsernameInput.value.trim();
  if (!displayName || !username) {
    showToast("Заполни имя и ник в Hermes.", "error");
    return;
  }

  state.profileSaveInFlight = true;
  renderProfileForm();

  try {
    const formData = new FormData();
    formData.append("displayName", displayName);
    formData.append("username", username);
    if (state.profileAvatarRemoveRequested) {
      formData.append("removeAvatar", "true");
    }
    const avatarFile = elements.profileAvatarInput.files?.[0];
    if (avatarFile) {
      formData.append("avatar", avatarFile);
    }

    state.me = await api("/api/auth/me/profile", {
      method: "PATCH",
      body: formData,
    });
    revokeProfileAvatarPreviewUrl();
    state.profileAvatarRemoveRequested = false;
    elements.profileAvatarInput.value = "";
    render();
    renderMessages();
    renderMembers(state.currentMembers);
    closeDrawer();
    showToast("Профиль обновлен.", "success");
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    state.profileSaveInFlight = false;
    renderProfileForm();
  }
});

elements.profileAvatarInput.addEventListener("change", () => {
  const file = elements.profileAvatarInput.files?.[0];
  revokeProfileAvatarPreviewUrl();

  if (!file) {
    renderProfileForm();
    return;
  }

  if (!(file.type || "").toLowerCase().startsWith("image/")) {
    elements.profileAvatarInput.value = "";
    showToast("Аватар должен быть изображением.", "error");
    renderProfileForm();
    return;
  }

  state.profileAvatarRemoveRequested = false;
  state.profileAvatarPreviewUrl = URL.createObjectURL(file);
  renderProfileForm();
});

elements.clearProfileAvatarButton.addEventListener("click", () => {
  revokeProfileAvatarPreviewUrl();
  elements.profileAvatarInput.value = "";
  state.profileAvatarRemoveRequested = !state.profileAvatarRemoveRequested && Boolean(state.me?.avatarUrl);
  renderProfileForm();
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

elements.recordVideoNoteButton.addEventListener("pointerdown", async (event) => {
  event.preventDefault();
  if (state.isRecordingVideoNote || state.messageSubmitInFlight) {
    return;
  }

  const started = await startVideoNoteRecording(event.pointerId);
  if (started) {
    elements.recordVideoNoteButton.setPointerCapture?.(event.pointerId);
  }
});

elements.recordVideoNoteButton.addEventListener("pointerup", (event) => {
  if (state.activeVideoNotePointerId !== event.pointerId) {
    return;
  }
  stopVideoNoteRecording();
});

elements.recordVideoNoteButton.addEventListener("pointercancel", (event) => {
  if (state.activeVideoNotePointerId !== event.pointerId) {
    return;
  }
  cancelVideoNoteRecording();
});

elements.recordVideoNoteButton.addEventListener("lostpointercapture", (event) => {
  if (state.activeVideoNotePointerId !== event.pointerId) {
    return;
  }

  if (state.isRecordingVideoNote) {
    stopVideoNoteRecording();
  }
});

elements.recordVideoNoteButton.addEventListener("contextmenu", (event) => {
  if (state.isRecordingVideoNote) {
    event.preventDefault();
  }
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
  if (state.isRecordingVideoNote) {
    showToast("Сначала отпусти кнопку кружка.", "error");
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
    cancelVideoNoteRecording(true);
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
    renderDrawer();
    return;
  }

  elements.userCard.innerHTML = `
    <div class="user-shell">
      ${renderAvatarMarkup(state.me.displayName || state.me.username, state.me.avatarUrl, "user-avatar")}
      <div class="user-meta">
        <strong class="user-name">${escapeHtml(state.me.displayName)}</strong>
        <div class="user-handle-line">
          <span class="muted">@${escapeHtml(state.me.username)}</span>
          ${state.me.telegramUsername ? `<span class="user-telegram-hint">(@${escapeHtml(state.me.telegramUsername)})</span>` : ""}
        </div>
        <span class="muted">Tenant: ${escapeHtml(state.me.tenantKey)}</span>
      </div>
    </div>
    <div class="user-card-footer">
      <div class="user-bridge">${state.me.telegramLinked ? "Telegram connected" : "Telegram not linked"}</div>
      <button type="button" class="ghost-button compact-button" data-open-profile>Профиль</button>
    </div>
  `;
  elements.userCard.querySelector("[data-open-profile]")?.addEventListener("click", () => {
    toggleDrawer("profile");
  });
  hydrateProtectedImagePreviews(elements.userCard).catch(() => {});
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

  if (state.sidebarDrawerMode === "profile") {
    prepareProfileForm();
    window.setTimeout(() => elements.profileDisplayNameInput.focus(), 180);
  }
}

function closeDrawer() {
  if (state.sidebarDrawerMode === "profile") {
    revokeProfileAvatarPreviewUrl();
    state.profileAvatarRemoveRequested = false;
    state.profileSaveInFlight = false;
    elements.profileAvatarInput.value = "";
  }
  state.sidebarDrawerMode = null;
  renderDrawer();
}

function renderDrawer() {
  const isCreate = state.sidebarDrawerMode === "create";
  const isJoin = state.sidebarDrawerMode === "join";
  const isProfile = state.sidebarDrawerMode === "profile";
  const isOpen = isCreate || isJoin || isProfile;

  elements.sidebarDrawer.dataset.open = String(isOpen);
  elements.sidebarDrawer.setAttribute("aria-hidden", String(!isOpen));
  elements.drawerCreateView.classList.toggle("hidden", !isCreate);
  elements.drawerJoinView.classList.toggle("hidden", !isJoin);
  elements.drawerProfileView.classList.toggle("hidden", !isProfile);
  elements.openCreateDrawerButton.classList.toggle("active", isCreate);
  elements.openJoinDrawerButton.classList.toggle("active", isJoin);

  if (isCreate) {
    elements.drawerEyebrow.textContent = "Новый чат";
    elements.drawerTitle.textContent = "Создать пространство";
  } else if (isJoin) {
    elements.drawerEyebrow.textContent = "Invite flow";
    elements.drawerTitle.textContent = "Войти по коду";
  } else if (isProfile) {
    elements.drawerEyebrow.textContent = "Профиль";
    elements.drawerTitle.textContent = "Настроить Hermes-профиль";
  } else {
    elements.drawerEyebrow.textContent = "Панель действий";
    elements.drawerTitle.textContent = "Выбери действие";
  }
}

function prepareProfileForm() {
  if (!state.me) {
    return;
  }

  revokeProfileAvatarPreviewUrl();
  state.profileAvatarRemoveRequested = false;
  state.profileSaveInFlight = false;
  elements.profileAvatarInput.value = "";
  elements.profileDisplayNameInput.value = state.me.displayName || "";
  elements.profileUsernameInput.value = state.me.username || "";
  renderProfileForm();
}

function renderProfileForm() {
  if (!state.me) {
    elements.profileAvatarPreview.innerHTML = "";
    return;
  }

  const previewUrl = state.profileAvatarRemoveRequested ? null : state.profileAvatarPreviewUrl || state.me.avatarUrl;
  const usesProtectedUrl = !state.profileAvatarPreviewUrl && Boolean(previewUrl);
  const telegramHandle = state.me.telegramUsername ? `@${state.me.telegramUsername}` : "не привязан";

  elements.profileTelegramHint.innerHTML = state.me.telegramUsername
    ? `Telegram ник будет виден рядом с твоим Hermes-ником как <span class="user-telegram-hint">(${escapeHtml(telegramHandle)})</span>.`
    : "Telegram ник пока не привязан к профилю.";

  elements.clearProfileAvatarButton.disabled = state.profileSaveInFlight
    || (!state.profileAvatarPreviewUrl && !state.profileAvatarRemoveRequested && !state.me.avatarUrl);
  elements.profileDisplayNameInput.disabled = state.profileSaveInFlight;
  elements.profileUsernameInput.disabled = state.profileSaveInFlight;
  elements.profileAvatarInput.disabled = state.profileSaveInFlight;
  elements.saveProfileButton.disabled = state.profileSaveInFlight;
  elements.saveProfileButton.textContent = state.profileSaveInFlight ? "Сохраняем..." : "Сохранить профиль";
  elements.clearProfileAvatarButton.textContent = state.profileAvatarRemoveRequested ? "Аватар уберется" : "Убрать аватар";

  elements.profileAvatarPreview.innerHTML = `
    ${renderAvatarMarkup(state.me.displayName || state.me.username, previewUrl, "user-avatar", { protectedSource: usesProtectedUrl })}
    <div class="stack gap-sm">
      <strong>${escapeHtml(state.me.displayName)}</strong>
      <span class="muted">@${escapeHtml(state.me.username)}</span>
    </div>
  `;

  if (usesProtectedUrl) {
    hydrateProtectedImagePreviews(elements.profileAvatarPreview).catch(() => {});
  }
}

function revokeProfileAvatarPreviewUrl() {
  if (state.profileAvatarPreviewUrl) {
    URL.revokeObjectURL(state.profileAvatarPreviewUrl);
    state.profileAvatarPreviewUrl = null;
  }
}

function renderAvatarMarkup(label, avatarUrl, className, options = {}) {
  const protectedSource = options.protectedSource ?? Boolean(avatarUrl);
  const imageMarkup = avatarUrl
    ? `<img class="avatar-image ${protectedSource ? "hidden" : ""}" ${protectedSource
      ? `data-protected-src="${escapeHtml(avatarUrl)}"`
      : `src="${escapeHtml(avatarUrl)}"`} alt="${escapeHtml(label)}">`
    : "";

  return `
    <div class="${className}">
      ${imageMarkup}
      <span class="avatar-fallback ${avatarUrl && !protectedSource ? "hidden" : ""}">${escapeHtml(getInitials(label))}</span>
    </div>
  `;
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
      const author = mine && state.me ? state.me.displayName : message.authorDisplayName || "Unknown";
      const transport = message.sourceTransport === "INTERNAL" ? "Hermes" : message.sourceTransport;
      return `
        <div class="message-row ${mine ? "mine" : ""}">
          ${renderAvatarMarkup(author, mine ? state.me.avatarUrl : null, "message-avatar")}
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
  hydrateProtectedImagePreviews(elements.messagesList).catch(() => {});
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
    .map((member) => {
      const mine = state.me && member.userId === state.me.id;
      const displayName = mine && state.me ? state.me.displayName : member.displayName;
      const username = mine && state.me ? state.me.username : member.username;
      const avatarUrl = mine && state.me ? state.me.avatarUrl : null;
      const telegramHint = mine && state.me?.telegramUsername
        ? `<span class="user-telegram-hint">(@${escapeHtml(state.me.telegramUsername)})</span>`
        : "";

      return `
        <article class="member-card">
          <div class="member-shell">
            ${renderAvatarMarkup(displayName || username, avatarUrl, "member-avatar")}
            <div>
              <strong class="member-name">${escapeHtml(displayName)}</strong>
              <span class="muted">@${escapeHtml(username)}</span>${telegramHint}<br>
              <span class="member-role">${escapeHtml(member.role)}</span>
            </div>
          </div>
        </article>
      `;
    })
    .join("");
  hydrateProtectedImagePreviews(elements.membersList).catch(() => {});
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
  return state.messageSubmitInFlight || state.isRecordingVoice || state.isRecordingVideoNote;
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

  return `<div class="message-body">${renderRichMessageText(body)}</div>`;
}

function renderRichMessageText(body) {
  return String(body)
    .split("\n")
    .map((line) => linkifyMessageLine(line))
    .join("<br>");
}

function linkifyMessageLine(line) {
  const text = String(line ?? "");
  const urlPattern = /(https?:\/\/[^\s<]+|file:\/\/[^\s<]+|tg:\/\/[^\s<]+|mailto:[^\s<]+)/gi;
  let cursor = 0;
  let html = "";

  for (const match of text.matchAll(urlPattern)) {
    const matchedUrl = match[0];
    const startIndex = match.index ?? 0;
    const endIndex = startIndex + matchedUrl.length;

    html += escapeHtml(text.slice(cursor, startIndex));
    html += renderMessageLink(matchedUrl);
    cursor = endIndex;
  }

  html += escapeHtml(text.slice(cursor));
  return html;
}

function renderMessageLink(rawUrl) {
  const href = rawUrl.trim();
  const isExternal = !href.toLowerCase().startsWith("file://");
  const label = formatMessageLinkLabel(href);
  const targetAttributes = isExternal ? ' target="_blank" rel="noopener noreferrer"' : "";

  return `<a class="message-link" href="${escapeHtml(href)}"${targetAttributes}>${escapeHtml(label)}</a>`;
}

function formatMessageLinkLabel(url) {
  try {
    return decodeURI(url);
  } catch (error) {
    return url;
  }
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
        <div
          class="video-note-shell"
          data-video-note-shell-id="${attachment.id}"
          tabindex="0"
          role="button"
          aria-label="Воспроизвести кружок"
        >
          <video
            class="attachment-video-note hidden"
            playsinline
            preload="metadata"
            data-attachment-preview-id="${attachment.id}"
            data-content-url="${escapeHtml(attachment.contentUrl)}"
            data-video-note-id="${attachment.id}"
          ></video>
          <button
            class="video-note-toggle"
            type="button"
            data-video-note-toggle-id="${attachment.id}"
            aria-label="Воспроизвести кружок"
          >
            <span class="video-note-toggle-icon">▶</span>
          </button>
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

  elements.messagesList.querySelectorAll("[data-video-note-shell-id]").forEach((shell) => {
    const video = shell.querySelector(".attachment-video-note");
    if (!video) {
      return;
    }

    const syncState = () => syncVideoNotePlaybackState(shell, video);

    shell.addEventListener("click", (event) => {
      if (event.target.closest(".video-note-toggle")) {
        return;
      }
      event.preventDefault();
      toggleVideoNotePlayback(shell, video).catch((error) => showToast(error.message, "error"));
    });

    shell.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      event.preventDefault();
      toggleVideoNotePlayback(shell, video).catch((error) => showToast(error.message, "error"));
    });

    shell.querySelector(".video-note-toggle")?.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleVideoNotePlayback(shell, video).catch((error) => showToast(error.message, "error"));
    });

    video.addEventListener("play", syncState);
    video.addEventListener("pause", syncState);
    video.addEventListener("ended", syncState);
    syncState();
  });
}

async function hydrateAttachmentPreviews() {
  const mediaElements = Array.from(elements.messagesList.querySelectorAll("[data-attachment-preview-id]"));
  await Promise.all(mediaElements.map((mediaElement) => hydrateAttachmentPreview(mediaElement)));
}

async function hydrateAttachmentPreview(mediaElement) {
  if (!mediaElement) {
    return;
  }

  try {
    const objectUrl = await ensureProtectedObjectUrl(
      mediaElement.dataset.attachmentPreviewId,
      mediaElement.dataset.contentUrl
    );
    if (mediaElement.src !== objectUrl) {
      mediaElement.src = objectUrl;
    }
    if (typeof mediaElement.load === "function") {
      mediaElement.load();
    }
    mediaElement.dataset.previewReady = "true";
    mediaElement.removeAttribute("data-preview-error");
    mediaElement.classList.remove("hidden");
    mediaElement.closest(".video-note-shell")?.classList.remove("is-unavailable");
  } catch (error) {
    mediaElement.dataset.previewReady = "false";
    mediaElement.dataset.previewError = "true";
    mediaElement.closest(".video-note-shell")?.classList.add("is-unavailable");
    mediaElement.remove();
  }
}

async function hydrateProtectedImagePreviews(rootElement = document) {
  const images = Array.from(rootElement.querySelectorAll("[data-protected-src]"));
  await Promise.all(
    images.map(async (image) => {
      try {
        const objectUrl = await ensureProtectedObjectUrl(
          image.dataset.protectedKey || image.dataset.protectedSrc,
          image.dataset.protectedSrc
        );
        image.src = objectUrl;
        image.classList.remove("hidden");
        image.parentElement?.querySelector(".avatar-fallback")?.classList.add("hidden");
      } catch (error) {
        image.remove();
        image.parentElement?.querySelector(".avatar-fallback")?.classList.remove("hidden");
      }
    })
  );
}

async function ensureProtectedObjectUrl(resourceKey, contentUrl) {
  const cacheKey = `${resourceKey}:${contentUrl}`;
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

async function toggleVideoNotePlayback(shell, video) {
  if (!video) {
    return;
  }

  if (!video.currentSrc && video.dataset.contentUrl) {
    await hydrateAttachmentPreview(video);
  }

  if (video.dataset.previewError === "true") {
    throw new Error("Не удалось загрузить кружок.");
  }

  if (!video.paused && !video.ended) {
    video.pause();
    return;
  }

  await ensureMediaElementReady(video);
  pauseOtherVideoNotes(video);
  try {
    await video.play();
  } catch (error) {
    throw new Error("Не удалось запустить кружок.");
  }
}

function pauseOtherVideoNotes(currentVideo) {
  elements.messagesList.querySelectorAll(".attachment-video-note").forEach((video) => {
    if (video !== currentVideo) {
      video.pause();
    }
  });
}

async function ensureMediaElementReady(mediaElement) {
  if (!mediaElement) {
    return;
  }

  if (mediaElement.dataset.previewError === "true") {
    throw new Error("Не удалось загрузить медиа.");
  }

  if (mediaElement.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
    return;
  }

  if (typeof mediaElement.load === "function") {
    mediaElement.load();
  }

  await new Promise((resolve, reject) => {
    const cleanup = () => {
      window.clearTimeout(timeoutId);
      mediaElement.removeEventListener("loadeddata", handleReady);
      mediaElement.removeEventListener("canplay", handleReady);
      mediaElement.removeEventListener("error", handleError);
    };

    const handleReady = () => {
      cleanup();
      resolve();
    };

    const handleError = () => {
      cleanup();
      reject(new Error("Не удалось подготовить медиа к воспроизведению."));
    };

    const timeoutId = window.setTimeout(() => {
      cleanup();
      reject(new Error("Кружок еще не успел загрузиться."));
    }, 5000);

    mediaElement.addEventListener("loadeddata", handleReady);
    mediaElement.addEventListener("canplay", handleReady);
    mediaElement.addEventListener("error", handleError);
  });
}

function syncVideoNotePlaybackState(shell, video) {
  const isPlaying = Boolean(video && !video.paused && !video.ended);
  shell.classList.toggle("is-playing", isPlaying);

  const toggle = shell.querySelector(".video-note-toggle");
  const icon = shell.querySelector(".video-note-toggle-icon");
  if (!toggle || !icon) {
    return;
  }

  toggle.setAttribute("aria-label", isPlaying ? "Поставить кружок на паузу" : "Воспроизвести кружок");
  shell.setAttribute("aria-label", isPlaying ? "Поставить кружок на паузу" : "Воспроизвести кружок");
  icon.textContent = isPlaying ? "❚❚" : "▶";
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

  if (state.isRecordingVideoNote) {
    parts.push("идет запись кружка");
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
  elements.recordVideoNoteButton.disabled =
    disabled || state.isRecordingVoice || !browserSupportsVideoNoteRecording();
  elements.recordVideoNoteButton.classList.toggle("recording", state.isRecordingVideoNote);
  elements.recordVideoNoteButton.textContent = state.isRecordingVideoNote
    ? "Отпусти, чтобы отправить"
    : "Зажми для кружка";
  elements.recordVoiceButton.disabled =
    disabled || state.isRecordingVideoNote || (!state.isRecordingVoice && !browserSupportsVoiceRecording());
  elements.clearRecordedVoiceButton.disabled =
    disabled || (!state.isRecordingVoice && !state.recordedVoiceFile);
  elements.sendMessageButton.disabled = disabled;
  elements.recordVoiceButton.textContent = state.isRecordingVoice ? "Стоп" : "Голосовое";
  elements.recordVoiceButton.classList.toggle("recording", state.isRecordingVoice);
  elements.clearRecordedVoiceButton.textContent = state.isRecordingVoice ? "Отмена" : "Удалить";
  elements.sendMessageButton.textContent = state.messageSubmitInFlight ? "Отправляем..." : "Отправить";
  renderVideoNoteRecordingPreview();
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

function renderVideoNoteRecordingPreview() {
  const visible = state.isRecordingVideoNote && Boolean(state.videoNoteRecorderStream);
  elements.videoNoteRecordingPreview.classList.toggle("hidden", !visible);

  if (!visible) {
    detachVideoNoteRecordingPreview();
    elements.videoNoteRecordingStatus.textContent = "Кружок · запись";
    elements.videoNoteRecordingMeta.textContent = "00:00 · отпусти кнопку, чтобы отправить";
    elements.videoNoteRecordingTimer.textContent = "00:00";
    return;
  }

  attachVideoNoteRecordingPreview();
  const elapsedMs = Math.max(0, Date.now() - (state.videoNoteRecordingStartedAt || Date.now()));
  const elapsedLabel = formatRecordingDuration(elapsedMs);
  elements.videoNoteRecordingStatus.textContent = "Кружок · запись";
  elements.videoNoteRecordingMeta.textContent = `${elapsedLabel} · отпусти кнопку, чтобы отправить`;
  elements.videoNoteRecordingTimer.textContent = elapsedLabel;
}

function attachVideoNoteRecordingPreview() {
  const preview = elements.videoNoteRecordingVideo;
  if (!preview) {
    return;
  }

  if (preview.srcObject !== state.videoNoteRecorderStream) {
    preview.srcObject = state.videoNoteRecorderStream || null;
  }

  preview.muted = true;
  preview.playsInline = true;
  preview.play?.().catch(() => {});
}

function detachVideoNoteRecordingPreview() {
  const preview = elements.videoNoteRecordingVideo;
  if (!preview) {
    return;
  }

  preview.pause?.();
  if (preview.srcObject) {
    preview.srcObject = null;
  }
}

function startVideoNoteRecordingTicker() {
  stopVideoNoteRecordingTicker();
  state.videoNoteRecordingTimerHandle = window.setInterval(() => {
    if (!state.isRecordingVideoNote) {
      stopVideoNoteRecordingTicker();
      return;
    }
    renderVideoNoteRecordingPreview();
  }, 250);
}

function stopVideoNoteRecordingTicker() {
  if (state.videoNoteRecordingTimerHandle) {
    window.clearInterval(state.videoNoteRecordingTimerHandle);
    state.videoNoteRecordingTimerHandle = null;
  }
}

function formatRecordingDuration(durationMs) {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
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

async function startVideoNoteRecording(pointerId) {
  if (!state.selectedConversationId) {
    showToast("Сначала выбери чат.", "error");
    return false;
  }

  if (state.messageSubmitInFlight || state.isRecordingVoice) {
    return false;
  }

  if (!browserSupportsVideoNoteRecording()) {
    showToast("Этот браузер не поддерживает запись кружков.", "error");
    return false;
  }

  let stream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: "user",
        width: { ideal: 480 },
        height: { ideal: 480 },
      },
      audio: true,
    });
  } catch (error) {
    showToast("Не удалось получить доступ к камере.", "error");
    return false;
  }

  const preferredFormat = selectPreferredVideoNoteRecordingFormat();
  let recorder;
  try {
    recorder = preferredFormat
      ? new MediaRecorder(stream, { mimeType: preferredFormat.mimeType })
      : new MediaRecorder(stream);
  } catch (error) {
    stopMediaStream(stream);
    showToast("Не удалось запустить запись кружка.", "error");
    return false;
  }

  state.videoNoteRecorder = recorder;
  state.videoNoteRecorderStream = stream;
  state.videoNoteRecorderChunks = [];
  state.videoNoteRecorderMimeType = recorder.mimeType || preferredFormat?.mimeType || "video/webm";
  state.isRecordingVideoNote = true;
  state.activeVideoNotePointerId = pointerId ?? null;
  state.videoNoteTargetConversationId = state.selectedConversationId;
  state.discardVideoNoteOnStop = false;
  state.videoNoteRecordingStartedAt = Date.now();
  recorder.addEventListener("dataavailable", handleVideoNoteRecordingChunk);
  recorder.addEventListener("stop", handleVideoNoteRecordingStopped, { once: true });
  recorder.start();
  startVideoNoteRecordingTicker();
  renderSelectedAttachments();
  renderComposerState();
  return true;
}

function stopVideoNoteRecording() {
  if (!state.videoNoteRecorder || state.videoNoteRecorder.state === "inactive") {
    return;
  }

  state.discardVideoNoteOnStop = false;
  state.isRecordingVideoNote = false;
  state.videoNoteRecorder.stop();
  renderSelectedAttachments();
  renderComposerState();
}

function cancelVideoNoteRecording(silent = false) {
  if (state.videoNoteRecorder && state.videoNoteRecorder.state !== "inactive") {
    state.discardVideoNoteOnStop = true;
    state.videoNoteRecorder.removeEventListener("dataavailable", handleVideoNoteRecordingChunk);
    try {
      state.videoNoteRecorder.stop();
    } catch (error) {
      // ignore best effort stop
    }
  }

  cleanupVideoNoteRecorder();
  if (!silent) {
    renderSelectedAttachments();
    renderComposerState();
  }
}

function handleVideoNoteRecordingChunk(event) {
  if (event.data && event.data.size > 0) {
    state.videoNoteRecorderChunks.push(event.data);
  }
}

async function handleVideoNoteRecordingStopped() {
  const blob = new Blob(state.videoNoteRecorderChunks, {
    type: state.videoNoteRecorderMimeType || "video/webm",
  });
  const targetConversationId = state.videoNoteTargetConversationId;
  const shouldDiscard = state.discardVideoNoteOnStop;
  cleanupVideoNoteRecorder();
  renderSelectedAttachments();
  renderComposerState();

  if (shouldDiscard || !targetConversationId || blob.size === 0) {
    if (!shouldDiscard && blob.size === 0) {
      showToast("Кружок получился пустым.", "error");
    }
    return;
  }

  const mimeType = blob.type || "video/webm";
  const file = new File([blob], `video-note-${createRecordingTimestamp()}.${resolveVideoNoteRecordingExtension(mimeType)}`, {
    type: mimeType,
    lastModified: Date.now(),
  });
  await uploadRecordedVideoNote(targetConversationId, file);
}

function cleanupVideoNoteRecorder() {
  stopVideoNoteRecordingTicker();
  detachVideoNoteRecordingPreview();
  if (state.videoNoteRecorderStream) {
    stopMediaStream(state.videoNoteRecorderStream);
  }
  state.videoNoteRecorder = null;
  state.videoNoteRecorderStream = null;
  state.videoNoteRecorderChunks = [];
  state.videoNoteRecorderMimeType = "";
  state.isRecordingVideoNote = false;
  state.activeVideoNotePointerId = null;
  state.videoNoteTargetConversationId = null;
  state.discardVideoNoteOnStop = false;
  state.videoNoteRecordingStartedAt = null;
}

async function uploadRecordedVideoNote(conversationId, file) {
  state.messageSubmitInFlight = true;
  renderComposerState();

  try {
    const formData = new FormData();
    formData.append("sendAsVideoNote", "true");
    formData.append("files", file);

    await api(`/api/conversations/${conversationId}/messages/upload`, {
      method: "POST",
      body: formData,
    });

    if (conversationId === state.selectedConversationId) {
      await loadMessages(conversationId);
    }
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    state.messageSubmitInFlight = false;
    renderComposerState();
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

function browserSupportsVideoNoteRecording() {
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

function selectPreferredVideoNoteRecordingFormat() {
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return null;
  }

  const candidates = [
    { mimeType: "video/mp4;codecs=avc1.42E01E,mp4a.40.2" },
    { mimeType: "video/mp4" },
    { mimeType: "video/webm;codecs=vp9,opus" },
    { mimeType: "video/webm;codecs=vp8,opus" },
    { mimeType: "video/webm" },
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

function resolveVideoNoteRecordingExtension(mimeType) {
  const normalized = String(mimeType || "").toLowerCase();
  if (normalized.includes("mp4")) {
    return "mp4";
  }
  if (normalized.includes("webm")) {
    return "webm";
  }
  return "mp4";
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
