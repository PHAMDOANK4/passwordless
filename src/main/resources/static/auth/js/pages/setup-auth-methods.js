import { api } from "../api.js";
import { ROUTES, goTo } from "../routes.js";
import { requireAuthenticated } from "../guards.js";
import { byId, setLoading, setStatus, setText } from "../ui.js";
import { clearAuth, getState, setUserEmail } from "../store.js";
import { normalizeCreationOptions, serializeCredential } from "../webauthn.js";

document.addEventListener("DOMContentLoaded", () => {
  if (!requireAuthenticated()) {
    return;
  }

  byId("refreshProfileBtn")?.addEventListener("click", loadProfile);
  byId("logoutBtn")?.addEventListener("click", onLogout);
  byId("activateEmailBtn")?.addEventListener("click", activateEmail);
  byId("startTotpBtn")?.addEventListener("click", startTotp);
  byId("activateTotpBtn")?.addEventListener("click", activateTotp);
  byId("registerPasskeyBtn")?.addEventListener("click", registerPasskey);
  byId("activatePasskeyBtn")?.addEventListener("click", activatePasskey);

  loadProfile();
});

async function loadProfile() {
  const status = byId("profileStatus");
  const btn = byId("refreshProfileBtn");
  setLoading(btn, true, "Refreshing...");

  try {
    const profile = await api("/auth/me", {
      method: "GET",
      auth: true
    });

    const summary = `${profile.email || "-"} | role=${profile.role || "user"} | preferredMfa=${profile.preferredMfaMethod || "none"}`;
    setUserEmail(profile.email || "");
    setText(byId("profileSummary"), summary);
    setStatus(status, "Profile loaded.", "ok");
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(btn, false);
  }
}

async function activateEmail() {
  const status = byId("emailStatus");
  const btn = byId("activateEmailBtn");
  setLoading(btn, true, "Activating...");

  try {
    const response = await api("/auth/mfa/email/activate", {
      method: "POST",
      auth: true
    });
    setStatus(status, `Email OTP active. preferred=${response.preferredMfaMethod || "email"}`, "ok");
    loadProfile();
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(btn, false);
  }
}

async function startTotp() {
  const status = byId("totpStatus");
  const btn = byId("startTotpBtn");
  const qrBox = byId("totpQrBox");
  setLoading(btn, true, "Generating...");

  try {
    const response = await api("/auth/mfa/totp/register", {
      method: "POST",
      auth: true
    });

    const qrSource = normalizeTotpQrSource(response.qr);
    if (qrSource) {
      const qrImage = new Image();
      qrImage.alt = "TOTP QR";
      qrImage.src = qrSource;
      qrImage.decoding = "async";
      qrBox.replaceChildren(qrImage);
    } else {
      qrBox.textContent = "QR not available.";
    }

    setStatus(status, "TOTP enrollment generated. Scan and activate.", "ok");
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(btn, false);
  }
}

async function activateTotp() {
  const status = byId("totpStatus");
  const btn = byId("activateTotpBtn");
  const totp = byId("totpActivateCode").value.trim();

  if (!totp) {
    setStatus(status, "Enter a TOTP code to activate.", "error");
    return;
  }

  setLoading(btn, true, "Activating...");
  try {
    const response = await api("/auth/mfa/totp/activate", {
      method: "POST",
      auth: true,
      body: {
        totp: Number(totp)
      }
    });
    setStatus(status, `TOTP active. preferred=${response.preferredMfaMethod || "totp"}`, "ok");
    loadProfile();
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(btn, false);
  }
}

async function registerPasskey() {
  const status = byId("passkeyStatus");
  const btn = byId("registerPasskeyBtn");
  const state = getState();
  const username = state.userEmail;

  if (!window.PublicKeyCredential) {
    setStatus(status, "WebAuthn is not supported by this browser.", "error");
    return;
  }

  if (!username) {
    setStatus(status, "Missing username. Login again.", "error");
    return;
  }

  setLoading(btn, true, "Registering...");
  try {
    const begin = await api("/webauthn/v1/register/begin", {
      method: "POST",
      body: {
        username,
        authenticatorAttachment: "cross-platform",
        residentKeyRequired: false,
        userVerification: "preferred"
      },
      credentials: "include"
    });

    if (!begin?.transactionId || !begin?.publicKey) {
      throw new Error("Invalid WebAuthn begin response.");
    }

    const publicKey = normalizeCreationOptions(begin.publicKey);
    const credential = await navigator.credentials.create({ publicKey });

    const finish = await api("/webauthn/v1/register/finish", {
      method: "POST",
      body: {
        transactionId: begin.transactionId,
        credential: serializeCredential(credential)
      },
      credentials: "include"
    });

    setStatus(status, `Passkey registered. credentialId=${finish.credentialId || "-"}`, "ok");
  } catch (error) {
    setStatus(status, `Passkey registration failed: ${error.message}`, "error");
  } finally {
    setLoading(btn, false);
  }
}

async function activatePasskey() {
  const status = byId("passkeyStatus");
  const btn = byId("activatePasskeyBtn");
  setLoading(btn, true, "Activating...");

  try {
    const response = await api("/auth/mfa/webauthn/activate", {
      method: "POST",
      auth: true
    });
    setStatus(status, `Passkey preferred. preferred=${response.preferredMfaMethod || "webauthn"}`, "ok");
    loadProfile();
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(btn, false);
  }
}

function onLogout() {
  clearAuth();
  goTo(ROUTES.login);
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
