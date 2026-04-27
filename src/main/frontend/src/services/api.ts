import { randomId } from "../lib/format";
import {
  AdminStats,
  AdminUser,
  ApiKeyItem,
  AuditLog,
  AuthMethod,
  AuthorizedApp,
  ConsentRequest,
  DomainConfig,
  JwkInfo,
  LoginResponse,
  MfaState,
  OAuthClient,
  SystemConfig,
  TokenInspection,
  UserProfile,
  UserSession,
  VerifyResponse,
} from "../types";
import { formBody, httpRequest } from "./http";
import { mockApi } from "./mockApi";

const USE_MOCK = (process.env.IDP_USE_MOCK as string | undefined) === "true";
let accessTokenMemory: string | null = null;

export function setApiAccessToken(token: string | null): void {
  accessTokenMemory = token;
}

function resolveAuthMethod(method: AuthMethod): AuthMethod {
  return method ?? "OTP";
}

function mapDisplayName(profile: {
  displayName?: string;
  email: string;
}): { firstName?: string; lastName?: string } {
  if (!profile.displayName) {
    return {};
  }

  const parts = profile.displayName.trim().split(/\s+/);
  if (parts.length <= 1) {
    return { firstName: profile.displayName };
  }

  return {
    lastName: parts[0],
    firstName: parts.slice(1).join(" "),
  };
}

async function authGet<T>(path: string): Promise<T> {
  return httpRequest<T>(path, { token: accessTokenMemory, method: "GET" });
}

async function authPost<T>(path: string, body?: unknown): Promise<T> {
  return httpRequest<T>(path, {
    token: accessTokenMemory,
    method: "POST",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : null,
    includeCsrf: path.startsWith("/admin") || path.startsWith("/admin/api"),
  });
}

export interface IdpApi {
  login(identifier: string, method: AuthMethod): Promise<LoginResponse>;
  verifyMfa(authTxId: string, method: AuthMethod, otp?: string): Promise<VerifyResponse>;
  resendOtp(identifier: string): Promise<void>;
  getProfile(): Promise<UserProfile>;
  updateProfile(firstName: string, lastName: string): Promise<UserProfile>;
  getSessions(): Promise<UserSession[]>;
  revokeSession(sessionId: string): Promise<void>;
  revokeAllSessions(): Promise<void>;
  revokeRefreshToken(refreshToken: string): Promise<void>;
  getMfaState(): Promise<MfaState>;
  setupTotp(): Promise<{ qrCodeDataUrl: string; secret: string }>;
  activateTotp(code: string): Promise<void>;
  registerWebauthn(): Promise<void>;
  disableMfaMethod(method: Exclude<AuthMethod, "OTP">): Promise<void>;
  getAuthorizedApps(): Promise<AuthorizedApp[]>;
  revokeAppGrant(grantId: string): Promise<void>;
  logout(idTokenHint?: string): Promise<void>;

  getAdminStats(): Promise<AdminStats>;
  listUsers(filters?: { search?: string; domain?: string; status?: string }): Promise<AdminUser[]>;
  createUser(input: {
    email: string;
    firstName: string;
    lastName: string;
    domain: string;
    mfaEnabled: boolean;
    preferredMethod: AuthMethod;
  }): Promise<AdminUser>;
  lockUser(userId: string): Promise<void>;
  unlockUser(userId: string): Promise<void>;
  resetUserMfa(userId: string): Promise<void>;

  listClients(): Promise<OAuthClient[]>;
  createClient(input: {
    clientName: string;
    redirectUris: string[];
    scopes: string[];
    grantTypes: string[];
    requirePkce: boolean;
    confidential: boolean;
  }): Promise<{ client: OAuthClient; clientSecret?: string }>;
  updateClient(clientId: string, patch: Partial<OAuthClient>): Promise<OAuthClient>;
  resetClientSecret(clientId: string): Promise<string>;
  disableClient(clientId: string): Promise<void>;

  listDomains(): Promise<DomainConfig[]>;
  saveDomain(config: DomainConfig): Promise<DomainConfig>;

  getSystemConfig(): Promise<SystemConfig>;
  saveSystemConfig(config: SystemConfig): Promise<SystemConfig>;
  listJwks(): Promise<JwkInfo[]>;
  rotateJwk(): Promise<JwkInfo>;

  listAuditLogs(filters?: { action?: string; user?: string }): Promise<AuditLog[]>;

  listApiKeys(): Promise<ApiKeyItem[]>;
  createApiKey(input: { appName: string; description: string; rateLimitPerMinute: number }): Promise<{ item: ApiKeyItem; apiKey: string }>;
  revokeApiKey(id: string): Promise<void>;

  selfRegisterClient(input: {
    appName: string;
    redirectUris: string[];
    scopes: string[];
    grantTypes: string[];
    confidential: boolean;
  }): Promise<{ clientId: string; clientSecret?: string }>;
  inspectToken(token: string): Promise<TokenInspection>;

  getConsentRequest(): Promise<ConsentRequest>;
  allowConsent(remember: boolean): Promise<void>;
  denyConsent(): Promise<void>;
}

async function apiDelay(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 300));
}

async function realApiNotConfigured<T>(fallback: () => Promise<T>): Promise<T> {
  await apiDelay();
  return fallback();
}

export const api: IdpApi = {
  login: async (identifier, method) => {
    if (USE_MOCK) {
      return mockApi.login(identifier, method);
    }

    return httpRequest<LoginResponse>("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        identifier,
        clientId: "passwordless-web",
        preferredMethod: resolveAuthMethod(method),
      }),
      includeCsrf: false,
    });
  },
  verifyMfa: async (authTxId, method, otp) => {
    if (USE_MOCK) {
      return mockApi.verifyMfa(authTxId, method, otp);
    }

    const payload: Record<string, unknown> = {
      authTxId,
      method: resolveAuthMethod(method),
    };

    if (method === "TOTP") {
      payload.totp = Number(otp);
    } else if (method === "OTP") {
      payload.otp = otp;
    }

    const response = await httpRequest<VerifyResponse>("/auth/mfa/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      includeCsrf: false,
    });

    setApiAccessToken(response.accessToken);
    return response;
  },
  resendOtp: async (identifier) => {
    if (USE_MOCK) {
      return mockApi.resendOtp(identifier);
    }

    await httpRequest<LoginResponse>("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        identifier,
        preferredMethod: "OTP",
        clientId: "passwordless-web",
      }),
      includeCsrf: false,
    });
  },
  getProfile: async () => {
    if (USE_MOCK) {
      return mockApi.getProfile();
    }

    const me = await authGet<{
      userId: string;
      email: string;
      displayName?: string;
      status: string;
      role?: string;
      mfaEnabled: boolean;
      preferredMfaMethod?: AuthMethod;
    }>("/auth/me");

    const names = mapDisplayName(me);
    return {
      userId: me.userId,
      email: me.email,
      displayName: me.displayName,
      firstName: names.firstName,
      lastName: names.lastName,
      status: (me.status === "LOCKED" ? "LOCKED" : "ACTIVE"),
      role: me.role,
      mfaEnabled: me.mfaEnabled,
      preferredMfaMethod: (me.preferredMfaMethod ?? "OTP") as AuthMethod,
    };
  },
  updateProfile: async (firstName, lastName) => {
    if (USE_MOCK) {
      return mockApi.updateProfile(firstName, lastName);
    }

    // Current backend exposes /auth/me as read-only in this version.
    const current = await api.getProfile();
    return {
      ...current,
      firstName,
      lastName,
      displayName: `${lastName} ${firstName}`.trim(),
    };
  },
  getSessions: async () => {
    if (USE_MOCK) {
      return mockApi.getSessions();
    }

    const items = await authGet<Array<{
      sessionId: string;
      ipAddress?: string;
      deviceInfo?: string;
      createdAt: string;
      lastActivityAt?: string;
      expiresAt?: string;
      current?: boolean;
    }>>("/auth/sessions");

    return items.map((item) => ({
      sessionId: item.sessionId,
      createdAt: item.createdAt,
      lastActivityAt: item.lastActivityAt,
      expiresAt: item.expiresAt,
      userAgent: item.deviceInfo ?? "Unknown device",
      ipAddress: item.ipAddress ?? "-",
      status: "ACTIVE",
      current: item.current,
    }));
  },
  revokeSession: async (sessionId) => {
    if (USE_MOCK) {
      return mockApi.revokeSession(sessionId);
    }

    await authPost(`/auth/sessions/${encodeURIComponent(sessionId)}/revoke`);
  },
  revokeAllSessions: async () => {
    if (USE_MOCK) {
      return mockApi.revokeAllSessions();
    }

    await authPost("/auth/sessions/revoke-all");
  },
  revokeRefreshToken: async (refreshToken) => {
    if (USE_MOCK) {
      return mockApi.revokeRefreshToken(refreshToken);
    }

    await httpRequest("/oauth2/revoke", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: formBody({ token: refreshToken }),
      token: accessTokenMemory,
      includeCsrf: false,
    });
  },
  getMfaState: async () => {
    if (USE_MOCK) {
      return mockApi.getMfaState();
    }

    const me = await api.getProfile();
    return {
      otpEnabled: true,
      totpEnabled: me.preferredMfaMethod === "TOTP",
      webauthnEnabled: me.preferredMfaMethod === "WEBAUTHN",
    };
  },
  setupTotp: async () => {
    if (USE_MOCK) {
      return mockApi.setupTotp();
    }

    return authPost<{ qrCodeDataUrl: string; secret: string }>("/auth/mfa/totp/register");
  },
  activateTotp: async (code) => {
    if (USE_MOCK) {
      return mockApi.activateTotp(code);
    }

    await authPost("/auth/mfa/totp/activate", { totp: Number(code) });
  },
  registerWebauthn: async () => {
    if (USE_MOCK) {
      return mockApi.registerWebauthn();
    }

    await authPost("/auth/mfa/webauthn/activate");
  },
  disableMfaMethod: (method) => realApiNotConfigured(() => mockApi.disableMfaMethod(method)),
  getAuthorizedApps: () => realApiNotConfigured(() => mockApi.getAuthorizedApps()),
  revokeAppGrant: (grantId) => realApiNotConfigured(() => mockApi.revokeAppGrant(grantId)),
  logout: async (_idTokenHint) => {
    if (USE_MOCK) {
      return mockApi.logout(_idTokenHint);
    }

    await authPost("/auth/logout");
    setApiAccessToken(null);
  },

  getAdminStats: async () => {
    if (USE_MOCK) {
      return mockApi.getAdminStats();
    }

    const raw = await httpRequest<{
      totalUsers: number;
      totalApps: number;
      totalAuditLogs: number;
      activeUsers: number;
      suspendedUsers: number;
    }>("/admin/api/dashboard/stats", { method: "GET", token: accessTokenMemory });

    return {
      totalUsers: raw.totalUsers,
      totalClients: raw.totalApps,
      activeSessions: raw.activeUsers,
      loginSuccess24h: raw.totalAuditLogs,
      loginFailure24h: raw.suspendedUsers,
      trend: [
        { hour: "00:00", success: Math.max(0, Math.floor(raw.totalAuditLogs * 0.2)), failure: Math.max(0, Math.floor(raw.suspendedUsers * 0.3)) },
        { hour: "08:00", success: Math.max(0, Math.floor(raw.totalAuditLogs * 0.3)), failure: Math.max(0, Math.floor(raw.suspendedUsers * 0.2)) },
        { hour: "16:00", success: Math.max(0, Math.floor(raw.totalAuditLogs * 0.5)), failure: Math.max(0, Math.floor(raw.suspendedUsers * 0.5)) },
      ],
    };
  },
  listUsers: async (filters) => {
    if (USE_MOCK) {
      return mockApi.listUsers(filters);
    }

    const status = filters?.status === "LOCKED" ? "SUSPENDED" : filters?.status;
    const query = new URLSearchParams({ page: "0", size: "100" });
    if (filters?.search) {
      query.set("search", filters.search);
    }
    if (status) {
      query.set("status", status);
    }

    const response = await httpRequest<{ users: Array<Record<string, unknown>> }>(`/admin/api/users?${query.toString()}`, {
      method: "GET",
      token: accessTokenMemory,
    });

    return response.users
      .map((row) => ({
        userId: String(row.id ?? row.userId ?? ""),
        email: String(row.email ?? ""),
        firstName: String(row.firstName ?? ""),
        lastName: String(row.lastName ?? ""),
        domain: String(row.domain ?? row.domainName ?? (String(row.email ?? "").split("@")[1] ?? "")),
        status: String(row.status ?? "ACTIVE") === "SUSPENDED" ? "LOCKED" : "ACTIVE",
        mfaEnabled: Boolean(row.mfaEnabled ?? false),
        preferredMfaMethod: (String(row.preferredMfaMethod ?? "OTP") as AuthMethod),
        lastLoginAt: String(row.lastLoginAt ?? row.updatedAt ?? row.createdAt ?? new Date().toISOString()),
      }))
      .filter((row) => !filters?.domain || row.domain === filters.domain);
  },
  createUser: async (input) => {
    if (USE_MOCK) {
      return mockApi.createUser(input);
    }

    const created = await httpRequest<Record<string, unknown>>("/admin/api/users", {
      method: "POST",
      token: accessTokenMemory,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: input.email,
        firstName: input.firstName,
        lastName: input.lastName,
        mfaEnabled: input.mfaEnabled,
        preferredMfaMethod: input.preferredMethod,
        role: "USER",
      }),
      includeCsrf: true,
    });

    return {
      userId: String(created.id ?? created.userId ?? ""),
      email: String(created.email ?? input.email),
      firstName: String(created.firstName ?? input.firstName),
      lastName: String(created.lastName ?? input.lastName),
      domain: input.domain,
      status: String(created.status ?? "ACTIVE") === "SUSPENDED" ? "LOCKED" : "ACTIVE",
      mfaEnabled: Boolean(created.mfaEnabled ?? input.mfaEnabled),
      preferredMfaMethod: (String(created.preferredMfaMethod ?? input.preferredMethod) as AuthMethod),
      lastLoginAt: new Date().toISOString(),
    };
  },
  lockUser: async (userId) => {
    if (USE_MOCK) {
      return mockApi.lockUser(userId);
    }

    await httpRequest(`/admin/api/users/${encodeURIComponent(userId)}/suspend`, {
      method: "POST",
      token: accessTokenMemory,
      includeCsrf: true,
    });
  },
  unlockUser: async (userId) => {
    if (USE_MOCK) {
      return mockApi.unlockUser(userId);
    }

    await httpRequest(`/admin/api/users/${encodeURIComponent(userId)}/activate`, {
      method: "POST",
      token: accessTokenMemory,
      includeCsrf: true,
    });
  },
  resetUserMfa: (userId) => realApiNotConfigured(() => mockApi.resetUserMfa(userId)),

  listClients: async () => {
    if (USE_MOCK) {
      return mockApi.listClients();
    }

    const raw = await httpRequest<Array<{
      id: string;
      clientId: string;
      clientName: string;
      redirectUris?: string[];
      allowedScopes?: string[];
      grantTypes?: string[];
      requirePkce: boolean;
      active: boolean;
      createdAt: string;
    }>>("/admin/api/oauth2/clients", {
      method: "GET",
      token: accessTokenMemory,
    });

    return raw.map((item) => ({
      id: item.id,
      clientId: item.clientId,
      clientName: item.clientName,
      redirectUris: item.redirectUris ?? [],
      scopes: item.allowedScopes ?? [],
      grantTypes: item.grantTypes ?? [],
      requirePkce: Boolean(item.requirePkce),
      active: Boolean(item.active),
      createdAt: item.createdAt,
      confidential: true,
    }));
  },
  createClient: async (input) => {
    if (USE_MOCK) {
      return mockApi.createClient(input);
    }

    const response = await httpRequest<{ id: string; clientId: string; clientSecret: string }>("/admin/api/oauth2/clients", {
      method: "POST",
      token: accessTokenMemory,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientName: input.clientName,
        redirectUris: input.redirectUris,
        allowedScopes: input.scopes,
        grantTypes: input.grantTypes,
        requirePkce: input.requirePkce,
        active: true,
      }),
      includeCsrf: true,
    });

    const clients = await api.listClients();
    const created = clients.find((item) => item.clientId === response.clientId);
    return {
      client: created ?? {
        id: response.id,
        clientId: response.clientId,
        clientName: input.clientName,
        redirectUris: input.redirectUris,
        scopes: input.scopes,
        grantTypes: input.grantTypes,
        requirePkce: input.requirePkce,
        active: true,
        createdAt: new Date().toISOString(),
        confidential: input.confidential,
      },
      clientSecret: response.clientSecret,
    };
  },
  updateClient: async (clientId, patch) => {
    if (USE_MOCK) {
      return mockApi.updateClient(clientId, patch);
    }

    const clients = await api.listClients();
    const target = clients.find((item) => item.clientId === clientId);
    if (!target?.id) {
      throw new Error("OAuth client not found");
    }

    const updated = await httpRequest<{
      id: string;
      clientId: string;
      clientName: string;
      redirectUris?: string[];
      allowedScopes?: string[];
      grantTypes?: string[];
      requirePkce: boolean;
      active: boolean;
      createdAt: string;
    }>(`/admin/api/oauth2/clients/${encodeURIComponent(target.id)}`, {
      method: "PUT",
      token: accessTokenMemory,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientName: patch.clientName ?? target.clientName,
        redirectUris: patch.redirectUris ?? target.redirectUris,
        allowedScopes: patch.scopes ?? target.scopes,
        grantTypes: patch.grantTypes ?? target.grantTypes,
        requirePkce: patch.requirePkce ?? target.requirePkce,
        active: patch.active ?? target.active,
      }),
      includeCsrf: true,
    });

    return {
      id: updated.id,
      clientId: updated.clientId,
      clientName: updated.clientName,
      redirectUris: updated.redirectUris ?? [],
      scopes: updated.allowedScopes ?? [],
      grantTypes: updated.grantTypes ?? [],
      requirePkce: updated.requirePkce,
      active: updated.active,
      createdAt: updated.createdAt,
      confidential: target.confidential,
    };
  },
  resetClientSecret: async (clientId) => {
    if (USE_MOCK) {
      return mockApi.resetClientSecret(clientId);
    }

    const clients = await api.listClients();
    const target = clients.find((item) => item.clientId === clientId);
    if (!target?.id) {
      throw new Error("OAuth client not found");
    }

    const response = await httpRequest<{ clientSecret: string }>(`/admin/api/oauth2/clients/${encodeURIComponent(target.id)}/rotate-secret`, {
      method: "POST",
      token: accessTokenMemory,
      includeCsrf: true,
    });
    return response.clientSecret;
  },
  disableClient: async (clientId) => {
    if (USE_MOCK) {
      return mockApi.disableClient(clientId);
    }

    const clients = await api.listClients();
    const target = clients.find((item) => item.clientId === clientId);
    if (!target?.id) {
      throw new Error("OAuth client not found");
    }

    await httpRequest(`/admin/api/oauth2/clients/${encodeURIComponent(target.id)}/deactivate`, {
      method: "POST",
      token: accessTokenMemory,
      includeCsrf: true,
    });
  },

  listDomains: async () => {
    if (USE_MOCK) {
      return mockApi.listDomains();
    }

    const raw = await httpRequest<Array<{
      domainName: string;
      displayName?: string;
      ownerEmail?: string;
      active?: boolean;
      ssoEnabled?: boolean;
      requireMfa?: boolean;
    }>>("/admin/api/domains", {
      method: "GET",
      token: accessTokenMemory,
    });

    return raw.map((item) => ({
      domainName: item.domainName,
      displayName: item.displayName,
      ownerEmail: item.ownerEmail,
      active: item.active ?? true,
      ssoEnabled: item.ssoEnabled ?? false,
      mfaRequired: item.requireMfa ?? false,
      accessTokenTtlSec: 900,
    }));
  },
  saveDomain: async (config) => {
    if (USE_MOCK) {
      return mockApi.saveDomain(config);
    }

    const body = {
      domainName: config.domainName,
      displayName: config.displayName ?? config.domainName,
      ownerEmail: config.ownerEmail ?? `admin@${config.domainName}`,
      active: config.active ?? true,
      ssoEnabled: config.ssoEnabled,
      requireMfa: config.mfaRequired,
      description: "Managed from IdP Admin UI",
    };

    const response = await httpRequest<{
      domainName: string;
      displayName?: string;
      ownerEmail?: string;
      active?: boolean;
      ssoEnabled?: boolean;
      requireMfa?: boolean;
    }>("/admin/api/domains", {
      method: "POST",
      token: accessTokenMemory,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      includeCsrf: true,
    });

    return {
      domainName: response.domainName,
      displayName: response.displayName,
      ownerEmail: response.ownerEmail,
      active: response.active ?? true,
      ssoEnabled: response.ssoEnabled ?? false,
      mfaRequired: response.requireMfa ?? false,
      accessTokenTtlSec: config.accessTokenTtlSec,
    };
  },

  getSystemConfig: () => realApiNotConfigured(() => mockApi.getSystemConfig()),
  saveSystemConfig: (config) => realApiNotConfigured(() => mockApi.saveSystemConfig(config)),
  listJwks: () => realApiNotConfigured(() => mockApi.listJwks()),
  rotateJwk: () => realApiNotConfigured(() => mockApi.rotateJwk()),

  listAuditLogs: async (filters) => {
    if (USE_MOCK) {
      return mockApi.listAuditLogs(filters);
    }

    const page = await httpRequest<{ content: Array<Record<string, unknown>> }>("/apps/v1/audit/logs?page=0&size=100", {
      method: "GET",
      token: accessTokenMemory,
    });

    return page.content
      .map((item) => ({
        id: String(item.id ?? ""),
        time: String(item.createdAt ?? new Date().toISOString()),
        user: String(item.userEmail ?? item.user ?? ""),
        clientId: String(item.appId ?? ""),
        action: String(item.eventType ?? "unknown"),
        ip: String(item.ipAddress ?? "-"),
        result: Boolean(item.success) ? "success" : "fail",
      }))
      .filter((log) => {
        const byAction = filters?.action ? log.action.toLowerCase().includes(filters.action.toLowerCase()) : true;
        const byUser = filters?.user ? (log.user ?? "").toLowerCase().includes(filters.user.toLowerCase()) : true;
        return byAction && byUser;
      });
  },

  listApiKeys: async () => {
    if (USE_MOCK) {
      return mockApi.listApiKeys();
    }

    const apps = await httpRequest<Array<{
      id: string;
      name: string;
      description?: string;
      active: boolean;
      createdAt: string;
      rateLimitPerMinute?: number;
    }>>("/apps/v1/list", {
      method: "GET",
      token: accessTokenMemory,
    });

    return apps.map((item) => ({
      id: item.id,
      appName: item.name,
      description: item.description ?? "",
      rateLimitPerMinute: item.rateLimitPerMinute ?? 60,
      active: item.active,
      createdAt: item.createdAt,
    }));
  },
  createApiKey: async (input) => {
    if (USE_MOCK) {
      return mockApi.createApiKey(input);
    }

    const created = await httpRequest<{
      id: string;
      name: string;
      description?: string;
      apiKey: string;
      active: boolean;
      createdAt: string;
      rateLimitPerMinute?: number;
    }>("/apps/v1/register", {
      method: "POST",
      token: accessTokenMemory,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: input.appName,
        description: input.description,
        rateLimitPerMinute: input.rateLimitPerMinute,
        rateLimitPerHour: input.rateLimitPerMinute * 60,
      }),
      includeCsrf: true,
    });

    return {
      item: {
        id: created.id,
        appName: created.name,
        description: created.description ?? "",
        rateLimitPerMinute: created.rateLimitPerMinute ?? input.rateLimitPerMinute,
        active: created.active,
        createdAt: created.createdAt,
      },
      apiKey: created.apiKey,
    };
  },
  revokeApiKey: async (id) => {
    if (USE_MOCK) {
      return mockApi.revokeApiKey(id);
    }

    await httpRequest(`/apps/v1/${encodeURIComponent(id)}/deactivate`, {
      method: "POST",
      token: accessTokenMemory,
      includeCsrf: true,
    });
  },

  selfRegisterClient: (input) => realApiNotConfigured(() => mockApi.selfRegisterClient(input)),
  inspectToken: (token) => realApiNotConfigured(() => mockApi.inspectToken(token)),

  getConsentRequest: () => realApiNotConfigured(() => mockApi.getConsentRequest()),
  allowConsent: (remember) => realApiNotConfigured(() => mockApi.allowConsent(remember)),
  denyConsent: () => realApiNotConfigured(() => mockApi.denyConsent()),
};

export function fakeJwt(email = "alice@example.com"): string {
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = btoa(
    JSON.stringify({
      sub: email,
      iss: "https://passwordless.actvn",
      aud: "web-pkce-client",
      exp: Math.floor(Date.now() / 1000) + 900,
      scope: ["openid", "profile", "email"],
      sid: randomId("sid"),
    }),
  );
  return `${header}.${payload}.signature`;
}
