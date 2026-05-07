const storageKeys = {
  accessToken: "idp_access_token",
  refreshToken: "idp_refresh_token",
  userEmail: "idp_user_email",
  authStage: "idp_auth_stage",
  authTxId: "idp_auth_tx_id",
  selectedMethod: "idp_selected_method",
  challenge: "idp_auth_challenge",
  oauthRequestId: "idp_oauth_request_id"
};

function readString(key, fallback = "") {
  const raw = localStorage.getItem(key);
  return raw !== null ? raw : fallback;
}

function readJson(key, fallback = null) {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return fallback;
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    return fallback;
  }
}

function writeString(key, value) {
  if (value === null || value === undefined || value === "") {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, String(value));
}

function writeJson(key, value) {
  if (value === null || value === undefined) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, JSON.stringify(value));
}

export function getState() {
  const accessToken = readString(storageKeys.accessToken, "");
  const refreshToken = readString(storageKeys.refreshToken, "");
  const userEmail = readString(storageKeys.userEmail, "");
  const authTxId = readString(storageKeys.authTxId, "");
  const selectedMethod = readString(storageKeys.selectedMethod, "");
  const challenge = readJson(storageKeys.challenge, null);
  const oauthRequestId = readString(storageKeys.oauthRequestId, "");

  let authStage = readString(storageKeys.authStage, "");
  if (!authStage) {
    authStage = accessToken ? "authenticated" : "anonymous";
  }
  if (accessToken && authStage === "otp_pending") {
    authStage = "authenticated";
  }

  return {
    accessToken,
    refreshToken,
    userEmail,
    authStage,
    authTxId,
    selectedMethod,
    challenge,
    oauthRequestId
  };
}

export function setState(patch) {
  const current = getState();
  const next = { ...current, ...patch };

  writeString(storageKeys.accessToken, next.accessToken);
  writeString(storageKeys.refreshToken, next.refreshToken);
  writeString(storageKeys.userEmail, next.userEmail);
  writeString(storageKeys.authStage, next.authStage);
  writeString(storageKeys.authTxId, next.authTxId);
  writeString(storageKeys.selectedMethod, next.selectedMethod);
  writeJson(storageKeys.challenge, next.challenge);
  writeString(storageKeys.oauthRequestId, next.oauthRequestId);

  return next;
}

export function setTokens(accessToken, refreshToken) {
  const hasToken = Boolean(accessToken);
  return setState({
    accessToken: accessToken || "",
    refreshToken: refreshToken || "",
    authStage: hasToken ? "authenticated" : "anonymous"
  });
}

export function setAuthTransaction({ authTxId, selectedMethod, challenge, userEmail }) {
  return setState({
    authTxId: authTxId || "",
    selectedMethod: selectedMethod || "",
    challenge: challenge || null,
    userEmail: userEmail || "",
    authStage: "otp_pending"
  });
}

export function clearAuthTransaction() {
  return setState({
    authTxId: "",
    selectedMethod: "",
    challenge: null
  });
}

export function setUserEmail(email) {
  return setState({ userEmail: email || "" });
}

export function markOtpPending() {
  return setState({ authStage: "otp_pending" });
}

export function markAuthenticated() {
  return setState({ authStage: "authenticated" });
}

export function clearAuth() {
  return setState({
    accessToken: "",
    refreshToken: "",
    authStage: "anonymous",
    authTxId: "",
    selectedMethod: "",
    challenge: null
  });
}

export function isAuthenticated() {
  const state = getState();
  return Boolean(state.accessToken) && state.authStage === "authenticated";
}

export function hasPendingOtp() {
  const state = getState();
  return state.authStage === "otp_pending" && Boolean(state.authTxId);
}

export function getAccessToken() {
  return getState().accessToken;
}

export function setOauthRequestId(id) {
  return setState({ oauthRequestId: id || "" });
}

export function clearOauthRequestId() {
  return setState({ oauthRequestId: "" });
}

