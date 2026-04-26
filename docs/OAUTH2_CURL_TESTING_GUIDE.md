# OAuth2/OIDC cURL Testing Guide

## 1. Discovery

```bash
curl -s http://localhost:8080/.well-known/openid-configuration | jq
curl -s http://localhost:8080/.well-known/jwks.json | jq
```

## 2. Authorization Code + PKCE

### 2.1 Prepare verifier/challenge

```bash
CODE_VERIFIER="pkce-verifier-1234567890"
CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -binary -sha256 | openssl base64 -A | tr '+/' '-_' | tr -d '=')
```

### 2.2 Authorize (JSON mode)

```bash
curl -s -X POST http://localhost:8080/oauth2/authorize \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer <USER_ACCESS_TOKEN>" \
  -d "{
    \"responseType\": \"code\",
    \"clientId\": \"web-pkce-client\",
    \"redirectUri\": \"http://localhost:3000/callback\",
    \"scope\": \"openid profile email\",
    \"state\": \"state-123\",
    \"codeChallenge\": \"${CODE_CHALLENGE}\",
    \"codeChallengeMethod\": \"S256\",
    \"nonce\": \"nonce-123\"
  }"
```

### 2.3 Exchange code for tokens

```bash
curl -s -X POST http://localhost:8080/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'client_id=web-pkce-client' \
  --data-urlencode 'redirect_uri=http://localhost:3000/callback' \
  --data-urlencode 'code=<AUTHORIZATION_CODE>' \
  --data-urlencode "code_verifier=${CODE_VERIFIER}" | jq
```

## 3. Refresh Token Rotation

```bash
curl -s -X POST http://localhost:8080/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'client_id=web-pkce-client' \
  --data-urlencode 'refresh_token=<REFRESH_TOKEN>' | jq
```

## 4. Client Credentials

```bash
curl -s -X POST http://localhost:8080/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=service-client' \
  --data-urlencode 'client_secret=<SERVICE_CLIENT_SECRET>' \
  --data-urlencode 'scope=api.read' | jq
```

## 5. Introspection

```bash
curl -s -X POST http://localhost:8080/oauth2/introspect \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=service-client' \
  --data-urlencode 'client_secret=<SERVICE_CLIENT_SECRET>' \
  --data-urlencode 'token=<ACCESS_TOKEN>' \
  --data-urlencode 'token_type_hint=access_token' | jq
```

## 6. Revocation

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/oauth2/revoke \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=service-client' \
  --data-urlencode 'client_secret=<SERVICE_CLIENT_SECRET>' \
  --data-urlencode 'token=<TOKEN_TO_REVOKE>'
```

## 7. UserInfo

```bash
curl -s http://localhost:8080/oauth2/userinfo \
  -H 'Authorization: Bearer <USER_ACCESS_TOKEN>' | jq
```

## 8. Security Test Cases

- Invalid redirect URI: change `redirectUri` to unregistered URL, expect 400
- Invalid `code_verifier`: send wrong verifier, expect 400
- Reuse revoked refresh token: expect invalid refresh token
- Expired token introspection: expect `{"active":false}`
- Revoked access token introspection: expect `{"active":false}`
