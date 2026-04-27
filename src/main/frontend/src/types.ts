export type AuthMethod = "OTP" | "TOTP" | "WEBAUTHN";

export type SessionStatus = "ACTIVE" | "REVOKED";

export type UserStatus = "ACTIVE" | "LOCKED";

export interface UserProfile {
  userId: string;
  email: string;
  firstName?: string;
  lastName?: string;
  displayName?: string;
  domain?: string;
  role?: string;
  status: UserStatus;
  mfaEnabled: boolean;
  preferredMfaMethod: AuthMethod;
}

export interface UserSession {
  sessionId: string;
  createdAt: string;
  lastActivityAt?: string;
  expiresAt?: string;
  userAgent: string;
  ipAddress: string;
  status: SessionStatus;
  current?: boolean;
}

export interface MfaState {
  otpEnabled: boolean;
  totpEnabled: boolean;
  webauthnEnabled: boolean;
}

export interface AuthorizedApp {
  grantId: string;
  clientName: string;
  scopes: string[];
  grantedAt: string;
}

export interface LoginResponse {
  authTxId: string;
  nextStep: "VERIFY";
  selectedMethod: AuthMethod;
  expiresInSeconds: number;
  challenge: {
    destination?: string;
    remainingAttempts: number;
    resendAllowedAt: number;
  };
}

export interface VerifyResponse {
  authenticated: boolean;
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  sessionId: string;
}

export interface AdminStats {
  totalUsers: number;
  totalClients: number;
  activeSessions: number;
  loginSuccess24h: number;
  loginFailure24h: number;
  trend: Array<{ hour: string; success: number; failure: number }>;
}

export interface AdminUser extends UserProfile {
  lastLoginAt: string;
}

export interface OAuthClient {
  id?: string;
  clientId: string;
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  grantTypes: string[];
  requirePkce: boolean;
  active: boolean;
  createdAt: string;
  confidential: boolean;
}

export interface DomainConfig {
  domainName: string;
  displayName?: string;
  ownerEmail?: string;
  active?: boolean;
  ssoEnabled: boolean;
  mfaRequired: boolean;
  accessTokenTtlSec: number;
}

export interface SystemConfig {
  accessTokenTtlSec: number;
  refreshTokenTtlSec: number;
  authorizationCodeTtlSec: number;
  refreshRotationEnabled: boolean;
  grantTypes: string[];
  rateLimits: Array<{ endpoint: string; perMinute: number; by: "IP" | "CLIENT" }>;
}

export interface JwkInfo {
  kid: string;
  algorithm: string;
  active: boolean;
}

export interface AuditLog {
  id: string;
  time: string;
  user?: string;
  clientId?: string;
  action: string;
  ip: string;
  result: "success" | "fail";
}

export interface ApiKeyItem {
  id: string;
  appName: string;
  description: string;
  rateLimitPerMinute: number;
  active: boolean;
  createdAt: string;
}

export interface ConsentRequest {
  clientName: string;
  scopes: string[];
  rememberDecision: boolean;
}

export interface TokenInspection {
  active: boolean;
  claims: Record<string, string | number | boolean | string[]>;
}

export interface ToastMessage {
  id: string;
  type: "success" | "error" | "info";
  message: string;
}
