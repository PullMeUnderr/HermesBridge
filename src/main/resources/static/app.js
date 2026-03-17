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
  videoNoteRecordingLocked: false,
  videoNotePointerStartY: null,
  videoNoteCameraFacingMode: "user",
  videoNoteCameraSwitchInFlight: false,
  profileAvatarPreviewUrl: null,
  profileAvatarRemoveRequested: false,
  profileSaveInFlight: false,
  sendAsVideoNote: false,
  metricFitFrame: null,
  mobileScreen: "sidebar",
  mobileMembersOpen: false,
  desktopMembersOpen: false,
  replyToMessageId: null,
  mentionSuggestions: [],
  activeMentionRange: null,
  activeMentionIndex: 0,
  highlightedMessageId: null,
  highlightedMessageTimeout: null,
};

const elements = {
  loginView: document.getElementById("loginView"),
  appView: document.getElementById("appView"),
  desktopOverlay: document.getElementById("desktopOverlay"),
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
  mobileBackButton: document.getElementById("mobileBackButton"),
  toggleMembersButton: document.getElementById("toggleMembersButton"),
  closeMembersPanelButton: document.getElementById("closeMembersPanelButton"),
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
  switchVideoNoteCameraButton: document.getElementById("switchVideoNoteCameraButton"),
  replyComposerPreview: document.getElementById("replyComposerPreview"),
  replyComposerAuthor: document.getElementById("replyComposerAuthor"),
  replyComposerExcerpt: document.getElementById("replyComposerExcerpt"),
  cancelReplyButton: document.getElementById("cancelReplyButton"),
  mentionSuggestions: document.getElementById("mentionSuggestions"),
  sendMessageButton: document.getElementById("sendMessageButton"),
  toast: document.getElementById("toast"),
};

elements.tokenInput.value = state.token;

elements.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = elements.tokenInput.value.trim();
  if (!token) {
    showToast("Вставь токен от бота.", "error");
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
  state.mobileScreen = "sidebar";
  state.mobileMembersOpen = false;
  state.desktopMembersOpen = false;
  clearReplyTarget();
  clearMentionSuggestions();
  revokeProfileAvatarPreviewUrl();
  elements.profileAvatarInput.value = "";
  cancelVideoNoteRecording(true);
  discardRecordedVoice();
  resetAttachmentObjectUrls();
  stopPolling();
  render();
});

elements.desktopOverlay.addEventListener("click", () => {
  state.desktopMembersOpen = false;
  syncMobileLayout();
  renderConversationHeader();
});

elements.openCreateDrawerButton.addEventListener("click", () => {
  toggleDrawer("create");
});

elements.openJoinDrawerButton.addEventListener("click", () => {
  toggleDrawer("join");
});

elements.mobileBackButton.addEventListener("click", () => {
  state.mobileScreen = "sidebar";
  state.mobileMembersOpen = false;
  syncMobileLayout();
});

elements.toggleMembersButton.addEventListener("click", () => {
  if (!state.selectedConversationId) {
    return;
  }

  if (isMobileViewport()) {
    state.mobileMembersOpen = !state.mobileMembersOpen;
  } else {
    state.desktopMembersOpen = !state.desktopMembersOpen;
  }
  syncMobileLayout();
  renderConversationHeader();
});

elements.closeMembersPanelButton.addEventListener("click", () => {
  state.mobileMembersOpen = false;
  syncMobileLayout();
  renderConversationHeader();
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
    showToast("Вставь код приглашения.", "error");
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
    showToast(`Инвайт готов: ${invite.inviteCode}`, "success", 6500);
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
  if (state.isRecordingVideoNote && state.videoNoteRecordingLocked) {
    stopVideoNoteRecording();
    return;
  }
  if (state.isRecordingVideoNote || state.messageSubmitInFlight) {
    return;
  }

  const started = await startVideoNoteRecording(event.pointerId);
  if (started) {
    state.videoNotePointerStartY = event.clientY;
    elements.recordVideoNoteButton.setPointerCapture?.(event.pointerId);
  }
});

elements.recordVideoNoteButton.addEventListener("pointermove", (event) => {
  if (
    !state.isRecordingVideoNote
    || state.videoNoteRecordingLocked
    || state.activeVideoNotePointerId !== event.pointerId
    || state.videoNotePointerStartY == null
  ) {
    return;
  }

  const deltaY = state.videoNotePointerStartY - event.clientY;
  if (deltaY < 72) {
    return;
  }

  state.videoNoteRecordingLocked = true;
  state.activeVideoNotePointerId = null;
  state.videoNotePointerStartY = null;
  try {
    elements.recordVideoNoteButton.releasePointerCapture?.(event.pointerId);
  } catch (error) {
    // Best effort only.
  }
  renderSelectedAttachments();
  renderComposerState();
});

elements.recordVideoNoteButton.addEventListener("pointerup", (event) => {
  if (state.videoNoteRecordingLocked) {
    return;
  }
  if (state.activeVideoNotePointerId !== event.pointerId) {
    return;
  }
  stopVideoNoteRecording();
});

elements.recordVideoNoteButton.addEventListener("pointercancel", (event) => {
  if (state.videoNoteRecordingLocked) {
    return;
  }
  if (state.activeVideoNotePointerId !== event.pointerId) {
    return;
  }
  cancelVideoNoteRecording();
});

elements.recordVideoNoteButton.addEventListener("lostpointercapture", (event) => {
  if (state.videoNoteRecordingLocked) {
    return;
  }
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

elements.switchVideoNoteCameraButton.addEventListener("click", async () => {
  await switchVideoNoteCamera();
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

elements.cancelReplyButton.addEventListener("click", () => {
  clearReplyTarget();
  elements.messageInput.focus();
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
      if (state.replyToMessageId) {
        formData.append("replyToMessageId", String(state.replyToMessageId));
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
      const requestBody = { body };
      if (state.replyToMessageId) {
        requestBody.replyToMessageId = state.replyToMessageId;
      }
      await api(`/api/conversations/${state.selectedConversationId}/messages`, {
        method: "POST",
        body: JSON.stringify(requestBody),
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
  if (handleMentionSuggestionsKeydown(event)) {
    return;
  }

  if (event.key !== "Enter" || event.shiftKey) {
    return;
  }

  event.preventDefault();
  elements.messageForm.requestSubmit();
});

elements.messageInput.addEventListener("input", () => {
  updateMentionSuggestions();
});

elements.messageInput.addEventListener("click", () => {
  updateMentionSuggestions();
});

document.addEventListener("click", (event) => {
  if (event.target === elements.messageInput || elements.mentionSuggestions.contains(event.target)) {
    return;
  }
  clearMentionSuggestions();
  renderMentionSuggestions();
});

async function bootstrapSession() {
  try {
    state.me = await api("/api/auth/me");
    await loadConversations();
    render();
    startPolling();
    if (state.conversations.length > 0) {
      if (state.selectedConversationId) {
        selectConversation(state.selectedConversationId);
      } else if (!isMobileViewport()) {
        selectConversation(state.conversations[0].id);
      } else {
        state.mobileScreen = "sidebar";
        syncMobileLayout();
      }
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
    state.mobileScreen = "sidebar";
    state.mobileMembersOpen = false;
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
    state.mobileScreen = "sidebar";
    state.mobileMembersOpen = false;
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
  syncMobileLayout();
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
    if (state.replyToMessageId && !state.currentMessages.some((message) => message.id === state.replyToMessageId)) {
      clearReplyTarget();
    }
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
  state.mobileMembersOpen = false;
  state.desktopMembersOpen = false;
  clearReplyTarget();
  clearMentionSuggestions();
  if (isMobileViewport()) {
    state.mobileScreen = "conversation";
  }
  resetAttachmentObjectUrls();
  closeDrawer();
  renderConversationList();
  renderConversationHeader();
  renderWorkspaceHeader();
  syncMobileLayout();
  loadMessages(conversationId).catch((error) => showToast(error.message, "error"));
}

function render() {
  const loggedIn = Boolean(state.me && state.token);
  document.body.classList.toggle("app-active", loggedIn);
  elements.appView.dataset.hasSelection = "false";
  elements.loginView.classList.toggle("hidden", loggedIn);
  elements.appView.classList.toggle("hidden", !loggedIn);
  elements.tokenInput.value = state.token;

  if (!loggedIn) {
    elements.userCard.innerHTML = "";
    renderConversationList();
    renderConversationHeader();
    renderWorkspaceHeader();
    renderDrawer();
    renderReplyComposer();
    renderMentionSuggestions();
    syncMobileLayout();
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
      <div class="user-bridge">${state.me.telegramLinked ? "Telegram подключен" : "Telegram не подключен"}</div>
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
  renderReplyComposer();
  renderMentionSuggestions();
  syncMobileLayout();
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
        <p class="muted">Создай чат или войди по коду.</p>
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
    elements.drawerEyebrow.textContent = "Доступ";
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
    elements.workspaceTitle.textContent = "Обзор Hermes";
    elements.workspaceSummary.textContent =
      "Чаты, доступ и синхронизация в одном интерфейсе.";
    elements.conversationCountValue.textContent = "0";
    elements.userHandleValue.textContent = "-";
    elements.syncStateValue.textContent = "Оффлайн";
    queueMetricValueFit();
    return;
  }

  const selectedConversation = state.conversations.find(
    (conversation) => conversation.id === state.selectedConversationId
  );

  elements.workspaceTitle.textContent = selectedConversation
    ? selectedConversation.title
    : "Обзор Hermes";
  elements.workspaceSummary.textContent = selectedConversation
    ? `Чат #${selectedConversation.id} открыт. Сообщения, участники и Telegram sync собраны здесь.`
    : "Выбери чат слева или создай новый.";
  elements.conversationCountValue.textContent = String(state.conversations.length);
  elements.userHandleValue.textContent = `@${state.me.username}`;
  elements.syncStateValue.textContent = state.lastSyncAt
    ? `Обновлено ${formatClock(state.lastSyncAt)}`
    : "Ожидает синк";
  queueMetricValueFit();
}

function queueMetricValueFit() {
  if (state.metricFitFrame) {
    window.cancelAnimationFrame(state.metricFitFrame);
  }

  state.metricFitFrame = window.requestAnimationFrame(() => {
    state.metricFitFrame = null;
    fitMetricValue(elements.userHandleValue, { minRem: 0.84, maxRem: 1.46 });
    fitMetricValue(elements.syncStateValue, { minRem: 0.82, maxRem: 1.38 });
  });
}

function fitMetricValue(element, options = {}) {
  if (!element || !element.isConnected || element.clientWidth === 0) {
    return;
  }

  const minRem = options.minRem ?? 0.82;
  const maxRem = options.maxRem ?? 1.4;
  element.style.fontSize = `${maxRem}rem`;

  const availableWidth = element.clientWidth;
  const requiredWidth = element.scrollWidth;
  if (!availableWidth || requiredWidth <= availableWidth) {
    return;
  }

  const fittedRem = Math.max(minRem, maxRem * ((availableWidth / requiredWidth) * 0.98));
  element.style.fontSize = `${fittedRem}rem`;

  if (element.scrollWidth > availableWidth) {
    const step = 0.02;
    for (let current = fittedRem; current > minRem; current -= step) {
      element.style.fontSize = `${Math.max(minRem, current)}rem`;
      if (element.scrollWidth <= availableWidth) {
        break;
      }
    }
  }
}

function renderConversationHeader() {
  const selectedConversation = state.conversations.find(
    (conversation) => conversation.id === state.selectedConversationId
  );

  const hasSelection = Boolean(selectedConversation);
  const membersPanelOpen = isMobileViewport() ? state.mobileMembersOpen : state.desktopMembersOpen;
  elements.appView.dataset.hasSelection = String(hasSelection);
  elements.emptyState.classList.toggle("hidden", hasSelection);
  elements.conversationView.classList.toggle("hidden", !hasSelection);
  elements.createInviteButton.disabled = !hasSelection;
  elements.refreshMessagesButton.disabled = !hasSelection;
  elements.mobileBackButton.disabled = !hasSelection;
  elements.toggleMembersButton.disabled = !hasSelection;
  elements.toggleMembersButton.classList.toggle("hidden", !hasSelection);
  elements.toggleMembersButton.textContent = membersPanelOpen ? "Скрыть состав" : "Состав";
  elements.createInviteButton.textContent = isMobileViewport() ? "Инвайт" : "Создать инвайт";
  renderComposerState();
  renderReplyComposer();

  if (!hasSelection) {
    elements.messagesList.innerHTML = "";
    elements.membersList.innerHTML = "";
    state.mobileMembersOpen = false;
    syncMobileLayout();
    return;
  }

  elements.conversationTitle.textContent = selectedConversation.title;
  elements.conversationRole.textContent = selectedConversation.membershipRole;
  elements.conversationMeta.textContent = formatConversationMeta(selectedConversation.id, state.currentMembers.length || 0);
  syncMobileLayout();
}

function isMobileViewport() {
  return window.matchMedia("(max-width: 980px)").matches;
}

function formatConversationMeta(conversationId, memberCount) {
  const normalizedCount = Number.isFinite(memberCount) ? memberCount : 0;
  if (isMobileViewport()) {
    return `#${conversationId} · ${formatMemberCountShort(normalizedCount)}`;
  }

  return `Чат #${conversationId} · ${formatMemberCountLong(normalizedCount)}`;
}

function formatMemberCountShort(count) {
  return `${count} уч.`;
}

function formatMemberCountLong(count) {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${count} участник`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${count} участника`;
  }
  return `${count} участников`;
}

function syncMobileLayout() {
  const loggedIn = Boolean(state.me && state.token);
  if (!loggedIn) {
    elements.appView.dataset.mobileScreen = "auth";
    elements.appView.dataset.mobileMembersOpen = "false";
    elements.appView.dataset.desktopMembersOpen = "false";
    elements.desktopOverlay.classList.add("hidden");
    return;
  }

  if (!isMobileViewport()) {
    state.mobileScreen = "split";
    state.mobileMembersOpen = false;
  } else if (!state.selectedConversationId) {
    state.mobileScreen = "sidebar";
    state.mobileMembersOpen = false;
  } else if (state.mobileScreen !== "conversation") {
    state.mobileScreen = "sidebar";
  }

  elements.appView.dataset.mobileScreen = state.mobileScreen;
  elements.appView.dataset.mobileMembersOpen = String(Boolean(state.mobileMembersOpen && isMobileViewport()));
  syncDesktopDrawers();
}

function syncDesktopDrawers() {
  const desktop = !isMobileViewport();
  const hasSelection = Boolean(state.selectedConversationId);

  if (!desktop) {
    state.desktopMembersOpen = false;
  }

  const membersOpen = desktop && hasSelection && state.desktopMembersOpen;
  const overlayOpen = membersOpen;

  elements.appView.dataset.desktopMembersOpen = String(membersOpen);
  elements.desktopOverlay.classList.toggle("hidden", !overlayOpen);
  elements.toggleMembersButton.setAttribute(
    "aria-expanded",
    String(desktop ? membersOpen : Boolean(state.mobileMembersOpen && hasSelection))
  );
}

function renderMessages() {
  if (state.currentMessages.length === 0) {
    elements.messagesList.innerHTML = `
      <div class="empty-inline">
        <strong>Сообщений пока нет.</strong>
        <p class="muted">Напиши первым здесь или отправь сообщение из Telegram.</p>
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
        <div class="message-row ${mine ? "mine" : ""}" data-message-id="${message.id}">
          ${renderAvatarMarkup(author, mine ? state.me.avatarUrl : null, "message-avatar")}
          <article class="message-card ${mine ? "mine" : ""}">
            <div class="message-topline">
              <div class="message-author">${escapeHtml(author)}</div>
              <div class="message-actions">
                <button class="message-reply-button" type="button" data-reply-message-id="${message.id}" aria-label="Ответить">↩</button>
                <div class="transport-pill">${escapeHtml(transport)}</div>
              </div>
            </div>
            ${renderReplySnippet(message.replyTo)}
            ${renderMessageAttachments(message.attachments || [])}
            ${renderMessageBody(message.body)}
            <div class="message-meta">${formatTimestamp(message.createdAt)}</div>
          </article>
        </div>
      `;
    })
    .join("");

  bindAttachmentActions();
  bindReplyActions();
  hydrateProtectedImagePreviews(elements.messagesList).catch(() => {});
  hydrateAttachmentPreviews();
  elements.messagesList.scrollTop = elements.messagesList.scrollHeight;
}

function renderMembers(members) {
  if (!members || members.length === 0) {
    elements.membersList.innerHTML = `
      <div class="empty-inline">
        <strong>Участников пока нет.</strong>
        <p class="muted">Добавь людей по коду, чтобы чат стал общим.</p>
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
            <div class="member-copy">
              <strong class="member-name">${escapeHtml(displayName)}</strong>
              <div class="member-handle-line">
                <span class="muted">@${escapeHtml(username)}</span>${telegramHint}
              </div>
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

    html += renderMentionText(text.slice(cursor, startIndex));
    html += renderMessageLink(matchedUrl);
    cursor = endIndex;
  }

  html += renderMentionText(text.slice(cursor));
  return html;
}

function renderMentionText(text) {
  const raw = String(text ?? "");
  const mentionPattern = /(^|[^A-Za-z0-9_])@([A-Za-z0-9_]{1,100})/g;
  let cursor = 0;
  let html = "";

  for (const match of raw.matchAll(mentionPattern)) {
    const prefix = match[1] ?? "";
    const username = match[2] ?? "";
    const startIndex = match.index ?? 0;
    const mentionStart = startIndex + prefix.length;
    const endIndex = mentionStart + username.length + 1;
    const normalizedUsername = username.toLowerCase();
    const classes = ["message-mention"];

    if (state.me?.username && normalizedUsername === state.me.username.toLowerCase()) {
      classes.push("message-mention-self");
    }

    html += escapeHtml(raw.slice(cursor, startIndex));
    html += escapeHtml(prefix);
    html += `<span class="${classes.join(" ")}">@${escapeHtml(username)}</span>`;
    cursor = endIndex;
  }

  html += escapeHtml(raw.slice(cursor));
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
  const attachmentKind = detectAttachmentDisplayKind(attachment);

  if (attachmentKind === "PHOTO") {
    return `
      <figure class="attachment-card attachment-card-photo">
        <img
          class="attachment-image hidden"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
          data-mime-type="${escapeHtml(attachment.mimeType || "")}"
          data-file-name="${escapeHtml(attachment.fileName || "")}"
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

  if (attachmentKind === "VIDEO") {
    return `
      <figure class="attachment-card attachment-card-video">
        <video
          class="attachment-video hidden"
          controls
          preload="metadata"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
          data-mime-type="${escapeHtml(attachment.mimeType || "")}"
          data-file-name="${escapeHtml(attachment.fileName || "")}"
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

  if (attachmentKind === "VIDEO_NOTE") {
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
            data-mime-type="${escapeHtml(attachment.mimeType || "")}"
            data-file-name="${escapeHtml(attachment.fileName || "")}"
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

  if (attachmentKind === "VOICE") {
    return `
      <div class="attachment-card attachment-card-voice">
        <audio
          class="attachment-audio hidden"
          controls
          preload="metadata"
          data-attachment-preview-id="${attachment.id}"
          data-content-url="${escapeHtml(attachment.contentUrl)}"
          data-mime-type="${escapeHtml(attachment.mimeType || "")}"
          data-file-name="${escapeHtml(attachment.fileName || "")}"
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

function detectAttachmentDisplayKind(attachment) {
  const declaredKind = String(attachment?.kind || "").toUpperCase();
  if (["PHOTO", "VIDEO", "VIDEO_NOTE", "VOICE", "DOCUMENT"].includes(declaredKind)) {
    if (declaredKind !== "DOCUMENT") {
      return declaredKind;
    }
  }

  const mimeType = String(attachment?.mimeType || "").trim().toLowerCase();
  const fileName = String(attachment?.fileName || "").trim().toLowerCase();
  if (mimeType.startsWith("image/") || /\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$/i.test(fileName)) {
    return "PHOTO";
  }
  if (mimeType.startsWith("video/") || /\.(mp4|mov|m4v|webm|mkv|avi)$/i.test(fileName)) {
    return declaredKind === "VIDEO_NOTE" ? "VIDEO_NOTE" : "VIDEO";
  }
  if (mimeType.startsWith("audio/") || /\.(ogg|oga|opus|mp3|m4a|aac|wav)$/i.test(fileName)) {
    return "VOICE";
  }
  return declaredKind || "DOCUMENT";
}

function renderReplySnippet(reply) {
  if (!reply) {
    return "";
  }

  return `
    <button
      class="message-reply-snippet"
      type="button"
      data-scroll-message-id="${reply.id}"
      aria-label="Перейти к сообщению, на которое дан ответ"
    >
      <span class="message-reply-author">${escapeHtml(reply.authorDisplayName || "Сообщение")}</span>
      <span class="message-reply-excerpt">${escapeHtml(summarizeReplyMessage(reply))}</span>
    </button>
  `;
}

function summarizeReplyMessage(message) {
  if (!message) {
    return "Сообщение";
  }

  const body = String(message.body || "").trim();
  if (body) {
    return body.length > 96 ? `${body.slice(0, 93)}...` : body;
  }

  const attachments = message.attachments || [];
  if (attachments.length === 0) {
    return "Сообщение";
  }
  if (attachments.length > 1) {
    return `${attachments.length} вложения`;
  }

  const attachment = attachments[0];
  const label = attachment.fileName || attachment.kind || "Вложение";
  const prefix = {
    PHOTO: "Фото",
    VIDEO: "Видео",
    VIDEO_NOTE: "Кружок",
    VOICE: "Голосовое",
    DOCUMENT: "Файл",
  }[attachment.kind] || "Вложение";
  return `${prefix}: ${label}`;
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

function bindReplyActions() {
  elements.messagesList.querySelectorAll("[data-reply-message-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const messageId = Number(button.dataset.replyMessageId);
      if (!Number.isFinite(messageId)) {
        return;
      }
      state.replyToMessageId = messageId;
      renderReplyComposer();
      elements.messageInput.focus();
    });
  });

  elements.messagesList.querySelectorAll("[data-scroll-message-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const messageId = Number(button.dataset.scrollMessageId);
      if (!Number.isFinite(messageId)) {
        return;
      }
      scrollToMessage(messageId);
    });
  });
}

function renderReplyComposer() {
  const replyTarget = findReplyTargetMessage();
  const visible = Boolean(replyTarget && state.selectedConversationId);
  elements.replyComposerPreview.classList.toggle("hidden", !visible);

  if (!visible) {
    elements.replyComposerAuthor.textContent = "Ответ";
    elements.replyComposerExcerpt.textContent = "";
    return;
  }

  elements.replyComposerAuthor.textContent = `Ответ: ${replyTarget.authorDisplayName || "Сообщение"}`;
  elements.replyComposerExcerpt.textContent = summarizeReplyMessage(replyTarget);
}

function clearReplyTarget() {
  state.replyToMessageId = null;
  renderReplyComposer();
}

function findReplyTargetMessage() {
  if (!state.replyToMessageId) {
    return null;
  }
  return state.currentMessages.find((message) => message.id === state.replyToMessageId) || null;
}

function scrollToMessage(messageId) {
  const target = elements.messagesList.querySelector(`[data-message-id="${messageId}"]`);
  if (!target) {
    return;
  }

  target.scrollIntoView({ block: "center", behavior: "smooth" });
  target.classList.add("is-highlighted");
  if (state.highlightedMessageTimeout) {
    window.clearTimeout(state.highlightedMessageTimeout);
  }
  state.highlightedMessageId = messageId;
  state.highlightedMessageTimeout = window.setTimeout(() => {
    target.classList.remove("is-highlighted");
    if (state.highlightedMessageId === messageId) {
      state.highlightedMessageId = null;
      state.highlightedMessageTimeout = null;
    }
  }, 1800);
}

function updateMentionSuggestions() {
  if (!state.selectedConversationId) {
    clearMentionSuggestions();
    renderMentionSuggestions();
    return;
  }

  const cursor = elements.messageInput.selectionStart ?? elements.messageInput.value.length;
  const beforeCursor = elements.messageInput.value.slice(0, cursor);
  const match = beforeCursor.match(/(?:^|[\s(])@([A-Za-z0-9_]*)$/);
  if (!match) {
    clearMentionSuggestions();
    renderMentionSuggestions();
    return;
  }

  const query = (match[1] || "").toLowerCase();
  const mentionStart = cursor - query.length - 1;
  const suggestions = buildMentionSuggestions(query);
  if (suggestions.length === 0) {
    clearMentionSuggestions();
    renderMentionSuggestions();
    return;
  }

  state.activeMentionRange = { start: mentionStart, end: cursor, query };
  state.mentionSuggestions = suggestions;
  state.activeMentionIndex = Math.min(state.activeMentionIndex, suggestions.length - 1);
  renderMentionSuggestions();
}

function buildMentionSuggestions(query) {
  const normalizedQuery = String(query || "").trim().toLowerCase();
  const seen = new Set();
  return state.currentMembers
    .filter((member) => {
      if (!member?.username) {
        return false;
      }
      const username = member.username.toLowerCase();
      const displayName = String(member.displayName || "").toLowerCase();
      return !normalizedQuery
        || username.startsWith(normalizedQuery)
        || displayName.includes(normalizedQuery);
    })
    .sort((left, right) => {
      const leftStarts = left.username.toLowerCase().startsWith(normalizedQuery);
      const rightStarts = right.username.toLowerCase().startsWith(normalizedQuery);
      if (leftStarts !== rightStarts) {
        return leftStarts ? -1 : 1;
      }
      return left.username.localeCompare(right.username, "ru");
    })
    .filter((member) => {
      const key = member.username.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    })
    .slice(0, 6);
}

function renderMentionSuggestions() {
  const visible = state.mentionSuggestions.length > 0 && Boolean(state.activeMentionRange);
  elements.mentionSuggestions.classList.toggle("hidden", !visible);

  if (!visible) {
    elements.mentionSuggestions.innerHTML = "";
    return;
  }

  elements.mentionSuggestions.innerHTML = state.mentionSuggestions
    .map((suggestion, index) => `
      <button
        class="mention-suggestion ${index === state.activeMentionIndex ? "active" : ""}"
        type="button"
        data-mention-username="${escapeHtml(suggestion.username)}"
      >
        <span class="mention-suggestion-name">${escapeHtml(suggestion.displayName || suggestion.username)}</span>
        <span class="mention-suggestion-handle">@${escapeHtml(suggestion.username)}</span>
      </button>
    `)
    .join("");

  elements.mentionSuggestions.querySelectorAll("[data-mention-username]").forEach((button) => {
    button.addEventListener("mousedown", (event) => {
      event.preventDefault();
      applyMentionSuggestion(button.dataset.mentionUsername);
    });
  });
}

function clearMentionSuggestions() {
  state.mentionSuggestions = [];
  state.activeMentionRange = null;
  state.activeMentionIndex = 0;
}

function handleMentionSuggestionsKeydown(event) {
  if (state.mentionSuggestions.length === 0 || !state.activeMentionRange) {
    return false;
  }

  if (event.key === "ArrowDown") {
    event.preventDefault();
    state.activeMentionIndex = (state.activeMentionIndex + 1) % state.mentionSuggestions.length;
    renderMentionSuggestions();
    return true;
  }

  if (event.key === "ArrowUp") {
    event.preventDefault();
    state.activeMentionIndex =
      (state.activeMentionIndex - 1 + state.mentionSuggestions.length) % state.mentionSuggestions.length;
    renderMentionSuggestions();
    return true;
  }

  if (event.key === "Enter" || event.key === "Tab") {
    event.preventDefault();
    const suggestion = state.mentionSuggestions[state.activeMentionIndex];
    if (suggestion) {
      applyMentionSuggestion(suggestion.username);
    }
    return true;
  }

  if (event.key === "Escape") {
    event.preventDefault();
    clearMentionSuggestions();
    renderMentionSuggestions();
    return true;
  }

  return false;
}

function applyMentionSuggestion(username) {
  if (!username || !state.activeMentionRange) {
    return;
  }

  const prefix = elements.messageInput.value.slice(0, state.activeMentionRange.start);
  const suffix = elements.messageInput.value.slice(state.activeMentionRange.end);
  const insertion = `@${username} `;
  const nextValue = `${prefix}${insertion}${suffix}`;
  const caretPosition = prefix.length + insertion.length;

  elements.messageInput.value = nextValue;
  elements.messageInput.focus();
  elements.messageInput.setSelectionRange(caretPosition, caretPosition);
  clearMentionSuggestions();
  renderMentionSuggestions();
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
      mediaElement.dataset.contentUrl,
      {
        mimeType: mediaElement.dataset.mimeType,
        fileName: mediaElement.dataset.fileName,
      }
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

async function ensureProtectedObjectUrl(resourceKey, contentUrl, options = {}) {
  const expectedMimeType = String(options.mimeType || "").trim().toLowerCase();
  const expectedFileName = String(options.fileName || "").trim();
  const cacheKey = `${resourceKey}:${contentUrl}:${expectedMimeType}:${expectedFileName}`;
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

  const rawBlob = await response.blob();
  const repairedBlob = repairPreviewBlobType(rawBlob, expectedMimeType, expectedFileName);
  const objectUrl = URL.createObjectURL(repairedBlob);
  state.attachmentObjectUrls.set(cacheKey, objectUrl);
  return objectUrl;
}

function repairPreviewBlobType(blob, expectedMimeType, fileName) {
  if (!(blob instanceof Blob)) {
    return blob;
  }

  const normalizedBlobType = String(blob.type || "").trim().toLowerCase();
  const normalizedExpectedMimeType = String(expectedMimeType || "").trim().toLowerCase();
  const inferredMimeType = inferMimeTypeFromFilename(fileName);
  const resolvedMimeType = [normalizedExpectedMimeType, inferredMimeType]
    .find((candidate) => candidate && candidate !== "application/octet-stream");

  if (!resolvedMimeType) {
    return blob;
  }

  if (normalizedBlobType && normalizedBlobType !== "application/octet-stream") {
    return blob;
  }

  return new Blob([blob], { type: resolvedMimeType });
}

function inferMimeTypeFromFilename(fileName) {
  const normalizedFileName = String(fileName || "").trim().toLowerCase();
  if (!normalizedFileName) {
    return "";
  }

  if (/\.(jpg|jpeg)$/.test(normalizedFileName)) {
    return "image/jpeg";
  }
  if (/\.png$/.test(normalizedFileName)) {
    return "image/png";
  }
  if (/\.gif$/.test(normalizedFileName)) {
    return "image/gif";
  }
  if (/\.webp$/.test(normalizedFileName)) {
    return "image/webp";
  }
  if (/\.bmp$/.test(normalizedFileName)) {
    return "image/bmp";
  }
  if (/\.heic$/.test(normalizedFileName)) {
    return "image/heic";
  }
  if (/\.heif$/.test(normalizedFileName)) {
    return "image/heif";
  }
  if (/\.mp4$/.test(normalizedFileName)) {
    return "video/mp4";
  }
  if (/\.mov$/.test(normalizedFileName)) {
    return "video/quicktime";
  }
  if (/\.m4v$/.test(normalizedFileName)) {
    return "video/x-m4v";
  }
  if (/\.webm$/.test(normalizedFileName)) {
    return "video/webm";
  }
  if (/\.mkv$/.test(normalizedFileName)) {
    return "video/x-matroska";
  }
  if (/\.avi$/.test(normalizedFileName)) {
    return "video/x-msvideo";
  }
  if (/\.ogg$/.test(normalizedFileName)) {
    return "audio/ogg";
  }
  if (/\.oga$/.test(normalizedFileName)) {
    return "audio/ogg";
  }
  if (/\.opus$/.test(normalizedFileName)) {
    return "audio/ogg";
  }
  if (/\.mp3$/.test(normalizedFileName)) {
    return "audio/mpeg";
  }
  if (/\.m4a$/.test(normalizedFileName)) {
    return "audio/mp4";
  }
  if (/\.aac$/.test(normalizedFileName)) {
    return "audio/aac";
  }
  if (/\.wav$/.test(normalizedFileName)) {
    return "audio/wav";
  }

  return "";
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
  elements.attachmentSelectionLabel.classList.toggle("is-empty-mobile", parts.length === 0);
  renderRecordedVoicePreview();
}

function resetComposer() {
  elements.messageInput.value = "";
  elements.messageAttachmentsInput.value = "";
  state.sendAsVideoNote = false;
  discardRecordedVoice();
  clearReplyTarget();
  clearMentionSuggestions();
  renderSelectedAttachments();
  renderMentionSuggestions();
}

function renderComposerState() {
  const disabled = !state.selectedConversationId || state.messageSubmitInFlight;
  const canSendVideoNote = canSendSelectedFileAsVideoNote();
  const videoNoteSupportIssue = getVideoNoteRecordingSupportIssue();
  const voiceSupportIssue = getVoiceRecordingSupportIssue();
  elements.messageInput.disabled = disabled;
  elements.messageAttachmentsInput.disabled = disabled;
  elements.sendAsVideoNoteButton.classList.toggle("hidden", !canSendVideoNote);
  elements.sendAsVideoNoteButton.disabled = disabled || !canSendVideoNote;
  elements.sendAsVideoNoteButton.classList.toggle("active", state.sendAsVideoNote && canSendVideoNote);
  setToolButtonLabel(elements.sendAsVideoNoteButton, state.sendAsVideoNote ? "Кружок: вкл" : "Кружок");
  elements.recordVideoNoteButton.disabled = disabled || state.isRecordingVoice;
  elements.recordVideoNoteButton.classList.toggle("recording", state.isRecordingVideoNote);
  elements.recordVideoNoteButton.classList.toggle("locked", state.videoNoteRecordingLocked);
  elements.recordVideoNoteButton.classList.toggle("unsupported", Boolean(videoNoteSupportIssue));
  elements.recordVideoNoteButton.title = videoNoteSupportIssue || "";
  setToolButtonLabel(
    elements.recordVideoNoteButton,
    state.isRecordingVideoNote
      ? (state.videoNoteRecordingLocked ? "Отправить кружок" : "Зажми для кружка")
      : "Зажми для кружка"
  );
  elements.recordVoiceButton.disabled = disabled || state.isRecordingVideoNote;
  elements.clearRecordedVoiceButton.disabled =
    disabled || (!state.isRecordingVoice && !state.recordedVoiceFile);
  elements.sendMessageButton.disabled = disabled;
  setToolButtonLabel(elements.recordVoiceButton, state.isRecordingVoice ? "Стоп" : "Голосовое");
  elements.recordVoiceButton.classList.toggle("recording", state.isRecordingVoice);
  elements.recordVoiceButton.classList.toggle("unsupported", Boolean(voiceSupportIssue));
  elements.recordVoiceButton.title = voiceSupportIssue || "";
  elements.clearRecordedVoiceButton.textContent = state.isRecordingVoice ? "Отмена" : "Удалить";
  elements.sendMessageButton.textContent = state.messageSubmitInFlight ? "Отправляем..." : "Отправить";
  elements.switchVideoNoteCameraButton.disabled =
    !state.isRecordingVideoNote || state.videoNoteCameraSwitchInFlight || !canSwitchVideoNoteCamera();
  renderVideoNoteRecordingPreview();
  renderRecordedVoicePreview();
  renderReplyComposer();
  renderMentionSuggestions();
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
  elements.videoNoteRecordingStatus.textContent = state.videoNoteRecordingLocked
    ? "Кружок · запись зафиксирована"
    : "Кружок · запись";
  elements.videoNoteRecordingMeta.textContent = state.videoNoteRecordingLocked
    ? `${elapsedLabel} · нажми кнопку еще раз, чтобы отправить`
    : `${elapsedLabel} · смахни вверх для фиксации`;
  elements.videoNoteRecordingTimer.textContent = elapsedLabel;
}

function setToolButtonLabel(button, label) {
  const labelElement = button?.querySelector(".tool-button-label");
  if (labelElement) {
    labelElement.textContent = label;
    return;
  }

  if (button) {
    button.textContent = label;
  }
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
        message.replyTo?.id ?? "",
        message.replyTo?.authorDisplayName ?? "",
        message.replyTo?.body ?? "",
        (message.replyTo?.attachments || [])
          .map((attachment) => [attachment.kind, attachment.fileName].join(":"))
          .join(","),
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
    showToast(getVideoNoteRecordingSupportIssue() || "Этот браузер не поддерживает запись кружков.", "error");
    return false;
  }

  let stream;
  try {
    stream = await getVideoNoteMediaStream({ includeAudio: true, facingMode: state.videoNoteCameraFacingMode });
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
  state.videoNotePointerStartY = null;
  state.videoNoteTargetConversationId = state.selectedConversationId;
  state.discardVideoNoteOnStop = false;
  state.videoNoteRecordingStartedAt = Date.now();
  state.videoNoteRecordingLocked = false;
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
  state.videoNotePointerStartY = null;
  state.videoNoteTargetConversationId = null;
  state.discardVideoNoteOnStop = false;
  state.videoNoteRecordingStartedAt = null;
  state.videoNoteRecordingLocked = false;
  state.videoNoteCameraSwitchInFlight = false;
}

async function uploadRecordedVideoNote(conversationId, file) {
  state.messageSubmitInFlight = true;
  renderComposerState();

  try {
    const formData = new FormData();
    formData.append("sendAsVideoNote", "true");
    if (state.replyToMessageId) {
      formData.append("replyToMessageId", String(state.replyToMessageId));
    }
    formData.append("files", file);

    await api(`/api/conversations/${conversationId}/messages/upload`, {
      method: "POST",
      body: formData,
    });

    if (conversationId === state.selectedConversationId) {
      clearReplyTarget();
      await loadMessages(conversationId);
    }
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    state.messageSubmitInFlight = false;
    renderComposerState();
  }
}

async function getVideoNoteMediaStream(options = {}) {
  const includeAudio = options.includeAudio ?? true;
  const facingMode = options.facingMode ?? state.videoNoteCameraFacingMode;
  const baseVideoConstraints = {
    width: { ideal: 480 },
    height: { ideal: 480 },
    aspectRatio: { ideal: 1 },
  };

  try {
    return await navigator.mediaDevices.getUserMedia({
      video: {
        ...baseVideoConstraints,
        facingMode: { ideal: facingMode },
      },
      ...(includeAudio ? { audio: true } : { audio: false }),
    });
  } catch (error) {
    if (!facingMode) {
      throw error;
    }

    return navigator.mediaDevices.getUserMedia({
      video: baseVideoConstraints,
      ...(includeAudio ? { audio: true } : { audio: false }),
    });
  }
}

function canSwitchVideoNoteCamera() {
  return Boolean(
    navigator.mediaDevices?.getUserMedia
    && state.isRecordingVideoNote
    && state.videoNoteRecorderStream
  );
}

async function switchVideoNoteCamera() {
  if (!canSwitchVideoNoteCamera() || state.videoNoteCameraSwitchInFlight) {
    return;
  }

  const nextFacingMode = state.videoNoteCameraFacingMode === "user" ? "environment" : "user";
  state.videoNoteCameraSwitchInFlight = true;
  renderComposerState();

  let nextVideoStream;
  try {
    nextVideoStream = await getVideoNoteMediaStream({
      includeAudio: false,
      facingMode: nextFacingMode,
    });
    const nextVideoTrack = nextVideoStream.getVideoTracks()[0];
    if (!nextVideoTrack || !state.videoNoteRecorderStream) {
      throw new Error("camera-switch-unavailable");
    }

    const currentStream = state.videoNoteRecorderStream;
    currentStream.getVideoTracks().forEach((track) => {
      currentStream.removeTrack(track);
      track.stop();
    });
    currentStream.addTrack(nextVideoTrack);
    state.videoNoteCameraFacingMode = nextFacingMode;

    detachVideoNoteRecordingPreview();
    attachVideoNoteRecordingPreview();
  } catch (error) {
    nextVideoStream?.getTracks().forEach((track) => track.stop());
    showToast("Не удалось переключить камеру.", "error");
  } finally {
    state.videoNoteCameraSwitchInFlight = false;
    renderVideoNoteRecordingPreview();
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
    showToast(getVoiceRecordingSupportIssue() || "Этот браузер не поддерживает запись голосовых.", "error");
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
  return !getVoiceRecordingSupportIssue();
}

function browserSupportsVideoNoteRecording() {
  return !getVideoNoteRecordingSupportIssue();
}

function getVoiceRecordingSupportIssue() {
  return getMediaRecordingSupportIssue("audio");
}

function getVideoNoteRecordingSupportIssue() {
  return getMediaRecordingSupportIssue("video");
}

function getMediaRecordingSupportIssue(kind) {
  const mediaLabel = kind === "video" ? "Запись кружков" : "Запись голосовых";
  const isLocalHost = ["localhost", "127.0.0.1", "::1"].includes(window.location.hostname);

  if (!window.isSecureContext && !isLocalHost) {
    return `${mediaLabel} доступна только через HTTPS или localhost. Сейчас Hermes открыт по обычному HTTP.`;
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    return `${mediaLabel} недоступна в этом браузере.`;
  }

  if (!window.MediaRecorder) {
    return `${mediaLabel} не поддерживается этим браузером.`;
  }

  return "";
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

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) {
    return;
  }

  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {
      // PWA support is best-effort in local dev.
    });
  });
}

render();
renderSelectedAttachments();
window.addEventListener("resize", () => {
  queueMetricValueFit();
  syncMobileLayout();
  renderConversationHeader();
});
registerServiceWorker();
if (state.token) {
  bootstrapSession();
}
