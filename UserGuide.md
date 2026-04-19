# User Guide

This guide explains how to build, run, and use the Passwordless IdP system in this repository.

## 1. What This System Provides

This project is a centralized passwordless Identity Provider (IdP) with:

- User self-registration
- Login and MFA verification (OTP, TOTP, WebAuthn/passkey)
- OAuth2 authorization code flow
- Token exchange and refresh
- Session management
- Admin UI for user and OAuth client operations

## 2. Prerequisites

Choose one runtime mode:

- Source mode: Java 17+, Maven wrapper
- Container mode: Docker + Docker Compose

Optional for OTP testing:

- MailHog web UI at http://localhost:8025 (when using docker compose)

## 3. Build and Run

### Option A: Run from source (fast local dev)

1. Build:

```bash
./mvnw clean package
```

2. Run:

```bash
./mvnw spring-boot:run
```

3. Open:

- IdP portal: http://localhost:8080/idp
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

### Option B: Run with docker compose (recommended for full flow)

1. Add local host mapping (important for WebAuthn origin/RP ID consistency):

```bash
# Linux/macOS
sudo sh -c 'echo "127.0.0.1 passwordless.actvn" >> /etc/hosts'
```

2. Build and start all services:

```bash
docker compose up -d --build
```

3. Open:

- Main app (behind nginx TLS): https://passwordless.actvn
- IdP portal: https://passwordless.actvn/idp
- Admin portal: https://passwordless.actvn/admin
- MailHog inbox: http://localhost:8025

Notes:

- The nginx certificate is self-signed in local docker mode. Your browser will show a warning. Continue for local development.
- WebAuthn/passkey testing works best through https://passwordless.actvn (not plain localhost).

## 4. Core URLs You Will Use

- End-user IdP portal: /idp
- Admin UI: /admin
- OAuth2 authorize (JSON): /oauth2/authorize
- OAuth2 token: /oauth2/token
- Refresh token endpoint: /token/refresh
- User profile endpoint: /auth/me
- User sessions endpoint: /auth/sessions

## 5. Quick Start: Use the IdP Portal (UI Flow)

1. Open /idp.
2. In "User Registration", create a user (email, first name, last name).
3. In "Login and Verification", start login using one method:
- OTP
- TOTP (if enrolled)
- WebAuthn USB key (if enrolled)
4. Verify MFA to obtain access and refresh tokens.
5. In "MFA Enrollment":
- Generate TOTP QR and activate it with a valid code, or
- Register USB security key and activate passkey method.
6. In "OAuth2 User Authorization":
- Send authorize request for a client
- Receive authorization code
- Exchange code at /oauth2/token
7. In "Session Control":
- List active sessions
- Revoke one or revoke all

## 6. Typical IdP API Flow (Without UI)

### 6.1 Register user

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "firstName": "Alice",
    "lastName": "Nguyen"
  }'
```

### 6.2 Start login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "alice@example.com",
    "clientId": "passwordless-web",
    "preferredMethod": "OTP"
  }'
```

### 6.3 Verify MFA

```bash
curl -X POST http://localhost:8080/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{
    "authTxId": "<AUTH_TX_ID>",
    "method": "OTP",
    "otp": "123456"
  }'
```

Use accessToken from verify response as Bearer token in later calls.

### 6.4 Authorize and get authorization code

```bash
curl -X POST http://localhost:8080/oauth2/authorize \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "responseType": "code",
    "clientId": "web-app-prod",
    "redirectUri": "https://client.example.com/callback",
    "scope": "openid profile email",
    "state": "state-123",
    "codeChallenge": "<PKCE_CHALLENGE>",
    "codeChallengeMethod": "S256",
    "nonce": "nonce-123"
  }'
```

### 6.5 Exchange code for tokens

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=<AUTH_CODE>" \
  -d "client_id=web-app-prod" \
  -d "redirect_uri=https://client.example.com/callback" \
  -d "code_verifier=<PKCE_VERIFIER>"
```

### 6.6 Refresh access token

```bash
curl -X POST http://localhost:8080/token/refresh \
  -H "Content-Type: application/json" \
  -d '{ "refreshToken": "<REFRESH_TOKEN>" }'
```

### 6.7 Register a USB security key (WebAuthn begin/finish)

Start registration ceremony:

```bash
curl -X POST http://localhost:8080/webauthn/v1/register/begin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice@example.com",
    "authenticatorAttachment": "cross-platform",
    "residentKeyRequired": false,
    "userVerification": "preferred"
  }'
```

Use returned `transactionId` and `publicKey` in browser WebAuthn APIs, then finish:

```bash
curl -X POST http://localhost:8080/webauthn/v1/register/finish \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "<TX_ID>",
    "credential": {
      "id": "<CREDENTIAL_ID>",
      "rawId": "<RAW_ID>",
      "type": "public-key",
      "response": {
        "attestationObject": "<ATTESTATION_OBJECT>",
        "clientDataJSON": "<CLIENT_DATA_JSON>"
      }
    }
  }'
```

## 7. Admin Guide (Basic)

Use /admin to:

- View and manage users
- Check registration status and passkeys/TOTP status
- Manage sessions
- Manage OAuth clients (create, update, rotate secret, activate/deactivate)

For API-driven OAuth client management, use endpoints under:

- /admin/api/oauth2/clients

## 8. Test and Validation Commands

Run full test suite:

```bash
./mvnw test -DskipTests=false
```

Run focused OAuth2/IdP tests:

```bash
./mvnw -Dtest=OAuth2ControllerTest,OAuth2FlowIntegrationTest,IdpPageControllerTest test
```

## 9. Troubleshooting

### WebAuthn/passkey fails in browser

- Use HTTPS route with host mapping: https://passwordless.actvn
- Confirm WEBAUTHN origin and RP ID match deployment values

### OTP code not received in dev

- Open MailHog UI: http://localhost:8025
- Check latest message and OTP content

### OAuth token exchange fails

- Confirm client_id and redirect_uri exactly match the registered OAuth client
- If PKCE required, provide correct code_verifier for the code_challenge used in authorize call

### Service not reachable

- Check containers:

```bash
docker compose ps
```

- Check app logs:

```bash
docker compose logs -f passwordless-service
```

## 10. Related Docs

- API reference: docs/API_DOCUMENTATION.md
- Deployment: docs/DEPLOYMENT.md
- Detailed IdP payload guide: docs/IDP_PORTAL_GUIDE.md
