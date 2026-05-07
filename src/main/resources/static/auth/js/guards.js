import { ROUTES, goTo } from "./routes.js";
import { getState, hasPendingOtp, isAuthenticated } from "./store.js";

export function requireUnauthenticated() {
  const params = new URLSearchParams(window.location.search);
  const oauthReqId = params.get("oauth_request_id") || getState().oauthRequestId;

  if (isAuthenticated()) {
    if (oauthReqId) {
      window.location.href = `/oauth2/authorize/callback?oauth_request_id=${encodeURIComponent(oauthReqId)}`;
      return false;
    }
    goTo(ROUTES.setupAuthMethods);
    return false;
  }

  if (hasPendingOtp()) {
    goTo(ROUTES.verifyOtp);
    return false;
  }

  return true;
}

export function requirePendingOtp() {
  if (isAuthenticated()) {
    goTo(ROUTES.setupAuthMethods);
    return false;
  }

  if (!hasPendingOtp()) {
    goTo(ROUTES.login);
    return false;
  }

  return true;
}

export function requireAuthenticated() {
  const state = getState();

  if (state.authStage === "otp_pending") {
    goTo(ROUTES.verifyOtp);
    return false;
  }

  if (!isAuthenticated()) {
    goTo(ROUTES.login);
    return false;
  }

  return true;
}
