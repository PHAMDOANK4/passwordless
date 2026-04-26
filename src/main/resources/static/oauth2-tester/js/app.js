(() => {
  const el = {
    baseUrl: byId("baseUrl"),
    userBearer: byId("userBearer"),
    redirectUri: byId("redirectUri"),

    authClientId: byId("authClientId"),
    authScope: byId("authScope"),
    authState: byId("authState"),
    authNonce: byId("authNonce"),
    pkceVerifier: byId("pkceVerifier"),
    pkceChallenge: byId("pkceChallenge"),
    authorizationCode: byId("authorizationCode"),

    tokenClientId: byId("tokenClientId"),
    tokenClientSecret: byId("tokenClientSecret"),
    accessToken: byId("accessToken"),
    refreshToken: byId("refreshToken"),
    idToken: byId("idToken"),

    svcClientId: byId("svcClientId"),
    svcClientSecret: byId("svcClientSecret"),
    svcScope: byId("svcScope"),

    manageToken: byId("manageToken"),
    tokenHint: byId("tokenHint"),
    manageClientId: byId("manageClientId"),
    manageClientSecret: byId("manageClientSecret"),

    console: byId("console"),

    btnLoadDiscovery: byId("btnLoadDiscovery"),
    btnClearState: byId("btnClearState"),
    btnGenPkce: byId("btnGenPkce"),
    btnAuthorize: byId("btnAuthorize"),
    btnExchange: byId("btnExchange"),
    btnRefresh: byId("btnRefresh"),
    btnClientCredentials: byId("btnClientCredentials"),
    btnUserInfo: byId("btnUserInfo"),
    btnIntrospect: byId("btnIntrospect"),
    btnRevoke: byId("btnRevoke")
  };

  const storageKey = "oauth2_test_lab_state";

  init();

  function init() {
    hydrate();
    wire();
    if (!el.authState.value) {
      el.authState.value = randomBase64Url(18);
    }
    if (!el.authNonce.value) {
      el.authNonce.value = randomBase64Url(18);
    }
  }

  function wire() {
    el.btnLoadDiscovery.addEventListener("click", loadDiscovery);
    el.btnClearState.addEventListener("click", clearState);
    el.btnGenPkce.addEventListener("click", generatePkce);
    el.btnAuthorize.addEventListener("click", authorize);
    el.btnExchange.addEventListener("click", exchangeCode);
    el.btnRefresh.addEventListener("click", refreshToken);
    el.btnClientCredentials.addEventListener("click", clientCredentials);
    el.btnUserInfo.addEventListener("click", userInfo);
    el.btnIntrospect.addEventListener("click", introspect);
    el.btnRevoke.addEventListener("click", revoke);

    Object.values(el).forEach((node) => {
      if (!node || !(node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement || node instanceof HTMLSelectElement)) {
        return;
      }
      node.addEventListener("input", persist);
      node.addEventListener("change", persist);
    });
  }

  async function loadDiscovery() {
    try {
      const data = await request("GET", "/.well-known/openid-configuration");
      log("DISCOVERY", data);

      if (Array.isArray(data.scopes_supported) && data.scopes_supported.length && !el.authScope.value) {
        el.authScope.value = data.scopes_supported.join(" ");
      }
      persist();
    } catch (error) {
      logError("DISCOVERY_ERROR", error);
    }
  }

  function generatePkce() {
    const verifier = randomBase64Url(64);
    el.pkceVerifier.value = verifier;
    sha256(verifier)
      .then((hash) => {
        el.pkceChallenge.value = base64UrlFromBytes(new Uint8Array(hash));
        el.authState.value = randomBase64Url(18);
        el.authNonce.value = randomBase64Url(18);
        persist();
        log("PKCE", {
          verifier: el.pkceVerifier.value,
          challenge: el.pkceChallenge.value,
          state: el.authState.value,
          nonce: el.authNonce.value
        });
      })
      .catch((error) => logError("PKCE_ERROR", error));
  }

  async function authorize() {
    try {
      const payload = {
        responseType: "code",
        clientId: text(el.authClientId),
        redirectUri: text(el.redirectUri),
        scope: text(el.authScope),
        state: text(el.authState),
        codeChallenge: text(el.pkceChallenge),
        codeChallengeMethod: "S256",
        nonce: text(el.authNonce)
      };

      const data = await request("POST", "/oauth2/authorize", {
        bearer: bearerValue(el.userBearer.value),
        json: payload
      });

      if (data.code) {
        el.authorizationCode.value = data.code;
      }
      log("AUTHORIZE", data);
      persist();
    } catch (error) {
      logError("AUTHORIZE_ERROR", error);
    }
  }

  async function exchangeCode() {
    try {
      const form = new URLSearchParams();
      form.set("grant_type", "authorization_code");
      form.set("client_id", text(el.tokenClientId));
      form.set("redirect_uri", text(el.redirectUri));
      form.set("code", text(el.authorizationCode));
      form.set("code_verifier", text(el.pkceVerifier));
      if (text(el.tokenClientSecret)) {
        form.set("client_secret", text(el.tokenClientSecret));
      }

      const data = await request("POST", "/oauth2/token", { form });
      fillTokens(data);
      log("TOKEN_EXCHANGE", data);
      persist();
    } catch (error) {
      logError("TOKEN_EXCHANGE_ERROR", error);
    }
  }

  async function refreshToken() {
    try {
      const form = new URLSearchParams();
      form.set("grant_type", "refresh_token");
      form.set("client_id", text(el.tokenClientId));
      form.set("refresh_token", text(el.refreshToken));
      if (text(el.tokenClientSecret)) {
        form.set("client_secret", text(el.tokenClientSecret));
      }

      const data = await request("POST", "/oauth2/token", { form });
      fillTokens(data);
      log("TOKEN_REFRESH", data);
      persist();
    } catch (error) {
      logError("TOKEN_REFRESH_ERROR", error);
    }
  }

  async function clientCredentials() {
    try {
      const form = new URLSearchParams();
      form.set("grant_type", "client_credentials");
      form.set("client_id", text(el.svcClientId));
      form.set("client_secret", text(el.svcClientSecret));
      if (text(el.svcScope)) {
        form.set("scope", text(el.svcScope));
      }

      const data = await request("POST", "/oauth2/token", { form });
      if (data.access_token) {
        el.accessToken.value = data.access_token;
        el.manageToken.value = data.access_token;
      }
      log("CLIENT_CREDENTIALS", data);
      persist();
    } catch (error) {
      logError("CLIENT_CREDENTIALS_ERROR", error);
    }
  }

  async function userInfo() {
    try {
      const token = text(el.accessToken) || text(el.manageToken);
      const data = await request("GET", "/oauth2/userinfo", {
        bearer: token
      });
      log("USERINFO", data);
    } catch (error) {
      logError("USERINFO_ERROR", error);
    }
  }

  async function introspect() {
    try {
      const form = new URLSearchParams();
      form.set("token", text(el.manageToken));
      if (text(el.tokenHint)) {
        form.set("token_type_hint", text(el.tokenHint));
      }
      form.set("client_id", text(el.manageClientId));
      if (text(el.manageClientSecret)) {
        form.set("client_secret", text(el.manageClientSecret));
      }

      const data = await request("POST", "/oauth2/introspect", { form });
      log("INTROSPECT", data);
    } catch (error) {
      logError("INTROSPECT_ERROR", error);
    }
  }

  async function revoke() {
    try {
      const form = new URLSearchParams();
      form.set("token", text(el.manageToken));
      if (text(el.tokenHint)) {
        form.set("token_type_hint", text(el.tokenHint));
      }
      form.set("client_id", text(el.manageClientId));
      if (text(el.manageClientSecret)) {
        form.set("client_secret", text(el.manageClientSecret));
      }

      await request("POST", "/oauth2/revoke", { form, expectEmpty: true });
      log("REVOKE", { status: "ok" });
    } catch (error) {
      logError("REVOKE_ERROR", error);
    }
  }

  async function request(method, path, options = {}) {
    const url = normalizeBaseUrl(el.baseUrl.value) + path;
    const headers = {};
    let body;

    if (options.json) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(options.json);
    }

    if (options.form) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      body = options.form.toString();
    }

    if (options.bearer) {
      headers.Authorization = bearerValue(options.bearer);
    }

    const res = await fetch(url, {
      method,
      headers,
      body
    });

    if (options.expectEmpty && res.status === 200) {
      return {};
    }

    const textBody = await res.text();
    let json;
    try {
      json = textBody ? JSON.parse(textBody) : {};
    } catch (e) {
      json = { raw: textBody };
    }

    if (!res.ok) {
      const message = json.error || json.message || JSON.stringify(json) || ("HTTP " + res.status);
      throw new Error(message);
    }

    return json;
  }

  function fillTokens(data) {
    el.accessToken.value = data.access_token || "";
    el.refreshToken.value = data.refresh_token || "";
    el.idToken.value = data.id_token || "";
    if (data.access_token) {
      el.manageToken.value = data.access_token;
    }
  }

  function clearState() {
    localStorage.removeItem(storageKey);
    window.location.reload();
  }

  function persist() {
    const state = {
      baseUrl: el.baseUrl.value,
      userBearer: el.userBearer.value,
      redirectUri: el.redirectUri.value,
      authClientId: el.authClientId.value,
      authScope: el.authScope.value,
      authState: el.authState.value,
      authNonce: el.authNonce.value,
      pkceVerifier: el.pkceVerifier.value,
      pkceChallenge: el.pkceChallenge.value,
      authorizationCode: el.authorizationCode.value,
      tokenClientId: el.tokenClientId.value,
      tokenClientSecret: el.tokenClientSecret.value,
      accessToken: el.accessToken.value,
      refreshToken: el.refreshToken.value,
      idToken: el.idToken.value,
      svcClientId: el.svcClientId.value,
      svcClientSecret: el.svcClientSecret.value,
      svcScope: el.svcScope.value,
      manageToken: el.manageToken.value,
      tokenHint: el.tokenHint.value,
      manageClientId: el.manageClientId.value,
      manageClientSecret: el.manageClientSecret.value
    };
    localStorage.setItem(storageKey, JSON.stringify(state));
  }

  function hydrate() {
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return;
    }

    try {
      const state = JSON.parse(raw);
      Object.entries(state).forEach(([key, value]) => {
        if (el[key] && typeof value === "string") {
          el[key].value = value;
        }
      });
    } catch (_error) {
      localStorage.removeItem(storageKey);
    }
  }

  function log(tag, data) {
    el.console.textContent = "[" + new Date().toISOString() + "] " + tag + "\n" + JSON.stringify(data, null, 2);
  }

  function logError(tag, error) {
    el.console.textContent = "[" + new Date().toISOString() + "] " + tag + "\n" + String(error.message || error);
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function normalizeBaseUrl(value) {
    return value.replace(/\/$/, "");
  }

  function text(node) {
    return node.value.trim();
  }

  function bearerValue(value) {
    const clean = String(value || "").trim();
    if (!clean) {
      return "";
    }
    return clean.startsWith("Bearer ") ? clean : "Bearer " + clean;
  }

  function randomBase64Url(length) {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return base64UrlFromBytes(bytes);
  }

  function base64UrlFromBytes(bytes) {
    let binary = "";
    for (let i = 0; i < bytes.length; i += 1) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  async function sha256(value) {
    return crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  }
})();
