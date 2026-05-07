import { api } from "../api.js";
import { ROUTES, goTo } from "../routes.js";
import { requireUnauthenticated } from "../guards.js";
import { byId, emptyToNull, setLoading, setStatus } from "../ui.js";
import { getState, setAuthTransaction, setUserEmail, setOauthRequestId } from "../store.js";

document.addEventListener("DOMContentLoaded", () => {
  if (!requireUnauthenticated()) {
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const oauthReqId = params.get("oauth_request_id");
  if (oauthReqId) {
    setOauthRequestId(oauthReqId);
    document.querySelectorAll('a[href^="/register/"]').forEach(link => {
      link.href = `/register/?oauth_request_id=${encodeURIComponent(oauthReqId)}`;
    });
  }

  const state = getState();
  const identifierInput = byId("loginIdentifier");
  if (state.userEmail && identifierInput && !identifierInput.value) {
    identifierInput.value = state.userEmail;
  }

  byId("loginForm")?.addEventListener("submit", onLoginSubmit);
});

async function onLoginSubmit(event) {
  event.preventDefault();

  const status = byId("loginStatus");
  const submitBtn = byId("loginSubmit");
  const identifier = byId("loginIdentifier").value.trim();
  const preferredMethod = emptyToNull(byId("loginMethod").value);
  const clientId = emptyToNull(byId("loginClientId").value) || "passwordless-web";

  if (!identifier) {
    setStatus(status, "Identifier is required.", "error");
    return;
  }

  setLoading(submitBtn, true, "Starting...");
  try {
    const response = await api("/auth/login", {
      method: "POST",
      body: {
        identifier,
        preferredMethod,
        clientId
      }
    });

    if (!response?.authTxId) {
      throw new Error("Login did not return an authTxId.");
    }

    setAuthTransaction({
      authTxId: response.authTxId,
      selectedMethod: response.selectedMethod,
      challenge: response.challenge,
      userEmail: identifier
    });
    setUserEmail(identifier);

    setStatus(
      status,
      `Login started. Method=${response.selectedMethod || "AUTO"}.`,
      "ok"
    );
    goTo(ROUTES.verifyOtp);
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(submitBtn, false);
  }
}
