import { api } from "../api.js";
import { ROUTES, goTo } from "../routes.js";
import { requirePendingOtp } from "../guards.js";
import { byId, hide, setLoading, setStatus, setText, show } from "../ui.js";
import { clearAuthTransaction, getState, setTokens } from "../store.js";
import { normalizeRequestOptions, serializeAssertion } from "../webauthn.js";

document.addEventListener("DOMContentLoaded", () => {
  if (!requirePendingOtp()) {
    return;
  }

  const state = getState();
  const method = (state.selectedMethod || "OTP").toUpperCase();

  setText(byId("verifyIdentifier"), state.userEmail || "-");
  setText(byId("verifyMethod"), method);

  setupSections(method);

  byId("verifyOtpBtn")?.addEventListener("click", () => verifyOtp("OTP"));
  byId("verifyTotpBtn")?.addEventListener("click", () => verifyOtp("TOTP"));
  byId("verifyWebAuthnBtn")?.addEventListener("click", verifyWebAuthn);

  if (method === "WEBAUTHN") {
    verifyWebAuthn();
  }
});

function setupSections(method) {
  const otpSection = byId("otpSection");
  const totpSection = byId("totpSection");
  const webauthnSection = byId("webauthnSection");

  hide(otpSection);
  hide(totpSection);
  hide(webauthnSection);

  if (method === "TOTP") {
    show(totpSection);
    return;
  }

  if (method === "WEBAUTHN") {
    show(webauthnSection);
    return;
  }

  show(otpSection);
}

async function verifyOtp(method) {
  const status = byId("verifyStatus");
  const submitBtn = method === "TOTP" ? byId("verifyTotpBtn") : byId("verifyOtpBtn");
  const state = getState();

  if (!state.authTxId) {
    setStatus(status, "No auth transaction found. Start login again.", "error");
    return;
  }

  const payload = { authTxId: state.authTxId, method };

  if (method === "TOTP") {
    const totp = byId("totpCode").value.trim();
    if (!totp) {
      setStatus(status, "TOTP code is required.", "error");
      return;
    }
    payload.totp = Number(totp);
  } else {
    const otp = byId("otpCode").value.trim();
    if (!otp) {
      setStatus(status, "OTP code is required.", "error");
      return;
    }
    payload.otp = otp;
  }

  setLoading(submitBtn, true, "Verifying...");
  try {
    const response = await api("/auth/mfa/verify", {
      method: "POST",
      body: payload
    });

    onVerifySuccess(response);
    setStatus(status, "Verification complete. Redirecting...", "ok");
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(submitBtn, false);
  }
}

async function verifyWebAuthn() {
  const status = byId("verifyStatus");
  const submitBtn = byId("verifyWebAuthnBtn");
  const state = getState();

  if (!state.authTxId || !state.challenge) {
    setStatus(status, "No WebAuthn challenge found. Start login again.", "error");
    return;
  }

  if (!window.PublicKeyCredential) {
    setStatus(status, "WebAuthn is not supported by this browser.", "error");
    return;
  }

  setLoading(submitBtn, true, "Verifying...");
  try {
    const publicKey = normalizeRequestOptions(state.challenge);
    const assertion = await navigator.credentials.get({ publicKey });

    const response = await api("/auth/mfa/verify", {
      method: "POST",
      body: {
        authTxId: state.authTxId,
        method: "WEBAUTHN",
        webauthnAssertion: serializeAssertion(assertion)
      }
    });

    onVerifySuccess(response);
    setStatus(status, "Passkey verified. Redirecting...", "ok");
  } catch (error) {
    setStatus(status, `WebAuthn verification failed: ${error.message}`, "error");
  } finally {
    setLoading(submitBtn, false);
  }
}

function onVerifySuccess(response) {
  const accessToken = response.accessToken || response.access_token || "";
  const refreshToken = response.refreshToken || response.refresh_token || "";

  if (!accessToken) {
    throw new Error("No access token returned from verification.");
  }

  setTokens(accessToken, refreshToken);
  clearAuthTransaction();

  window.setTimeout(() => {
    goTo(ROUTES.setupAuthMethods);
  }, 1200);
}
