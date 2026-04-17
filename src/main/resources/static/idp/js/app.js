(() => {
  const storageKeys = {
    accessToken: "idp_access_token",
    refreshToken: "idp_refresh_token",
    userEmail: "idp_user_email"
  };

  const state = {
    accessToken: localStorage.getItem(storageKeys.accessToken) || "",
    refreshToken: localStorage.getItem(storageKeys.refreshToken) || "",
    userEmail: localStorage.getItem(storageKeys.userEmail) || "",
    authTxId: null,
    selectedMethod: null,
    challenge: null
  };

  const els = {
    authStatePill: byId("authStatePill"),
    authSummary: byId("authSummary"),
    registerForm: byId("registerForm"),
    registerStatus: byId("registerStatus"),
    useRegisterEmailBtn: byId("useRegisterEmailBtn"),
    loginForm: byId("loginForm"),
    loginStatus: byId("loginStatus"),
    verifyOtpBtn: byId("verifyOtpBtn"),
    verifyTotpBtn: byId("verifyTotpBtn"),
    verifyWebAuthnBtn: byId("verifyWebAuthnBtn"),
    refreshMeBtn: byId("refreshMeBtn"),
    mfaStatus: byId("mfaStatus"),
    startTotpEnrollmentBtn: byId("startTotpEnrollmentBtn"),
    activateTotpBtn: byId("activateTotpBtn"),
    activateEmailOtpBtn: byId("activateEmailOtpBtn"),
    registerPasskeyBtn: byId("registerPasskeyBtn"),
    activatePasskeyBtn: byId("activatePasskeyBtn"),
    totpQrBox: byId("totpQrBox"),
    authorizeForm: byId("authorizeForm"),
    authorizeStatus: byId("authorizeStatus"),
    generatePkceBtn: byId("generatePkceBtn"),
    exchangeTokenBtn: byId("exchangeTokenBtn"),
    refreshTokenBtn: byId("refreshTokenBtn"),
    loadUserInfoBtn: byId("loadUserInfoBtn"),
    loadSessionsBtn: byId("loadSessionsBtn"),
    revokeAllSessionsBtn: byId("revokeAllSessionsBtn"),
    sessionsBody: byId("sessionsBody"),
    consoleOutput: byId("consoleOutput")
  };

  init();

  function init() {
    bindEvents();
    if (state.userEmail) {
      const loginInput = byId("loginIdentifier");
      if (loginInput && !loginInput.value) {
        loginInput.value = state.userEmail;
      }
    }
    paintAuthState();
    if (state.accessToken) {
      refreshProfile().catch(() => {
        paintAuthState();
      });
    }
  }

  function bindEvents() {
    els.registerForm?.addEventListener("submit", onRegister);
    els.useRegisterEmailBtn?.addEventListener("click", () => {
      const email = byId("registerEmail")?.value?.trim();
      if (email) {
        byId("loginIdentifier").value = email;
        writeStatus(els.loginStatus, "Login identifier prefilled from registration form.", "ok");
      }
    });

    els.loginForm?.addEventListener("submit", onLoginStart);
    els.verifyOtpBtn?.addEventListener("click", () => verifyAuth("OTP"));
    els.verifyTotpBtn?.addEventListener("click", () => verifyAuth("TOTP"));
    els.verifyWebAuthnBtn?.addEventListener("click", verifyWithWebAuthn);
    els.refreshMeBtn?.addEventListener("click", refreshProfile);

    els.startTotpEnrollmentBtn?.addEventListener("click", registerTotp);
    els.activateTotpBtn?.addEventListener("click", activateTotp);
    els.activateEmailOtpBtn?.addEventListener("click", activateEmail);
    els.registerPasskeyBtn?.addEventListener("click", registerPasskey);
    els.activatePasskeyBtn?.addEventListener("click", activatePasskey);

    els.authorizeForm?.addEventListener("submit", authorizeUser);
    els.generatePkceBtn?.addEventListener("click", generatePkcePair);
    els.exchangeTokenBtn?.addEventListener("click", exchangeAuthorizationCode);
    els.refreshTokenBtn?.addEventListener("click", refreshTokens);
    els.loadUserInfoBtn?.addEventListener("click", loadUserInfo);

    els.loadSessionsBtn?.addEventListener("click", loadSessions);
    els.revokeAllSessionsBtn?.addEventListener("click", revokeAllSessions);

    els.sessionsBody?.addEventListener("click", async (event) => {
      const button = event.target.closest("button[data-revoke-session]");
      if (!button) {
        return;
      }
      const sessionId = button.getAttribute("data-revoke-session");
      if (!sessionId) {
        return;
      }
      await revokeSingleSession(sessionId);
    });
  }

  async function onRegister(event) {
    event.preventDefault();
    const payload = {
      email: byId("registerEmail").value.trim(),
      firstName: byId("registerFirstName").value.trim(),
      lastName: byId("registerLastName").value.trim(),
      phoneNumber: emptyToNull(byId("registerPhone").value),
      mfaEnabled: byId("registerMfaEnabled").value === "true",
      preferredMethod: emptyToNull(byId("registerPreferredMethod").value)
    };

    try {
      const response = await api("/auth/register", {
        method: "POST",
        body: payload
      });
      writeStatus(
        els.registerStatus,
        `User created: ${response.email} (${response.userId}), domain=${response.domain}`,
        "ok"
      );
      writeConsole("REGISTER", response);
      byId("loginIdentifier").value = response.email;
      state.userEmail = response.email;
      localStorage.setItem(storageKeys.userEmail, response.email);
    } catch (error) {
      writeStatus(els.registerStatus, error.message, "error");
      writeConsole("REGISTER_ERROR", { message: error.message });
    }
  }

  async function onLoginStart(event) {
    event.preventDefault();
    const identifier = byId("loginIdentifier").value.trim();
    const preferredMethod = emptyToNull(byId("loginMethod").value);
    const clientId = emptyToNull(byId("loginClientId").value) || "passwordless-web";

    try {
      const response = await api("/auth/login", {
        method: "POST",
        body: {
          identifier,
          preferredMethod,
          clientId
        }
      });

      state.authTxId = response.authTxId;
      state.selectedMethod = response.selectedMethod;
      state.challenge = response.challenge;
      state.userEmail = identifier;
      localStorage.setItem(storageKeys.userEmail, identifier);

      writeStatus(
        els.loginStatus,
        `Login started: tx=${response.authTxId}, method=${response.selectedMethod}, nextStep=${response.nextStep}`,
        "ok"
      );
      writeConsole("LOGIN_START", response);

      if (response.selectedMethod === "WEBAUTHN") {
        await verifyWithWebAuthn();
      }
    } catch (error) {
      writeStatus(els.loginStatus, error.message, "error");
      writeConsole("LOGIN_START_ERROR", { message: error.message });
    }
  }

  async function verifyAuth(method) {
    if (!state.authTxId) {
      writeStatus(els.loginStatus, "No auth transaction. Start login first.", "error");
      return;
    }

    try {
      let payload;
      if (method === "OTP") {
        const otp = byId("verifyOtp").value.trim();
        if (!otp) {
          throw new Error("OTP is required.");
        }
        payload = {
          authTxId: state.authTxId,
          method: "OTP",
          otp
        };
      } else {
        const totpRaw = byId("verifyTotp").value.trim();
        if (!totpRaw) {
          throw new Error("TOTP is required.");
        }
        payload = {
          authTxId: state.authTxId,
          method: "TOTP",
          totp: Number(totpRaw)
        };
      }

      const response = await api("/auth/mfa/verify", {
        method: "POST",
        body: payload
      });

      onAuthSuccess(response, method);
    } catch (error) {
      writeStatus(els.loginStatus, error.message, "error");
      writeConsole("VERIFY_ERROR", { method, message: error.message });
    }
  }

  async function verifyWithWebAuthn() {
    if (!state.authTxId || !state.challenge) {
      writeStatus(els.loginStatus, "No WebAuthn challenge available. Start login with WEBAUTHN first.", "error");
      return;
    }

    if (!window.PublicKeyCredential) {
      writeStatus(els.loginStatus, "WebAuthn is not supported by this browser.", "error");
      return;
    }

    try {
      const publicKey = normalizeRequestOptions(state.challenge);
      const assertion = await navigator.credentials.get({ publicKey });
      const payload = {
        authTxId: state.authTxId,
        method: "WEBAUTHN",
        webauthnAssertion: serializeAssertion(assertion)
      };

      const response = await api("/auth/mfa/verify", {
        method: "POST",
        body: payload
      });

      onAuthSuccess(response, "WEBAUTHN");
    } catch (error) {
      writeStatus(els.loginStatus, `WebAuthn verification failed: ${error.message}`, "error");
      writeConsole("WEBAUTHN_VERIFY_ERROR", { message: error.message });
    }
  }

  function onAuthSuccess(response, method) {
    persistTokens(response.accessToken, response.refreshToken);
    state.authTxId = null;
    state.selectedMethod = null;
    state.challenge = null;

    writeStatus(
      els.loginStatus,
      `Authenticated via ${method}. sessionId=${response.sessionId}`,
      "ok"
    );
    writeConsole("AUTH_SUCCESS", response);
    refreshProfile().catch(() => {
      paintAuthState();
    });
    loadSessions().catch(() => {});
  }

  async function registerTotp() {
    try {
      const response = await api("/auth/mfa/totp/register", {
        method: "POST",
        auth: true
      });

      if (response.qr) {
        const qrSource = normalizeTotpQrSource(response.qr);
        const qrImage = new Image();
        qrImage.alt = "TOTP QR";
        qrImage.src = qrSource;
        qrImage.decoding = "async";
        qrImage.addEventListener("error", () => {
          els.totpQrBox.innerHTML = '<span class="mono" style="opacity:0.7">Unable to render TOTP QR. Please retry.</span>';
          writeStatus(els.mfaStatus, "Unable to display TOTP QR image. Please retry.", "error");
        }, { once: true });
        els.totpQrBox.replaceChildren(qrImage);
      }

      writeStatus(
        els.mfaStatus,
        `TOTP enrollment generated for ${response.username}. Scan the QR and activate with a fresh code.`,
        "ok"
      );
      writeConsole("TOTP_REGISTER", response);
    } catch (error) {
      writeStatus(els.mfaStatus, error.message, "error");
      writeConsole("TOTP_REGISTER_ERROR", { message: error.message });
    }
  }

  async function activateTotp() {
    try {
      const totp = byId("activateTotpCode").value.trim();
      if (!totp) {
        throw new Error("Enter a TOTP code to activate.");
      }

      const response = await api("/auth/mfa/totp/activate", {
        method: "POST",
        auth: true,
        body: {
          totp: Number(totp)
        }
      });

      writeStatus(
        els.mfaStatus,
        `TOTP activated. preferredMfaMethod=${response.preferredMfaMethod}`,
        "ok"
      );
      writeConsole("TOTP_ACTIVATE", response);
      await refreshProfile();
    } catch (error) {
      writeStatus(els.mfaStatus, error.message, "error");
      writeConsole("TOTP_ACTIVATE_ERROR", { message: error.message });
    }
  }

  async function activateEmail() {
    try {
      const response = await api("/auth/mfa/email/activate", {
        method: "POST",
        auth: true
      });
      writeStatus(
        els.mfaStatus,
        `Email OTP set as preferred MFA method (${response.preferredMfaMethod}).`,
        "ok"
      );
      writeConsole("EMAIL_OTP_ACTIVATE", response);
      await refreshProfile();
    } catch (error) {
      writeStatus(els.mfaStatus, error.message, "error");
      writeConsole("EMAIL_OTP_ACTIVATE_ERROR", { message: error.message });
    }
  }

  async function registerPasskey() {
    if (!window.PublicKeyCredential) {
      writeStatus(els.mfaStatus, "WebAuthn is not supported by this browser.", "error");
      return;
    }

    const username = state.userEmail || byId("loginIdentifier").value.trim();
    if (!username) {
      writeStatus(els.mfaStatus, "Login or fill identifier first so the passkey can be bound to a user.", "error");
      return;
    }

    try {
      const challengeResponse = await fetch(`/webauthn/v1/register/challenge/${encodeURIComponent(username)}`, {
        method: "GET",
        credentials: "include"
      });

      if (!challengeResponse.ok) {
        throw new Error(`Failed to request registration challenge (${challengeResponse.status}).`);
      }

      const challengeBody = await challengeResponse.json();
      const publicKey = normalizeCreationOptions(challengeBody);
      const credential = await navigator.credentials.create({ publicKey });

      const registerResult = await api("/webauthn/v1/register/credential", {
        method: "POST",
        body: serializeCredential(credential),
        credentials: "include"
      });

      writeStatus(els.mfaStatus, `Passkey registered. credentialId=${registerResult.credentialId}`, "ok");
      writeConsole("PASSKEY_REGISTER", registerResult);
    } catch (error) {
      writeStatus(els.mfaStatus, `Passkey registration failed: ${error.message}`, "error");
      writeConsole("PASSKEY_REGISTER_ERROR", { message: error.message });
    }
  }

  async function activatePasskey() {
    try {
      const response = await api("/auth/mfa/webauthn/activate", {
        method: "POST",
        auth: true
      });
      writeStatus(
        els.mfaStatus,
        `Passkey set as preferred MFA method (${response.preferredMfaMethod}).`,
        "ok"
      );
      writeConsole("PASSKEY_ACTIVATE", response);
      await refreshProfile();
    } catch (error) {
      writeStatus(els.mfaStatus, error.message, "error");
      writeConsole("PASSKEY_ACTIVATE_ERROR", { message: error.message });
    }
  }

  async function refreshProfile() {
    try {
      const response = await api("/auth/me", {
        method: "GET",
        auth: true
      });

      state.userEmail = response.email;
      localStorage.setItem(storageKeys.userEmail, response.email || "");
      paintAuthState(response);
      writeConsole("PROFILE", response);
    } catch (error) {
      paintAuthState();
      writeStatus(els.loginStatus, error.message, "error");
      writeConsole("PROFILE_ERROR", { message: error.message });
      throw error;
    }
  }

  async function authorizeUser(event) {
    event.preventDefault();

    const payload = {
      responseType: "code",
      clientId: byId("authorizeClientId").value.trim(),
      redirectUri: byId("authorizeRedirectUri").value.trim(),
      scope: emptyToNull(byId("authorizeScope").value),
      state: emptyToNull(byId("authorizeState").value),
      codeChallenge: emptyToNull(byId("authorizeCodeChallenge").value),
      codeChallengeMethod: emptyToNull(byId("authorizeCodeChallengeMethod").value),
      nonce: emptyToNull(byId("authorizeNonce").value)
    };

    try {
      const response = await api("/oauth2/authorize", {
        method: "POST",
        auth: true,
        body: payload
      });

      if (response.code) {
        byId("tokenCode").value = response.code;
      }

      writeStatus(
        els.authorizeStatus,
        `Authorization approved. code=${response.code || "<none>"}`,
        "ok"
      );
      writeConsole("OAUTH_AUTHORIZE", response);
    } catch (error) {
      writeStatus(els.authorizeStatus, error.message, "error");
      writeConsole("OAUTH_AUTHORIZE_ERROR", { message: error.message });
    }
  }

  async function exchangeAuthorizationCode() {
    try {
      const code = byId("tokenCode").value.trim();
      if (!code) {
        throw new Error("Authorization code is required.");
      }

      const formData = new URLSearchParams();
      formData.set("grant_type", "authorization_code");
      formData.set("code", code);
      formData.set("client_id", byId("authorizeClientId").value.trim());
      formData.set("redirect_uri", byId("authorizeRedirectUri").value.trim());

      const codeVerifier = byId("pkceVerifier").value.trim();
      if (codeVerifier) {
        formData.set("code_verifier", codeVerifier);
      }

      const clientSecret = byId("tokenClientSecret").value.trim();
      if (clientSecret) {
        formData.set("client_secret", clientSecret);
      }

      const response = await api("/oauth2/token", {
        method: "POST",
        body: formData,
        asForm: true
      });

      persistTokens(response.access_token, response.refresh_token);
      writeStatus(els.authorizeStatus, "Token exchange completed successfully.", "ok");
      writeConsole("OAUTH_TOKEN", response);
      await refreshProfile();
    } catch (error) {
      writeStatus(els.authorizeStatus, error.message, "error");
      writeConsole("OAUTH_TOKEN_ERROR", { message: error.message });
    }
  }

  async function refreshTokens() {
    try {
      if (!state.refreshToken) {
        throw new Error("No refresh token available.");
      }

      const response = await api("/token/refresh", {
        method: "POST",
        body: {
          refreshToken: state.refreshToken
        }
      });

      persistTokens(response.accessToken, response.refreshToken);
      writeStatus(els.authorizeStatus, "Refresh token flow completed.", "ok");
      writeConsole("TOKEN_REFRESH", response);
      await refreshProfile();
    } catch (error) {
      writeStatus(els.authorizeStatus, error.message, "error");
      writeConsole("TOKEN_REFRESH_ERROR", { message: error.message });
    }
  }

  async function loadUserInfo() {
    try {
      const response = await api("/oauth2/userinfo", {
        method: "GET",
        auth: true
      });
      writeConsole("OAUTH_USERINFO", response);
      writeStatus(els.authorizeStatus, "Loaded /oauth2/userinfo successfully.", "ok");
    } catch (error) {
      writeStatus(els.authorizeStatus, error.message, "error");
      writeConsole("OAUTH_USERINFO_ERROR", { message: error.message });
    }
  }

  async function loadSessions() {
    try {
      const sessions = await api("/auth/sessions", {
        method: "GET",
        auth: true
      });

      if (!Array.isArray(sessions) || sessions.length === 0) {
        els.sessionsBody.innerHTML = "<tr><td colspan=\"5\" class=\"mono\">No active sessions.</td></tr>";
        writeConsole("SESSIONS", []);
        return;
      }

      els.sessionsBody.innerHTML = sessions
        .map((session) => {
          const marker = session.current ? " (current)" : "";
          const lastActivity = session.lastActivityAt ? new Date(session.lastActivityAt).toLocaleString() : "-";
          return `
            <tr>
              <td class="mono">${escapeHtml(trimMid(session.sessionId, 24))}${marker}</td>
              <td>${escapeHtml(session.authMethod || "-")}</td>
              <td class="mono">${escapeHtml(session.ipAddress || "-")}</td>
              <td>${escapeHtml(lastActivity)}</td>
              <td><button class="btn-danger" data-revoke-session="${escapeHtml(session.sessionId)}">Revoke</button></td>
            </tr>
          `;
        })
        .join("");

      writeConsole("SESSIONS", sessions);
    } catch (error) {
      els.sessionsBody.innerHTML = `<tr><td colspan="5" class="mono">${escapeHtml(error.message)}</td></tr>`;
      writeConsole("SESSIONS_ERROR", { message: error.message });
    }
  }

  async function revokeSingleSession(sessionId) {
    try {
      await api(`/auth/sessions/${encodeURIComponent(sessionId)}/revoke`, {
        method: "POST",
        auth: true
      });
      writeConsole("REVOKE_SESSION", { sessionId });
      await loadSessions();
    } catch (error) {
      writeConsole("REVOKE_SESSION_ERROR", { sessionId, message: error.message });
    }
  }

  async function revokeAllSessions() {
    try {
      const response = await api("/auth/sessions/revoke-all", {
        method: "POST",
        auth: true
      });
      writeConsole("REVOKE_ALL_SESSIONS", response);
      await loadSessions();
    } catch (error) {
      writeConsole("REVOKE_ALL_SESSIONS_ERROR", { message: error.message });
    }
  }

  async function generatePkcePair() {
    try {
      const verifier = randomVerifier(64);
      const challenge = await createS256Challenge(verifier);
      byId("pkceVerifier").value = verifier;
      byId("authorizeCodeChallenge").value = challenge;
      byId("authorizeCodeChallengeMethod").value = "S256";
      writeStatus(els.authorizeStatus, "Generated PKCE verifier/challenge pair.", "ok");
    } catch (error) {
      writeStatus(els.authorizeStatus, error.message, "error");
    }
  }

  async function createS256Challenge(verifier) {
    const data = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest("SHA-256", data);
    return bytesToBase64Url(new Uint8Array(digest));
  }

  async function api(path, options = {}) {
    const {
      method = "GET",
      body,
      auth = false,
      asForm = false,
      credentials = "same-origin"
    } = options;

    const headers = {};

    if (auth) {
      if (!state.accessToken) {
        throw new Error("Authentication required. Login first.");
      }
      headers.Authorization = `Bearer ${state.accessToken}`;
    }

    let payload = undefined;
    if (body !== undefined) {
      if (asForm) {
        headers["Content-Type"] = "application/x-www-form-urlencoded";
        payload = body instanceof URLSearchParams ? body.toString() : new URLSearchParams(body).toString();
      } else {
        headers["Content-Type"] = "application/json";
        payload = typeof body === "string" ? body : JSON.stringify(body);
      }
    }

    const response = await fetch(path, {
      method,
      headers,
      credentials,
      body: payload
    });

    const contentType = response.headers.get("content-type") || "";
    let responseBody;

    if (contentType.includes("application/json")) {
      responseBody = await response.json();
    } else {
      responseBody = await response.text();
    }

    if (!response.ok) {
      const message = typeof responseBody === "object"
        ? responseBody.error || JSON.stringify(responseBody)
        : responseBody || `HTTP ${response.status}`;
      throw new Error(message);
    }

    return responseBody;
  }

  function persistTokens(accessToken, refreshToken) {
    if (accessToken) {
      state.accessToken = accessToken;
      localStorage.setItem(storageKeys.accessToken, accessToken);
    }
    if (refreshToken) {
      state.refreshToken = refreshToken;
      localStorage.setItem(storageKeys.refreshToken, refreshToken);
    }
    paintAuthState();
  }

  function paintAuthState(profile) {
    if (state.accessToken) {
      els.authStatePill.textContent = "Authenticated";
      els.authSummary.textContent = profile
        ? `${profile.email} | role=${profile.role} | preferredMfa=${profile.preferredMfaMethod || "none"}`
        : `${trimMid(state.accessToken, 30)} | refresh=${state.refreshToken ? "yes" : "no"}`;
      return;
    }

    els.authStatePill.textContent = "Not authenticated";
    els.authSummary.textContent = "No active token loaded.";
  }

  function writeStatus(element, message, kind = "") {
    if (!element) {
      return;
    }
    element.textContent = message;
    element.className = "status" + (kind ? ` ${kind}` : "");
  }

  function writeConsole(eventName, payload) {
    const now = new Date().toISOString();
    const body = typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
    const entry = `[${now}] ${eventName}\n${body}`;
    const previous = els.consoleOutput.textContent;
    els.consoleOutput.textContent = `${entry}\n\n${previous}`.trim().slice(0, 32000);
  }

  function normalizeCreationOptions(input) {
    const options = structuredClone(input);
    options.challenge = base64ToBytes(unwrapByteField(options.challenge));

    if (!options.user || !options.user.id) {
      throw new Error("Invalid registration challenge: user.id is missing.");
    }
    options.user.id = base64ToBytes(unwrapByteField(options.user.id));

    if (Array.isArray(options.excludeCredentials)) {
      options.excludeCredentials = options.excludeCredentials.map((credential) => ({
        ...credential,
        id: base64ToBytes(unwrapByteField(credential.id))
      }));
    }

    return options;
  }

  function normalizeRequestOptions(input) {
    const options = structuredClone(input);
    options.challenge = base64ToBytes(unwrapByteField(options.challenge));

    if (Array.isArray(options.allowCredentials)) {
      options.allowCredentials = options.allowCredentials.map((credential) => ({
        ...credential,
        id: base64ToBytes(unwrapByteField(credential.id))
      }));
    }

    return options;
  }

  function serializeCredential(credential) {
    return {
      id: credential.id,
      rawId: bytesToBase64Url(new Uint8Array(credential.rawId)),
      type: credential.type,
      response: {
        attestationObject: bytesToBase64Url(new Uint8Array(credential.response.attestationObject)),
        clientDataJSON: bytesToBase64Url(new Uint8Array(credential.response.clientDataJSON))
      }
    };
  }

  function serializeAssertion(assertion) {
    return {
      id: assertion.id,
      rawId: bytesToBase64Url(new Uint8Array(assertion.rawId)),
      type: assertion.type,
      response: {
        authenticatorData: bytesToBase64Url(new Uint8Array(assertion.response.authenticatorData)),
        clientDataJSON: bytesToBase64Url(new Uint8Array(assertion.response.clientDataJSON)),
        signature: bytesToBase64Url(new Uint8Array(assertion.response.signature)),
        userHandle: assertion.response.userHandle
          ? bytesToBase64Url(new Uint8Array(assertion.response.userHandle))
          : ""
      }
    };
  }

  function unwrapByteField(value) {
    if (value && typeof value === "object" && "value" in value) {
      return value.value;
    }
    return value;
  }

  function base64ToBytes(value) {
    const encoded = String(value || "")
      .replace(/-/g, "+")
      .replace(/_/g, "/");
    const padded = encoded + "=".repeat((4 - (encoded.length % 4)) % 4);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  function bytesToBase64Url(bytes) {
    let binary = "";
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function randomVerifier(length) {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    let result = "";
    for (let i = 0; i < values.length; i += 1) {
      result += alphabet[values[i] % alphabet.length];
    }
    return result;
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function emptyToNull(value) {
    const normalized = (value || "").trim();
    return normalized ? normalized : null;
  }

  function normalizeTotpQrSource(value) {
    const raw = String(value || "").trim();
    if (!raw) {
      return "";
    }
    if (raw.startsWith("data:image")) {
      return raw;
    }
    return `data:image/png;base64,${raw.replace(/\s+/g, "")}`;
  }

  function trimMid(value, max) {
    const source = String(value || "");
    if (source.length <= max) {
      return source;
    }
    const head = source.slice(0, Math.floor(max / 2) - 1);
    const tail = source.slice(source.length - Math.floor(max / 2) + 1);
    return `${head}...${tail}`;
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }
})();
