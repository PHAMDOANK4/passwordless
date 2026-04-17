# IdP Portal Guide (/idp)

This guide documents the end-user Identity Provider (IdP) portal flow exposed at `/idp` and the backend API payloads used by each step.

## Overview

The portal UI is available at:

- `GET /idp`
- `GET /idp/index.html`

The full flow covers:

1. User self-registration
2. Login start and MFA verification
3. MFA enrollment (TOTP/passkey/email OTP preference)
4. OAuth2 authorization code flow
5. Session management
6. Token refresh and userinfo

## 1) User Self-Registration

### Endpoint

- `POST /auth/register`

### Request

```json
{
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Nguyen",
  "phoneNumber": "+12025550123",
  "mfaEnabled": false,
  "preferredMethod": "OTP"
}
```

### Response (201)

```json
{
  "userId": "b4d7a95f-7a44-46d2-9582-5e06d62b89d3",
  "email": "alice@example.com",
  "displayName": "Alice Nguyen",
  "domain": "example.com",
  "status": "ACTIVE",
  "mfaEnabled": true,
  "preferredMfaMethod": "EMAIL"
}
```

## 2) Login And MFA Verification

### 2.1 Start login

### Endpoint

- `POST /auth/login`

### Request

```json
{
  "identifier": "alice@example.com",
  "clientId": "passwordless-web",
  "preferredMethod": "OTP"
}
```

`preferredMethod` can be `OTP`, `TOTP`, or `WEBAUTHN`. If omitted, the server auto-selects based on user enrollments.

### Example response for OTP

```json
{
  "authTxId": "6ff77f7d-dfac-45b8-a2d6-6b7ddff7f4d5",
  "nextStep": "VERIFY",
  "selectedMethod": "OTP",
  "expiresInSeconds": 300,
  "message": "OTP sent to destination",
  "challenge": {
    "destination": "alice@example.com",
    "resendAllowedAt": 1763402160000,
    "remainingAttempts": 5
  }
}
```

### Example response for TOTP

```json
{
  "authTxId": "a7f0b673-2116-49dc-82eb-69f8e6cf2b95",
  "nextStep": "VERIFY",
  "selectedMethod": "TOTP",
  "expiresInSeconds": 300,
  "message": "Enter TOTP code from authenticator app",
  "challenge": null
}
```

### Example response for WebAuthn

```json
{
  "authTxId": "0e3c1d18-1f7d-4b95-88ef-92d2f272f3a0",
  "nextStep": "VERIFY",
  "selectedMethod": "WEBAUTHN",
  "expiresInSeconds": 300,
  "message": "Complete WebAuthn assertion",
  "challenge": {
    "challenge": { "value": "...base64url..." },
    "allowCredentials": [],
    "timeout": 60000,
    "rpId": "localhost",
    "userVerification": "preferred"
  }
}
```

### 2.2 Verify MFA

### Endpoint

- `POST /auth/mfa/verify`

### OTP verify request

```json
{
  "authTxId": "6ff77f7d-dfac-45b8-a2d6-6b7ddff7f4d5",
  "method": "OTP",
  "otp": "123456"
}
```

### TOTP verify request

```json
{
  "authTxId": "a7f0b673-2116-49dc-82eb-69f8e6cf2b95",
  "method": "TOTP",
  "totp": 123456
}
```

### WebAuthn verify request

```json
{
  "authTxId": "0e3c1d18-1f7d-4b95-88ef-92d2f272f3a0",
  "method": "WEBAUTHN",
  "webauthnAssertion": {
    "id": "credential-id",
    "rawId": "base64url-raw-id",
    "type": "public-key",
    "response": {
      "authenticatorData": "base64url-authenticator-data",
      "clientDataJSON": "base64url-client-data",
      "signature": "base64url-signature",
      "userHandle": ""
    }
  }
}
```

### Verify response (success)

```json
{
  "authenticated": true,
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "dFJ2RW9VQ2dK...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "sessionId": "9ecf5b90-25b8-47c2-9c08-58cf86b9e8b5",
  "userId": "b4d7a95f-7a44-46d2-9582-5e06d62b89d3",
  "email": "alice@example.com"
}
```

## 3) MFA Enrollment (Authenticated User)

These APIs require:

- `Authorization: Bearer <accessToken>`

### 3.1 Generate TOTP enrollment

- `POST /auth/mfa/totp/register`

Response example:

```json
{
  "username": "alice@example.com",
  "uri": "otpauth://totp/example.com:alice@example.com?secret=...",
  "qr": "iVBORw0KGgoAAAANSUhEUg..."
}
```

### 3.2 Activate TOTP as preferred MFA

- `POST /auth/mfa/totp/activate`

Request:

```json
{
  "totp": 123456
}
```

Response:

```json
{
  "mfaEnabled": true,
  "preferredMfaMethod": "TOTP"
}
```

### 3.3 Activate WebAuthn as preferred MFA

- `POST /auth/mfa/webauthn/activate`

Response:

```json
{
  "mfaEnabled": true,
  "preferredMfaMethod": "WEBAUTHN"
}
```

### 3.4 Activate email OTP as preferred MFA

- `POST /auth/mfa/email/activate`

Response:

```json
{
  "mfaEnabled": true,
  "preferredMfaMethod": "EMAIL"
}
```

### 3.5 Passkey registration APIs used by portal

The portal uses WebAuthn registration endpoints before calling `/auth/mfa/webauthn/activate`.

- `GET /webauthn/v1/register/challenge/{username}`
- `POST /webauthn/v1/register/credential`

Example registration credential payload:

```json
{
  "id": "credential-id",
  "rawId": "base64url-raw-id",
  "type": "public-key",
  "response": {
    "attestationObject": "base64url-attestation-object",
    "clientDataJSON": "base64url-client-data-json"
  }
}
```

## 4) OAuth2 Authorization Code Flow (Portal)

The portal uses JSON authorize request and standard form token exchange.

### 4.1 Authorize user

- `POST /oauth2/authorize`
- Header: `Authorization: Bearer <accessToken>`

Request (camelCase is accepted):

```json
{
  "responseType": "code",
  "clientId": "web-app-prod",
  "redirectUri": "https://client.example.com/callback",
  "scope": "openid profile email",
  "state": "state-123",
  "codeChallenge": "7OBZrY8xK8kNfVn3F7N8Xr7nB7nXkTWptnCz5afVfRw",
  "codeChallengeMethod": "S256",
  "nonce": "nonce-123"
}
```

Response:

```json
{
  "redirectUri": "https://client.example.com/callback?code=abc123&state=state-123",
  "code": "abc123",
  "state": "state-123"
}
```

### 4.2 Exchange code for tokens

- `POST /oauth2/token`
- Content-Type: `application/x-www-form-urlencoded`

Request form fields:

```text
grant_type=authorization_code
code=abc123
client_id=web-app-prod
redirect_uri=https://client.example.com/callback
code_verifier=plain-or-pkce-verifier
client_secret=optional-for-public-pkce-clients
```

Response:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "fM4W...",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "scope": "openid profile email"
}
```

## 5) Session Management

These APIs require:

- `Authorization: Bearer <accessToken>`

### 5.1 Get sessions

- `GET /auth/sessions`

Response example:

```json
[
  {
    "sessionId": "9ecf5b90-25b8-47c2-9c08-58cf86b9e8b5",
    "ipAddress": "127.0.0.1",
    "deviceInfo": "Mozilla/5.0 ...",
    "authMethod": "WEBAUTHN",
    "authLevel": 2,
    "createdAt": "2026-04-17T09:10:42.117619Z",
    "lastActivityAt": "2026-04-17T09:12:11.502821Z",
    "expiresAt": "2026-04-18T09:10:42.117619Z",
    "current": true
  }
]
```

### 5.2 Revoke one session

- `POST /auth/sessions/{sessionId}/revoke`

Response:

```json
{
  "status": "revoked",
  "sessionId": "9ecf5b90-25b8-47c2-9c08-58cf86b9e8b5"
}
```

### 5.3 Revoke all sessions

- `POST /auth/sessions/revoke-all`

Response:

```json
{
  "status": "revoked_all",
  "revokedCount": 2
}
```

## 6) Token Refresh And User Info

### Refresh access token

- `POST /token/refresh`
- Body:

```json
{
  "refreshToken": "fM4W..."
}
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "JmA1...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Read profile

- `GET /auth/me`
- Header: `Authorization: Bearer <accessToken>`

Response:

```json
{
  "userId": "b4d7a95f-7a44-46d2-9582-5e06d62b89d3",
  "email": "alice@example.com",
  "displayName": "Alice Nguyen",
  "status": "ACTIVE",
  "role": "USER",
  "mfaEnabled": true,
  "preferredMfaMethod": "TOTP"
}
```

### OIDC userinfo

- `GET /oauth2/userinfo`
- Header: `Authorization: Bearer <accessToken>`

Response:

```json
{
  "sub": "b4d7a95f-7a44-46d2-9582-5e06d62b89d3",
  "email": "alice@example.com",
  "email_verified": true,
  "name": "Alice Nguyen",
  "preferred_username": "alice@example.com",
  "updated_at": 1776418212
}
```

## Quick Curl Sequence

```bash
# 1) Register
curl -sS -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email":"alice@example.com",
    "firstName":"Alice",
    "lastName":"Nguyen",
    "mfaEnabled":false
  }'

# 2) Start login (OTP)
curl -sS -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "identifier":"alice@example.com",
    "preferredMethod":"OTP",
    "clientId":"passwordless-web"
  }'

# 3) Verify OTP
curl -sS -X POST http://localhost:8080/auth/mfa/verify \
  -H 'Content-Type: application/json' \
  -d '{
    "authTxId":"<AUTH_TX_ID>",
    "method":"OTP",
    "otp":"123456"
  }'

# 4) List sessions
curl -sS http://localhost:8080/auth/sessions \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

## Notes

- The portal JavaScript implementation is in `src/main/resources/static/idp/js/app.js`.
- The page route `/idp` redirects to `/idp/index.html`.
- `/oauth2/token` returns snake_case JSON fields, while `/auth/*` and `/token/refresh` return camelCase.
