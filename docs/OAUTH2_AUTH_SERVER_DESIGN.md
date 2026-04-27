# OAuth2/OIDC Authorization Server Design (IdP)

## 1. Architecture Overview

This IdP acts as a centralized OAuth2/OIDC Provider for web apps, mobile apps, and backend services.

### Core Components
- Auth UI and login orchestration: passwordless OTP/TOTP/WebAuthn under `/auth/**`
- Authorization Server endpoints: `/oauth2/**` and `/.well-known/**`
- User store: `users`, `domains`
- Client store: `oauth_clients`
- Authorization store: `authorization_codes`
- Token store: `oauth_tokens` and `token_blacklist`
- Session store: `user_sessions`
- Audit trail: `audit_logs`
- Key material: JWKS exposed at `/.well-known/jwks.json`

### Supported OAuth2/OIDC Capabilities
- Authorization Code + PKCE (`S256` only)
- Refresh Token rotation
- Client Credentials
- OIDC discovery + JWKS + userinfo
- Introspection + revocation

## 2. Flow Diagrams

### 2.1 Authorization Code + PKCE
1. Client redirects user in the browser to `GET /oauth2/authorize` with `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`, `code_challenge`, `code_challenge_method=S256`, `nonce`.
2. IdP validates client, redirect URI, scope, PKCE, state/nonce requirements and relies on the existing IdP session cookie when the user is already signed in.
3. IdP issues short-lived authorization code.
4. Client exchanges code at `/oauth2/token` with `grant_type=authorization_code` and `code_verifier`.
5. IdP returns `access_token`, `refresh_token`, `id_token`.

### 2.2 Refresh Token Rotation
1. Client calls `/oauth2/token` with `grant_type=refresh_token`.
2. IdP validates refresh token and client binding.
3. Old refresh token is revoked.
4. New `access_token` + `refresh_token` returned.

### 2.3 Client Credentials
1. Service client calls `/oauth2/token` with `grant_type=client_credentials`, `client_id`, `client_secret`, optional `scope`.
2. IdP validates confidential client and allowed scopes.
3. IdP returns short-lived `access_token` JWT.

### 2.4 Logout and Session Revocation
1. Client or portal calls `POST /oauth2/revoke` to invalidate a refresh token or access token that should no longer be used.
2. Application logout can also call `POST /auth/sessions/{sessionId}/revoke` for a single session or `POST /auth/sessions/revoke-all` for all sessions of the current user.
3. If OIDC RP-initiated logout is enabled, the browser can be redirected to `GET /oauth2/logout?id_token_hint=...&post_logout_redirect_uri=...`.
4. Revoking a token stops token reuse, while ending the IdP session removes the browser sign-in state that powers SSO and future authorization requests.

## 3. Data Model Mapping

- `users`: user identity profile, status, role, MFA preference
- `oauth_clients`: client metadata (`client_id`, hashed `client_secret`, redirect URIs, scopes, grant types, PKCE policy)
- `authorization_codes`: one-time auth codes with PKCE/state/nonce metadata
- `oauth_tokens`: persisted user access/refresh tokens
- `user_sessions`: user session lifecycle and revocation state
- `token_blacklist`: JWT JTI denylist for immediate revocation
- `audit_logs`: event records (auth, API, rate-limit, security)

## 4. Endpoint Contract Summary

### Discovery / Metadata
- `GET /.well-known/openid-configuration`
- `GET /.well-known/jwks.json`

### Authorization / Token
- `GET|POST /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/introspect`
- `POST /oauth2/revoke`
- `GET /oauth2/logout`
- `GET /oauth2/userinfo`

### Session Management
- `GET /auth/sessions`
- `POST /auth/sessions/{sessionId}/revoke`
- `POST /auth/sessions/revoke-all`

### Token Endpoint Grants
- `authorization_code`
- `refresh_token`
- `client_credentials`

## 5. Security Controls

- PKCE enforced (`S256` only)
- Strict redirect URI exact matching
- Mandatory `state` for authorization requests
- Mandatory `nonce` when `openid` scope is requested
- Client secret hashing with BCrypt
- JWT signed with RS256 and exposed via JWKS
- Session-aware token validation (sid claim)
- Token revocation via denylist + persistent revoke flag
- Refresh token rotation
- Browser logout support via IdP session revocation and optional RP-initiated logout endpoint
- Actuator health probes for ops readiness/liveness

## 6. Production Hardening Checklist

- Enable HTTPS and HSTS at ingress/reverse proxy
- Set strong `TOKEN_SIGNING_SECRET` and rotate periodically
- Disable OAuth seed in production (`oauth2.seed.enabled=false`)
- Use managed secrets (Vault/KMS), not plain env files
- Restrict CORS to exact origins
- Add brute-force and endpoint rate limiting for `/oauth2/authorize`, `/oauth2/token`, `/oauth2/introspect`
- Enable centralized audit log export + SIEM alerts
- Restrict Actuator exposure and secure `/actuator/**`
- Add key rollover strategy (new `kid`, overlap period, old key retirement)

## 7. Resource Server Validation Guide

For JWT access tokens:
- Fetch JWKS from `/.well-known/jwks.json`
- Validate signature algorithm and `kid`
- Validate `iss`, `aud`, `exp`, optional `nbf`
- Enforce scope authorization per API policy

For opaque/managed tokens:
- Call `/oauth2/introspect` with client auth
- Accept token only when `active=true`
- Enforce `scope`, `client_id`, and `sub` semantics

## 8. Multi-Application / Multi-Tenant Notes

- Multi-app supported through per-client `client_id`, scopes, grant types, redirect URIs
- Multi-tenant can be implemented via per-domain client ownership and issuer/audience partitioning by environment or tenant prefix
- Recommended next step: introduce tenant-aware issuer (`iss`) and audience strategy for strict isolation
