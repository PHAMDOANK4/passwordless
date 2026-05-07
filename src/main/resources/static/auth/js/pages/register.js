import { api } from "../api.js";
import { ROUTES, goTo } from "../routes.js";
import { requireUnauthenticated } from "../guards.js";
import { byId, emptyToNull, setLoading, setStatus } from "../ui.js";
import { setAuthTransaction, setUserEmail, setOauthRequestId } from "../store.js";

document.addEventListener("DOMContentLoaded", () => {
  if (!requireUnauthenticated()) {
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const oauthReqId = params.get("oauth_request_id");
  if (oauthReqId) {
    setOauthRequestId(oauthReqId);
  }

  byId("registerForm")?.addEventListener("submit", onRegisterSubmit);
});

async function onRegisterSubmit(event) {
  event.preventDefault();

  const status = byId("registerStatus");
  const submitBtn = byId("registerSubmit");

  const payload = {
    email: byId("registerEmail").value.trim(),
    firstName: byId("registerFirstName").value.trim(),
    lastName: byId("registerLastName").value.trim(),
    phoneNumber: emptyToNull(byId("registerPhone").value),
    mfaEnabled: byId("registerMfaEnabled").value === "true",
    preferredMethod: emptyToNull(byId("registerPreferredMethod").value)
  };

  if (!payload.email || !payload.firstName || !payload.lastName) {
    setStatus(status, "Email, first name, and last name are required.", "error");
    return;
  }

  setLoading(submitBtn, true, "Creating...");
  try {
    const response = await api("/auth/register", {
      method: "POST",
      body: payload
    });

    setUserEmail(response.email || payload.email);
    setStatus(status, `User created: ${response.email}.`, "ok");

    await startLogin(response.email || payload.email, payload.preferredMethod);
    goTo(ROUTES.verifyOtp);
  } catch (error) {
    setStatus(status, error.message, "error");
  } finally {
    setLoading(submitBtn, false);
  }
}

async function startLogin(identifier, preferredMethod) {
  const response = await api("/auth/login", {
    method: "POST",
    body: {
      identifier,
      preferredMethod,
      clientId: "passwordless-web"
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
}
