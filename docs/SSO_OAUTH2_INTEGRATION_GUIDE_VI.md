# Hướng Dẫn Tích Hợp SSO và OAuth2 Cho Dự Án Phần Mềm

Tài liệu này dành cho các nhà phát triển muốn tích hợp hệ thống IdP của dự án vào ứng dụng, website, backend service hoặc SPA của họ.

Mục tiêu:

1. Dùng IdP làm nơi đăng nhập tập trung cho nhiều ứng dụng.
2. Tích hợp SSO qua OAuth2/OIDC.
3. Lấy access token, refresh token, id token để bảo vệ API.
4. Tích hợp vào frontend, backend và resource server.
5. Giảm lỗi triển khai bằng các mẫu payload và quy ước an toàn.

## 1. SSO Là Gì Trong Hệ Thống Này

SSO ở đây có nghĩa là:

1. Người dùng đăng nhập một lần vào IdP.
2. IdP phát hành token hoặc session.
3. Các ứng dụng khác tin cậy IdP và dùng token đó để xác thực người dùng.

Trong dự án này, SSO thường được triển khai qua OAuth2/OIDC:

- Người dùng đăng nhập bằng portal `/idp` hoặc flow `POST /auth/login` + `POST /auth/mfa/verify`.
- Ứng dụng client sử dụng `Authorization Code + PKCE` để nhận token.
- Backend của ứng dụng dùng `accessToken` để gọi API của chính nó hoặc API khác.

## 2. Kiến Trúc Tích Hợp

### 2.1 Thành phần chính

- IdP server: cung cấp `/auth/**`, `/oauth2/**`, `/.well-known/**`.
- Ứng dụng client: web app, mobile app, SPA hoặc backend service của bạn.
- Resource server: API cần bảo vệ, kiểm tra `Authorization: Bearer <token>`.

### 2.2 Mô hình khuyến nghị

- Web app / SPA: dùng `Authorization Code + PKCE`.
- Backend service: dùng `client_credentials` nếu là service-to-service.
- Portal nội bộ: dùng `/idp` và các API `/auth/*` để tạo user, login, MFA, quản lý phiên.

## 3. Endpoint Quan Trọng

### Discovery và public keys

- `GET /.well-known/openid-configuration`
- `GET /.well-known/jwks.json`

### Đăng nhập và MFA

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/mfa/verify`
- `POST /auth/mfa/totp/register`
- `POST /auth/mfa/totp/activate`
- `POST /auth/mfa/email/activate`

### OAuth2 / OIDC

- `POST /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/introspect`
- `POST /oauth2/revoke`
- `GET /oauth2/userinfo`

### App registration để gọi OTP/TOTP server-to-server

- `POST /apps/v1/register`
- `POST /otp/v1/send`
- `POST /otp/v1/verify`
- `POST /totp/v1/register`
- `POST /totp/v1/verify`

## 4. Luồng SSO Cho Ứng Dụng Web / SPA

### Bước 1: Đăng ký OAuth2 client

Tạo client trong admin portal hoặc qua API quản trị. Các thông tin tối thiểu:

- `clientId`
- `redirectUri`
- `allowedScopes`
- `grantTypes`
- `requirePkce=true`

Ví dụ cấu hình:

```json
{
  "clientName": "My Web App",
  "redirectUris": ["https://myapp.example.com/callback"],
  "allowedScopes": ["openid", "profile", "email"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "requirePkce": true,
  "active": true,
  "domainName": "default.com"
}
```

### Bước 2: Tạo PKCE verifier/challenge

```bash
CODE_VERIFIER="pkce-verifier-1234567890"
CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -binary -sha256 | openssl base64 -A | tr '+/' '-_' | tr -d '=')
```

### Bước 3: Chuyển người dùng đến authorize endpoint

```bash
curl -X POST https://passwordless.actvn/oauth2/authorize \
  -H "Authorization: Bearer <USER_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "responseType": "code",
    "clientId": "web-pkce-client",
    "redirectUri": "https://myapp.example.com/callback",
    "scope": "openid profile email",
    "state": "state-123",
    "codeChallenge": "<CODE_CHALLENGE>",
    "codeChallengeMethod": "S256",
    "nonce": "nonce-123"
  }'
```

### Bước 4: Đổi authorization code lấy token

```bash
curl -X POST https://passwordless.actvn/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=web-pkce-client" \
  --data-urlencode "redirect_uri=https://myapp.example.com/callback" \
  --data-urlencode "code=<AUTH_CODE>" \
  --data-urlencode "code_verifier=<CODE_VERIFIER>"
```

### Bước 5: Nhận token và tạo session ứng dụng

Response sẽ có các trường như:

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "id_token": "...",
  "token_type": "Bearer",
  "scope": "openid profile email"
}
```

Ứng dụng của bạn nên:

1. Lưu `access_token` cho các request API.
2. Giữ `refresh_token` ở nơi an toàn hơn `localStorage` nếu có thể.
3. Dùng `id_token` để nhận diện user ở lớp client nếu cần.

## 5. Luồng SSO Cho Backend Service

Nếu backend của bạn là service-to-service, dùng `client_credentials`.

### Ví dụ

```bash
curl -X POST https://passwordless.actvn/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=service-client" \
  --data-urlencode "client_secret=<SERVICE_CLIENT_SECRET>" \
  --data-urlencode "scope=api.read"
```

Kết quả là một `access_token` có thể dùng để gọi resource server.

## 6. Luồng SSO Từ Portal IdP

Nếu bạn đang dùng portal IdP nội bộ:

1. User vào `/idp`.
2. User đăng ký bằng `POST /auth/register`.
3. User login bằng `POST /auth/login`.
4. User xác thực OTP/TOTP/WebAuthn qua `POST /auth/mfa/verify`.
5. Sau khi authenticated, IdP trả token.
6. Ứng dụng của bạn dùng token để gắn session và gọi API.

Đây là flow phù hợp cho demo, portal nội bộ hoặc ứng dụng admin.

## 7. Tích Hợp Vào Frontend

Ví dụ tối giản với browser:

```js
async function startLogin(email) {
  const response = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      identifier: email,
      preferredMethod: 'OTP',
      clientId: 'passwordless-web'
    })
  });

  return await response.json();
}
```

Sau khi có `authTxId`, gọi verify:

```js
async function verifyLogin(authTxId, otp) {
  const response = await fetch('/auth/mfa/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      authTxId,
      method: 'OTP',
      otp
    })
  });

  return await response.json();
}
```

## 8. Tích Hợp Vào Backend

Backend của bạn nên làm các việc sau:

1. Nhận callback từ frontend.
2. Gửi request đến IdP.
3. Lưu token đúng cách.
4. Gọi resource server với `Authorization: Bearer`.

Ví dụ Node.js:

```js
const tokenResponse = await fetch('https://passwordless.actvn/oauth2/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    client_id: process.env.OAUTH_CLIENT_ID,
    redirect_uri: process.env.REDIRECT_URI,
    code_verifier: verifier
  })
});
```

## 9. Bảo Vệ API Trong Dự Án Của Bạn

### JWT validation

Khi nhận `access_token`, ứng dụng của bạn cần kiểm tra:

- chữ ký JWT bằng JWKS
- `iss`
- `aud`
- `exp`
- `scope`

### Quy trình khuyến nghị

1. Tải JWKS từ `/.well-known/jwks.json`.
2. Kiểm tra `kid`.
3. Xác minh chữ ký.
4. Kiểm tra `scope` phù hợp với API.
5. Nếu cần revoke tức thời, dùng introspection hoặc `sid`/session tracking.

## 10. So Sánh Dùng SSO OAuth2 Với Login Trực Tiếp

### Dùng SSO/OAuth2 khi:

- Bạn có nhiều ứng dụng cùng chia sẻ một IdP.
- Bạn muốn user login một lần và dùng cho nhiều app.
- Bạn cần chuẩn OIDC để dễ tích hợp với hệ sinh thái khác.

### Dùng `/auth/*` trực tiếp khi:

- Bạn xây portal nội bộ đơn giản.
- Bạn muốn chủ động điều khiển OTP/TOTP/WebAuthn trong UI riêng.
- Bạn không cần chuẩn redirect login của OAuth2.

## 11. Mẫu Tích Hợp Thực Tế

### Web app

1. Redirect user đến IdP authorize endpoint.
2. Nhận `code` ở callback.
3. Exchange `code` lấy token.
4. Gắn token vào session/cookie an toàn.

### SPA

1. Dùng PKCE.
2. Không lưu `clientSecret`.
3. Dùng `access_token` ngắn hạn.
4. Refresh token chỉ khi kiến trúc bảo mật cho phép.

### Backend service

1. Dùng `client_credentials` hoặc API key nếu gọi OTP/TOTP.
2. Lưu secret ở vault hoặc secret manager.
3. Giới hạn quyền theo scope.

## 12. Lỗi Thường Gặp

### `redirect_uri is not allowed`

Nguyên nhân:

- `redirectUri` không khớp với URI đã đăng ký.

### `Unsupported response_type`

Nguyên nhân:

- Chỉ hỗ trợ flow được cấu hình, thông thường là `code`.

### `No authentication method is available for this user`

Nguyên nhân:

- User chưa có OTP/TOTP/WebAuthn phù hợp.

### `Token is revoked` hoặc `Session is no longer active`

Nguyên nhân:

- Access token hoặc session đã bị thu hồi.

## 13. Checklist Trước Khi Go-Live

1. Bật HTTPS.
2. Đăng ký đầy đủ `redirectUri`.
3. Bật PKCE cho client công khai.
4. Kiểm tra `state` và `nonce`.
5. Xác minh JWKS và scope ở resource server.
6. Đặt secret trong vault.
7. Tắt seed tự động nếu đã lên production.

## 14. Tài Liệu Liên Quan

- [IdP Developer Integration Guide](IDP_DEVELOPER_INTEGRATION_GUIDE.md)
- [IdP Portal Guide](IDP_PORTAL_GUIDE.md)
- [OAuth2 Client Testing Guide](OAUTH2_CLIENT_TESTING_GUIDE.md)
- [OAuth2 Auth Server Design](OAUTH2_AUTH_SERVER_DESIGN.md)
