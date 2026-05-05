import { clearAuth, getAccessToken } from "./store.js";

export async function api(path, options = {}) {
  const {
    method = "GET",
    body,
    auth = false,
    asForm = false,
    credentials = "same-origin"
  } = options;

  const headers = {};

  if (auth) {
    const token = getAccessToken();
    if (!token) {
      throw new Error("Authentication required. Login first.");
    }
    headers.Authorization = `Bearer ${token}`;
  }

  let payload;
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
    if (response.status === 401) {
      clearAuth();
    }
    const message = typeof responseBody === "object"
      ? responseBody.error || JSON.stringify(responseBody)
      : responseBody || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return responseBody;
}
