package com.vladislav.tgclone.tdlight;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TdlightLocalQrPageController {

    private final String masterToken;

    public TdlightLocalQrPageController(
        @Value("${app.auth.master-token:}") String masterToken
    ) {
        this.masterToken = masterToken == null ? "" : masterToken.trim();
    }

    @GetMapping(value = "/local/tdlight/qr", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> qrPage() {
        String html = """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>TDLight QR Live</title>
              <style>
                :root {
                  color-scheme: dark;
                  --bg: #0e1116;
                  --panel: #171c24;
                  --muted: #95a2b3;
                  --line: #293241;
                  --accent: #5cc8ff;
                  --ok: #79e2a0;
                  --warn: #ffd166;
                  --err: #ff7b72;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: radial-gradient(circle at top, #16202c 0%, var(--bg) 48%);
                  color: #f6f8fb;
                  min-height: 100vh;
                }
                .wrap {
                  max-width: 980px;
                  margin: 0 auto;
                  padding: 32px 20px 48px;
                }
                .card {
                  background: rgba(23, 28, 36, 0.94);
                  border: 1px solid var(--line);
                  border-radius: 24px;
                  padding: 24px;
                  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
                }
                h1 {
                  margin: 0 0 8px;
                  font-size: clamp(32px, 4vw, 52px);
                  line-height: 0.98;
                }
                p {
                  margin: 0;
                  color: var(--muted);
                  line-height: 1.5;
                }
                .grid {
                  display: grid;
                  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
                  gap: 24px;
                  margin-top: 24px;
                }
                .qr-box {
                  background: #fff;
                  border-radius: 20px;
                  padding: 20px;
                  min-height: 360px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                .qr-box img {
                  width: 100%;
                  max-width: 320px;
                  height: auto;
                  display: block;
                }
                .placeholder {
                  color: #243042;
                  text-align: center;
                  font-weight: 600;
                }
                .status-list {
                  display: grid;
                  gap: 12px;
                }
                .status-row {
                  border: 1px solid var(--line);
                  border-radius: 16px;
                  padding: 14px 16px;
                  background: rgba(255, 255, 255, 0.02);
                }
                .label {
                  display: block;
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.08em;
                  color: var(--muted);
                  margin-bottom: 6px;
                }
                .value {
                  font-size: 18px;
                  font-weight: 600;
                  word-break: break-word;
                }
                .value.muted { color: var(--muted); }
                .value.ok { color: var(--ok); }
                .value.warn { color: var(--warn); }
                .value.err { color: var(--err); }
                .actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 12px;
                  margin-top: 18px;
                }
                button {
                  border: 0;
                  border-radius: 999px;
                  padding: 12px 18px;
                  font-size: 15px;
                  font-weight: 700;
                  cursor: pointer;
                  background: var(--accent);
                  color: #07131f;
                }
                button.secondary {
                  background: #263241;
                  color: #f6f8fb;
                }
                code {
                  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                  font-size: 13px;
                }
                .hint {
                  margin-top: 18px;
                  font-size: 14px;
                }
                @media (max-width: 860px) {
                  .grid {
                    grid-template-columns: 1fr;
                  }
                }
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="card">
                  <h1>TDLight QR Live</h1>
                  <p>Эта local-only страница сама поднимает сессию, создаёт dev connection, стартует QR auth и автоматически обновляет QR-код, если Telegram выдаёт новый token.</p>
                  <div class="grid">
                    <div>
                      <div class="qr-box">
                        <div class="placeholder" id="qr-placeholder">Готовим QR...</div>
                        <img id="qr-image" alt="TDLight login QR" hidden />
                      </div>
                      <div class="actions">
                        <button id="restart-btn" type="button">Перезапустить QR</button>
                        <button id="open-link-btn" class="secondary" type="button">Открыть tg://login</button>
                      </div>
                    </div>
                    <div class="status-list">
                      <div class="status-row">
                        <span class="label">Session</span>
                        <div class="value" id="session-value">starting</div>
                      </div>
                      <div class="status-row">
                        <span class="label">Connection</span>
                        <div class="value" id="connection-value">pending</div>
                      </div>
                      <div class="status-row">
                        <span class="label">Phase</span>
                        <div class="value" id="phase-value">NOT_STARTED</div>
                      </div>
                      <div class="status-row">
                        <span class="label">Updated</span>
                        <div class="value muted" id="updated-value">n/a</div>
                      </div>
                      <div class="status-row">
                        <span class="label">Last Error</span>
                        <div class="value muted" id="error-value">none</div>
                      </div>
                      <div class="status-row">
                        <span class="label">QR Link</span>
                        <div class="value"><code id="link-value">loading...</code></div>
                      </div>
                    </div>
                  </div>
                  <p class="hint">Если Telegram отвечает <code>AUTH_TOKEN_EXPIRED</code>, просто подождите 1-2 секунды: страница сама перезапустит QR и подменит картинку на новый token.</p>
                </div>
              </div>
              <script>
                const masterToken = "__MASTER_TOKEN__";
                const qrImage = document.getElementById("qr-image");
                const qrPlaceholder = document.getElementById("qr-placeholder");
                const restartBtn = document.getElementById("restart-btn");
                const openLinkBtn = document.getElementById("open-link-btn");
                const sessionValue = document.getElementById("session-value");
                const connectionValue = document.getElementById("connection-value");
                const phaseValue = document.getElementById("phase-value");
                const updatedValue = document.getElementById("updated-value");
                const errorValue = document.getElementById("error-value");
                const linkValue = document.getElementById("link-value");

                let accessToken = "";
                let connectionId = null;
                let currentQrLink = "";
                let currentQrObjectUrl = "";
                let pollHandle = null;
                let restartInFlight = false;

                function setStatus(el, text, cls = "") {
                  el.textContent = text;
                  el.className = "value" + (cls ? " " + cls : "");
                }

                async function api(path, init = {}) {
                  const headers = {
                    ...(init.headers || {}),
                  };
                  if (accessToken) {
                    headers.Authorization = `Bearer ${accessToken}`;
                  }
                  const response = await fetch(path, {
                    ...init,
                    headers,
                    credentials: "include",
                  });
                  if (!response.ok) {
                    const text = await response.text();
                    throw new Error(text || `HTTP ${response.status}`);
                  }
                  const contentType = response.headers.get("content-type") || "";
                  if (contentType.includes("application/json")) {
                    return response.json();
                  }
                  return response.text();
                }

                async function bootstrapSession() {
                  sessionValue.textContent = "bootstrapping";
                  const payload = await api("/api/auth/session", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ token: masterToken }),
                  });
                  accessToken = payload.accessToken;
                  setStatus(sessionValue, payload.user?.username || "ok", "ok");
                }

                async function ensureConnection() {
                  const list = await api("/api/tdlight/connections");
                  if (Array.isArray(list) && list.length > 0) {
                    connectionId = list[0].id;
                    setStatus(connectionValue, `#${connectionId}`, "ok");
                    return;
                  }
                  const created = await api("/api/tdlight/connections/dev", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                      phoneMask: "+7 *** ***-**-**",
                      tdlightUserId: "local-browser-qr",
                    }),
                  });
                  connectionId = created.id;
                  setStatus(connectionValue, `#${connectionId}`, "ok");
                }

                async function setQrFromLink(link) {
                  if (!link) {
                    qrImage.hidden = true;
                    qrPlaceholder.hidden = false;
                    qrPlaceholder.textContent = "Ожидаем QR token...";
                    return;
                  }
                  const response = await fetch(`/local/tdlight/qr/image?link=${encodeURIComponent(link)}`);
                  if (!response.ok) {
                    throw new Error(`QR image HTTP ${response.status}`);
                  }
                  const blob = await response.blob();
                  const objectUrl = URL.createObjectURL(blob);
                  if (currentQrObjectUrl) {
                    URL.revokeObjectURL(currentQrObjectUrl);
                  }
                  currentQrObjectUrl = objectUrl;
                  qrImage.src = objectUrl;
                  qrImage.hidden = false;
                  qrPlaceholder.hidden = true;
                  currentQrLink = link;
                  linkValue.textContent = link;
                }

                async function restartQr() {
                  if (restartInFlight) {
                    return;
                  }
                  restartInFlight = true;
                  try {
                    const status = await api("/api/tdlight/auth/qr/start", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: JSON.stringify({ tdlightConnectionId: connectionId }),
                    });
                    await renderStatus(status);
                  } finally {
                    restartInFlight = false;
                  }
                }

                async function renderStatus(status) {
                  const phase = status.phase || "UNKNOWN";
                  const phaseClass = phase === "READY" ? "ok" : phase === "FAILED" ? "err" : phase === "WAIT_QR_SCAN" ? "warn" : "";
                  setStatus(phaseValue, phase, phaseClass);
                  updatedValue.textContent = status.updatedAt || "n/a";
                  errorValue.textContent = status.lastError || "none";
                  errorValue.className = "value" + (status.lastError ? " err" : " muted");
                  openLinkBtn.disabled = !status.qrLink;
                  if (status.qrLink && status.qrLink !== currentQrLink) {
                    await setQrFromLink(status.qrLink);
                  }
                  if (!status.qrLink) {
                    linkValue.textContent = "waiting...";
                  }
                }

                async function pollStatus() {
                  if (!connectionId) {
                    return;
                  }
                  try {
                    const status = await api(`/api/tdlight/auth/${connectionId}/status`);
                    await renderStatus(status);
                    if ((status.lastError || "").includes("AUTH_TOKEN_EXPIRED")) {
                      await restartQr();
                    }
                  } catch (error) {
                    setStatus(phaseValue, "FAILED", "err");
                    errorValue.textContent = error.message;
                    errorValue.className = "value err";
                  }
                }

                restartBtn.addEventListener("click", () => restartQr());
                openLinkBtn.addEventListener("click", () => {
                  if (currentQrLink) {
                    window.location.href = currentQrLink;
                  }
                });

                async function init() {
                  try {
                    await bootstrapSession();
                    await ensureConnection();
                    await restartQr();
                    await pollStatus();
                    pollHandle = window.setInterval(pollStatus, 2000);
                  } catch (error) {
                    setStatus(sessionValue, "failed", "err");
                    errorValue.textContent = error.message;
                    errorValue.className = "value err";
                  }
                }

                init();
              </script>
            </body>
            </html>
            """.replace("\"__MASTER_TOKEN__\"", toJsStringLiteral(masterToken));

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(html);
    }

    @GetMapping(value = "/local/tdlight/qr/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(@RequestParam("link") String link) throws Exception {
        if (link == null || link.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        BitMatrix matrix = new MultiFormatWriter().encode(
            link,
            BarcodeFormat.QR_CODE,
            420,
            420,
            Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name())
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_PNG)
            .body(output.toByteArray());
    }

    private String toJsStringLiteral(String value) {
        String safe = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
        return "\"" + safe + "\"";
    }
}
