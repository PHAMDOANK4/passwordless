import { fakeJwt } from "./api";
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

const profile: UserProfile = {
  userId: "u_1001",
  email: "alice@example.com",
  firstName: "Alice",
  lastName: "Nguyen",
  domain: "example.com",
  status: "ACTIVE",
  mfaEnabled: true,
  preferredMfaMethod: "OTP",
};

let sessions: UserSession[] = [
  {
    sessionId: "sess_1",
    createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    userAgent: "Chrome 124 / Linux",
    ipAddress: "10.10.1.25",
    status: "ACTIVE",
  },
  {
    sessionId: "sess_2",
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    userAgent: "Safari / iOS",
    ipAddress: "10.10.1.41",
    status: "ACTIVE",
  },
];

let mfaState: MfaState = {
  otpEnabled: true,
  totpEnabled: false,
  webauthnEnabled: true,
};

let authorizedApps: AuthorizedApp[] = [
  {
    grantId: "grant_1",
    clientName: "Knowledge Portal",
    scopes: ["openid", "profile", "email"],
    grantedAt: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
  },
  {
    grantId: "grant_2",
    clientName: "Procurement App",
    scopes: ["openid", "profile"],
    grantedAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
  },
];

let adminUsers: AdminUser[] = [
  {
    ...profile,
    lastLoginAt: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
  },
  {
    userId: "u_1002",
    email: "bob@example.com",
    firstName: "Bob",
    lastName: "Tran",
    domain: "example.com",
    status: "LOCKED",
    mfaEnabled: true,
    preferredMfaMethod: "TOTP",
    lastLoginAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
  },
  {
    userId: "u_1003",
    email: "linh@vendor.com",
    firstName: "Linh",
    lastName: "Pham",
    domain: "vendor.com",
    status: "ACTIVE",
    mfaEnabled: false,
    preferredMfaMethod: "OTP",
    lastLoginAt: new Date(Date.now() - 1000 * 60 * 60 * 7).toISOString(),
  },
];

let clients: OAuthClient[] = [
  {
    clientId: "web-pkce-client",
    clientName: "Core Web App",
    redirectUris: ["https://app.example.com/callback"],
    scopes: ["openid", "profile", "email"],
    grantTypes: ["authorization_code", "refresh_token"],
    requirePkce: true,
    active: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 20).toISOString(),
    confidential: false,
  },
  {
    clientId: "svc-reporting",
    clientName: "Reporting Service",
    redirectUris: [],
    scopes: ["api.read"],
    grantTypes: ["client_credentials"],
    requirePkce: false,
    active: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 10).toISOString(),
    confidential: true,
  },
];

let domains: DomainConfig[] = [
  {
    domainName: "example.com",
    ssoEnabled: true,
    mfaRequired: true,
    accessTokenTtlSec: 900,
  },
  {
    domainName: "vendor.com",
    ssoEnabled: true,
    mfaRequired: false,
    accessTokenTtlSec: 1200,
  },
];

let systemConfig: SystemConfig = {
  accessTokenTtlSec: 900,
  refreshTokenTtlSec: 2592000,
  authorizationCodeTtlSec: 180,
  refreshRotationEnabled: true,
  grantTypes: ["authorization_code", "refresh_token", "client_credentials"],
  rateLimits: [
    { endpoint: "/oauth2/token", perMinute: 60, by: "CLIENT" },
    { endpoint: "/auth/login", perMinute: 30, by: "IP" },
    { endpoint: "/auth/mfa/verify", perMinute: 20, by: "IP" },
  ],
};

let jwks: JwkInfo[] = [
  { kid: "kid-2026-01", algorithm: "RS256", active: false },
  { kid: "kid-2026-04", algorithm: "RS256", active: true },
];

let auditLogs: AuditLog[] = [
  {
    id: randomId("audit"),
    time: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    user: "alice@example.com",
    clientId: "web-pkce-client",
    action: "issue_token",
    ip: "10.10.1.25",
    result: "success",
  },
  {
    id: randomId("audit"),
    time: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
    user: "bob@example.com",
    action: "mfa_verify",
    ip: "10.10.1.27",
    result: "fail",
  },
  {
    id: randomId("audit"),
    time: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    user: "admin@example.com",
    action: "admin_lock_user",
    ip: "10.10.1.2",
    result: "success",
  },
];

let apiKeys: ApiKeyItem[] = [
  {
    id: "key_1",
    appName: "OTP Gateway",
    description: "SMS OTP bridge",
    rateLimitPerMinute: 120,
    active: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 3).toISOString(),
  },
];

const consentRequest: ConsentRequest = {
  clientName: "Partner Procurement Portal",
  scopes: ["openid", "profile", "email"],
  rememberDecision: false,
};

function assertOtp(otp?: string): void {
  if (otp !== "123456") {
    throw new Error("MFA code is invalid. Please try again.");
  }
}

export const mockApi = {
  async login(identifier: string, method: AuthMethod): Promise<LoginResponse> {
    if (!identifier.includes("@")) {
      throw new Error("Định dạng email không hợp lệ.");
    }

    if (identifier !== profile.email) {
      throw new Error("Người dùng không tồn tại hoặc chưa được kích hoạt.");
    }

    return {
      authTxId: randomId("tx"),
      nextStep: "VERIFY",
      selectedMethod: method,
      expiresInSeconds: 300,
      challenge: {
        destination: method === "OTP" ? profile.email : undefined,
        remainingAttempts: 5,
        resendAllowedAt: Date.now() + 30000,
      },
    };
  },

  async verifyMfa(authTxId: string, method: AuthMethod, otp?: string): Promise<VerifyResponse> {
    if (!authTxId) {
      throw new Error("authTxId is required");
    }

    if (method !== "WEBAUTHN") {
      assertOtp(otp);
    }

    return {
      authenticated: true,
      accessToken: fakeJwt(profile.email),
      refreshToken: randomId("refresh"),
      tokenType: "Bearer",
      expiresIn: 900,
      sessionId: sessions[0]?.sessionId ?? randomId("sess"),
    };
  },

  async resendOtp(_authTxId: string): Promise<void> {
    return;
  },

  async getProfile(): Promise<UserProfile> {
    return profile;
  },

  async updateProfile(firstName: string, lastName: string): Promise<UserProfile> {
    profile.firstName = firstName;
    profile.lastName = lastName;
    return profile;
  },

  async getSessions(): Promise<UserSession[]> {
    return sessions;
  },

  async revokeSession(sessionId: string): Promise<void> {
    sessions = sessions.map((s) => (s.sessionId === sessionId ? { ...s, status: "REVOKED" } : s));
  },

  async revokeAllSessions(): Promise<void> {
    sessions = sessions.map((s) => ({ ...s, status: "REVOKED" }));
  },

  async revokeRefreshToken(_refreshToken: string): Promise<void> {
    return;
  },

  async getMfaState(): Promise<MfaState> {
    return mfaState;
  },

  async setupTotp(): Promise<{ qrCodeDataUrl: string; secret: string }> {
    return {
      qrCodeDataUrl:
        "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0nMjAwJyBoZWlnaHQ9JzIwMCcgeG1sbnM9J2h0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnJz48cmVjdCB3aWR0aD0nMjAwJyBoZWlnaHQ9JzIwMCcgZmlsbD0nI2Y4ZjlkYicvPjxyZWN0IHg9JzIwJyB5PScyMCcgd2lkdGg9JzQwJyBoZWlnaHQ9JzQwJyBmaWxsPScjMDAwJy8+PHJlY3QgeD0nMTQwJyB5PScyMCcgd2lkdGg9JzQwJyBoZWlnaHQ9JzQwJyBmaWxsPScjMDAwJy8+PHJlY3QgeD0nMjAnIHk9JzE0MCcgd2lkdGg9JzQwJyBoZWlnaHQ9JzQwJyBmaWxsPScjMDAwJy8+PHRleHQgeD0nMTAwJyB5PScxMDUnIGZvbnQtc2l6ZT0nMTQnIHRleHQtYW5jaG9yPSdtaWRkbGUnIGZpbGw9JyMzMzMnPlRPVFA8L3RleHQ+PC9zdmc+",
      secret: "JBSWY3DPEHPK3PXP",
    };
  },

  async activateTotp(code: string): Promise<void> {
    assertOtp(code);
    mfaState = { ...mfaState, totpEnabled: true };
  },

  async registerWebauthn(): Promise<void> {
    mfaState = { ...mfaState, webauthnEnabled: true };
  },

  async disableMfaMethod(method: Exclude<AuthMethod, "OTP">): Promise<void> {
    if (method === "TOTP") {
      mfaState.totpEnabled = false;
    }

    if (method === "WEBAUTHN") {
      mfaState.webauthnEnabled = false;
    }

    if (!mfaState.totpEnabled && !mfaState.webauthnEnabled && !mfaState.otpEnabled) {
      throw new Error("At least one MFA method must remain active.");
    }
  },

  async getAuthorizedApps(): Promise<AuthorizedApp[]> {
    return authorizedApps;
  },

  async revokeAppGrant(grantId: string): Promise<void> {
    authorizedApps = authorizedApps.filter((a) => a.grantId !== grantId);
  },

  async logout(_idTokenHint?: string): Promise<void> {
    return;
  },

  async getAdminStats(): Promise<AdminStats> {
    return {
      totalUsers: adminUsers.length,
      totalClients: clients.length,
      activeSessions: sessions.filter((s) => s.status === "ACTIVE").length,
      loginSuccess24h: 812,
      loginFailure24h: 36,
      trend: [
        { hour: "00:00", success: 22, failure: 2 },
        { hour: "04:00", success: 35, failure: 1 },
        { hour: "08:00", success: 81, failure: 5 },
        { hour: "12:00", success: 121, failure: 7 },
        { hour: "16:00", success: 104, failure: 4 },
        { hour: "20:00", success: 73, failure: 3 },
      ],
    };
  },

  async listUsers(filters?: { search?: string; domain?: string; status?: string }): Promise<AdminUser[]> {
    return adminUsers.filter((u) => {
      const bySearch = filters?.search
        ? `${u.email} ${u.firstName} ${u.lastName}`.toLowerCase().includes(filters.search.toLowerCase())
        : true;
      const byDomain = filters?.domain ? u.domain === filters.domain : true;
      const byStatus = filters?.status ? u.status === filters.status : true;
      return bySearch && byDomain && byStatus;
    });
  },

  async createUser(input: {
    email: string;
    firstName: string;
    lastName: string;
    domain: string;
    mfaEnabled: boolean;
    preferredMethod: AuthMethod;
  }): Promise<AdminUser> {
    const user: AdminUser = {
      userId: randomId("u"),
      email: input.email,
      firstName: input.firstName,
      lastName: input.lastName,
      domain: input.domain,
      status: "ACTIVE",
      mfaEnabled: input.mfaEnabled,
      preferredMfaMethod: input.preferredMethod,
      lastLoginAt: new Date().toISOString(),
    };
    adminUsers = [user, ...adminUsers];
    return user;
  },

  async lockUser(userId: string): Promise<void> {
    adminUsers = adminUsers.map((u) => (u.userId === userId ? { ...u, status: "LOCKED" } : u));
  },

  async unlockUser(userId: string): Promise<void> {
    adminUsers = adminUsers.map((u) => (u.userId === userId ? { ...u, status: "ACTIVE" } : u));
  },

  async resetUserMfa(_userId: string): Promise<void> {
    return;
  },

  async listClients(): Promise<OAuthClient[]> {
    return clients;
  },

  async createClient(input: {
    clientName: string;
    redirectUris: string[];
    scopes: string[];
    grantTypes: string[];
    requirePkce: boolean;
    confidential: boolean;
  }): Promise<{ client: OAuthClient; clientSecret?: string }> {
    const client: OAuthClient = {
      clientId: randomId("client"),
      clientName: input.clientName,
      redirectUris: input.redirectUris,
      scopes: input.scopes,
      grantTypes: input.grantTypes,
      requirePkce: input.requirePkce,
      active: true,
      createdAt: new Date().toISOString(),
      confidential: input.confidential,
    };

    clients = [client, ...clients];

    return {
      client,
      clientSecret: input.confidential ? randomId("secret") + randomId("x") : undefined,
    };
  },

  async updateClient(clientId: string, patch: Partial<OAuthClient>): Promise<OAuthClient> {
    let updated!: OAuthClient;
    clients = clients.map((c) => {
      if (c.clientId === clientId) {
        updated = { ...c, ...patch };
        return updated;
      }
      return c;
    });
    return updated;
  },

  async resetClientSecret(_clientId: string): Promise<string> {
    return randomId("new_secret") + randomId("v2");
  },

  async disableClient(clientId: string): Promise<void> {
    clients = clients.map((c) => (c.clientId === clientId ? { ...c, active: false } : c));
  },

  async listDomains(): Promise<DomainConfig[]> {
    return domains;
  },

  async saveDomain(config: DomainConfig): Promise<DomainConfig> {
    const exists = domains.some((d) => d.domainName === config.domainName);
    domains = exists
      ? domains.map((d) => (d.domainName === config.domainName ? config : d))
      : [config, ...domains];
    return config;
  },

  async getSystemConfig(): Promise<SystemConfig> {
    return systemConfig;
  },

  async saveSystemConfig(config: SystemConfig): Promise<SystemConfig> {
    systemConfig = config;
    return systemConfig;
  },

  async listJwks(): Promise<JwkInfo[]> {
    return jwks;
  },

  async rotateJwk(): Promise<JwkInfo> {
    jwks = jwks.map((j) => ({ ...j, active: false }));
    const next = { kid: `kid-${new Date().toISOString().slice(0, 10)}`, algorithm: "RS256", active: true };
    jwks = [next, ...jwks];
    return next;
  },

  async listAuditLogs(filters?: { action?: string; user?: string }): Promise<AuditLog[]> {
    return auditLogs.filter((l) => {
      const byAction = filters?.action ? l.action.includes(filters.action) : true;
      const byUser = filters?.user ? (l.user ?? "").includes(filters.user) : true;
      return byAction && byUser;
    });
  },

  async listApiKeys(): Promise<ApiKeyItem[]> {
    return apiKeys;
  },

  async createApiKey(input: { appName: string; description: string; rateLimitPerMinute: number }): Promise<{ item: ApiKeyItem; apiKey: string }> {
    const item: ApiKeyItem = {
      id: randomId("apikey"),
      appName: input.appName,
      description: input.description,
      rateLimitPerMinute: input.rateLimitPerMinute,
      active: true,
      createdAt: new Date().toISOString(),
    };
    apiKeys = [item, ...apiKeys];
    return {
      item,
      apiKey: randomId("idp_live") + randomId("k"),
    };
  },

  async revokeApiKey(id: string): Promise<void> {
    apiKeys = apiKeys.map((k) => (k.id === id ? { ...k, active: false } : k));
  },

  async selfRegisterClient(input: {
    appName: string;
    redirectUris: string[];
    scopes: string[];
    grantTypes: string[];
    confidential: boolean;
  }): Promise<{ clientId: string; clientSecret?: string }> {
    const created = await this.createClient({
      clientName: input.appName,
      redirectUris: input.redirectUris,
      scopes: input.scopes,
      grantTypes: input.grantTypes,
      requirePkce: true,
      confidential: input.confidential,
    });

    return {
      clientId: created.client.clientId,
      clientSecret: created.clientSecret,
    };
  },

  async inspectToken(token: string): Promise<TokenInspection> {
    if (!token || token.split(".").length < 2) {
      return { active: false, claims: {} };
    }

    return {
      active: true,
      claims: {
        sub: profile.email,
        iss: "https://passwordless.actvn",
        aud: "web-pkce-client",
        exp: Math.floor(Date.now() / 1000) + 900,
        scope: ["openid", "profile", "email"],
        sid: "sess_1",
      },
    };
  },

  async getConsentRequest(): Promise<ConsentRequest> {
    return consentRequest;
  },

  async allowConsent(remember: boolean): Promise<void> {
    consentRequest.rememberDecision = remember;
  },

  async denyConsent(): Promise<void> {
    return;
  },
};
