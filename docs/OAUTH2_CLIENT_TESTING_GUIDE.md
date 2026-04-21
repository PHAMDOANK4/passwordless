# OAuth2 Client Testing Guide

This guide provides an end-to-end way to test an OAuth2 client against this IdP implementation.

Covered flow:
1. Create OAuth2 client.
2. Authenticate user and get bearer access token.
3. Run Authorization Code + PKCE flow.
4. Exchange code for tokens.
5. Validate userinfo and introspection.
6. Revoke token and verify inactive state.

## 1. Prerequisites

1. Stack is running (Docker Compose or local app).
2. You can call IdP base URL.
3. Tools installed: curl, openssl, jq (optional but recommended).

If you run with Nginx + self-signed certificate, use -k in curl commands.

## 2. Set Environment Variables

```bash
export BASE_URL="https://localhost"
export CURL="curl -k -sS"

export TEST_EMAIL="oauth2-tester@example.com"
export TEST_FIRST_NAME="OAuth2"
export TEST_LAST_NAME="Tester"

export REDIRECT_URI="https://oauthdebugger.com/debug"
export SCOPE="openid profile email"
export STATE="state-123"
export NONCE="nonce-123"
```

If you run without Nginx/TLS, set:

```bash
export BASE_URL="http://localhost:8080"
export CURL="curl -sS"
```

## 3. Create OAuth2 Client

```bash
CLIENT_JSON=$($CURL -X POST "$BASE_URL/admin/api/oauth2/clients" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientName\": \"OAuth2 Test Client\",
    \"redirectUris\": [\"$REDIRECT_URI\"],
    \"allowedScopes\": [\"openid\", \"profile\", \"email\"],
    \"grantTypes\": [\"authorization_code\", \"refresh_token\"],
    \"requirePkce\": true,
    \"active\": true,
    \"domainName\": \"default.com\",
    \"createdBy\": \"manual-test\"
  }")

echo "$CLIENT_JSON"

export OAUTH_CLIENT_ID=$(echo "$CLIENT_JSON" | jq -r '.clientId')
export OAUTH_CLIENT_SECRET=$(echo "$CLIENT_JSON" | jq -r '.clientSecret')
```

Expected response contains:
- id
- clientId
- clientSecret
- issuedAt

Important: save clientSecret immediately.

## 4. Register Test User (if needed)

```bash
$CURL -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$TEST_EMAIL\",
    \"firstName\": \"$TEST_FIRST_NAME\",
    \"lastName\": \"$TEST_LAST_NAME\",
    \"mfaEnabled\": false
  }"
```

If user already exists, you can continue.

## 5. Login and Get User Access Token

### 5.1 Start login with OTP

```bash
LOGIN_JSON=$($CURL -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"identifier\": \"$TEST_EMAIL\",
    \"preferredMethod\": \"OTP\",
    \"clientId\": \"passwordless-web\"
  }")

echo "$LOGIN_JSON"
export AUTH_TX_ID=$(echo "$LOGIN_JSON" | jq -r '.authTxId')
```

### 5.2 Get OTP code (dev/testing)

Option A: read from MailHog UI/API.

Option B: read latest OTP from MySQL container:

```bash
docker exec passwordless-mysql mysql -upasswordless -pchangeme -D passwordless \
  -e "SELECT otp,destination,last_sent_at FROM sent_otp ORDER BY last_sent_at DESC LIMIT 1;"
```

Copy OTP value and set:

```bash
export OTP_CODE="123456"
```

### 5.3 Verify OTP and capture bearer token

```bash
VERIFY_JSON=$($CURL -X POST "$BASE_URL/auth/mfa/verify" \
  -H "Content-Type: application/json" \
  -d "{
    \"authTxId\": \"$AUTH_TX_ID\",
    \"method\": \"OTP\",
    \"otp\": \"$OTP_CODE\"
  }")

echo "$VERIFY_JSON"
export USER_ACCESS_TOKEN=$(echo "$VERIFY_JSON" | jq -r '.accessToken')
```

## 6. Generate PKCE Verifier and Challenge

```bash
export CODE_VERIFIER=$(openssl rand -base64 48 | tr -d '=+/' | cut -c1-64)
export CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
```

## 7. Authorize (Get Authorization Code)

Use JSON authorize endpoint for easier automated testing:

```bash
AUTHORIZE_JSON=$($CURL -X POST "$BASE_URL/oauth2/authorize" \
  -H "Authorization: Bearer $USER_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"responseType\": \"code\",
    \"clientId\": \"$OAUTH_CLIENT_ID\",
    \"redirectUri\": \"$REDIRECT_URI\",
    \"scope\": \"$SCOPE\",
    \"state\": \"$STATE\",
    \"codeChallenge\": \"$CODE_CHALLENGE\",
    \"codeChallengeMethod\": \"S256\",
    \"nonce\": \"$NONCE\"
  }")

echo "$AUTHORIZE_JSON"
export AUTH_CODE=$(echo "$AUTHORIZE_JSON" | jq -r '.code')
```

Expected response fields:
- redirectUri
- code
- state

## 8. Exchange Code for Tokens

```bash
TOKEN_JSON=$($CURL -X POST "$BASE_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$AUTH_CODE" \
  --data-urlencode "client_id=$OAUTH_CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code_verifier=$CODE_VERIFIER" \
  --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET")

echo "$TOKEN_JSON"

export OAUTH_ACCESS_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.access_token')
export OAUTH_REFRESH_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.refresh_token')
```

Expected response fields:
- access_token
- token_type
- expires_in
- refresh_token
- id_token
- scope

## 9. Validate UserInfo

```bash
$CURL "$BASE_URL/oauth2/userinfo" \
  -H "Authorization: Bearer $OAUTH_ACCESS_TOKEN"
```

Expected:
- sub
- email
- preferred_username

## 10. Introspect Access Token

```bash
$CURL -X POST "$BASE_URL/oauth2/introspect" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=$OAUTH_ACCESS_TOKEN" \
  --data-urlencode "token_type_hint=access_token" \
  --data-urlencode "client_id=$OAUTH_CLIENT_ID" \
  --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET"
```

Expected:
- active: true

## 11. Refresh Token Test

```bash
$CURL -X POST "$BASE_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=$OAUTH_REFRESH_TOKEN" \
  --data-urlencode "client_id=$OAUTH_CLIENT_ID" \
  --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET"
```

Expected:
- new access_token
- new refresh_token (rotation)

## 12. Revoke and Verify Inactive

### 12.1 Revoke access token

```bash
$CURL -X POST "$BASE_URL/oauth2/revoke" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=$OAUTH_ACCESS_TOKEN" \
  --data-urlencode "token_type_hint=access_token" \
  --data-urlencode "client_id=$OAUTH_CLIENT_ID" \
  --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET"
```

### 12.2 Introspect again

```bash
$CURL -X POST "$BASE_URL/oauth2/introspect" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=$OAUTH_ACCESS_TOKEN" \
  --data-urlencode "token_type_hint=access_token" \
  --data-urlencode "client_id=$OAUTH_CLIENT_ID" \
  --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET"
```

Expected:
- active: false

## 13. Negative Test Cases (Recommended)

1. Wrong redirect_uri at token exchange -> expect 4xx.
2. Wrong code_verifier -> expect invalid code_verifier error.
3. Reuse same authorization code -> expect invalid/expired code.
4. Missing bearer token at authorize -> expect missing bearer error.
5. Wrong client_secret for confidential client call -> expect invalid client credentials.

## 14. Test Completion Checklist

Mark test as passed when all checks are true:

1. Client created and credentials captured.
2. User authenticated and bearer token obtained.
3. Authorization code generated with PKCE.
4. Code exchanged to token set successfully.
5. UserInfo returns correct subject/email.
6. Introspection shows active=true before revoke.
7. Revoke executed successfully.
8. Introspection shows active=false after revoke.
