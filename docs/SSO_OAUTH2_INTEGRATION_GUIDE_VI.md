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

- `GET|POST /oauth2/authorize`
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

Với trình duyệt, hãy redirect user đến URL GET thực tế thay vì gọi `curl` POST. Endpoint `/oauth2/authorize` hỗ trợ cả `GET` và `POST`, nhưng khuyến nghị dùng `GET` + session cookie của IdP để bảo đảm luồng đăng nhập tự nhiên và tương thích với browser.

Ví dụ URL redirect:

```text
https://passwordless.actvn/oauth2/authorize?response_type=code&client_id=web-pkce-client&redirect_uri=https%3A%2F%2Fmyapp.example.com%2Fcallback&scope=openid%20profile%20email&state=state-123&code_challenge=<CODE_CHALLENGE>&code_challenge_method=S256&nonce=nonce-123
```

Ví dụ từ frontend:

```js
window.location.href = 'https://passwordless.actvn/oauth2/authorize?response_type=code&client_id=web-pkce-client&redirect_uri=https%3A%2F%2Fmyapp.example.com%2Fcallback&scope=openid%20profile%20email&state=state-123&code_challenge=<CODE_CHALLENGE>&code_challenge_method=S256&nonce=nonce-123';
```

Nếu client đã có token hợp lệ và cần xử lý theo luồng hậu kiểm, backend có thể gọi `POST /oauth2/authorize`, nhưng đây không phải cách khuyến nghị cho browser-based SSO.

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

### Bước 6: Chọn đúng kiểu tích hợp

| Trường hợp | Nên dùng | Lý do |
| --- | --- | --- |
| Portal nội bộ do chính bạn kiểm soát UI | Token trực tiếp từ `POST /auth/mfa/verify` | Luồng ngắn, IdP trả token ngay sau OTP/TOTP/WebAuthn, phù hợp portal admin hoặc ứng dụng nội bộ. |
| Ứng dụng bên thứ ba, web app, SPA, mobile | OAuth2 Authorization Code + PKCE | Không lộ `client_secret`, tận dụng redirect login chuẩn, dễ tích hợp SSO giữa nhiều ứng dụng. |
| Backend service gọi server-to-server | `client_credentials` | Không cần user tương tác, dùng cho machine-to-machine. |

### Cảnh báo bảo mật cho client công khai

`client_secret` không được đặt trong frontend, SPA hoặc mobile app. Nếu ứng dụng không thể giữ bí mật phía server, phải dùng PKCE và coi client là public client.

Các giá trị như `client_secret` chỉ được lưu ở backend an toàn, vault hoặc secret manager.

### Bổ sung về logout và session revocation

Khi user đăng xuất khỏi ứng dụng, nên làm đồng thời hai việc:

1. Thu hồi refresh token bằng `POST /oauth2/revoke` để ngăn cấp access token mới.
2. Kết thúc session IdP bằng `POST /auth/sessions/{sessionId}/revoke` hoặc `POST /auth/sessions/revoke-all` để chấm dứt toàn bộ trạng thái đăng nhập.

Nếu hỗ trợ OIDC RP-initiated logout, có thể redirect user tới:

```text
GET /oauth2/logout?id_token_hint=<ID_TOKEN>&post_logout_redirect_uri=https%3A%2F%2Fmyapp.example.com%2Flogged-out
```

Phân biệt quan trọng:

- Revoke token: vô hiệu hóa refresh token hoặc access token đã phát hành.
- Kết thúc session IdP: ngắt trạng thái đăng nhập ở phía IdP, hữu ích khi muốn logout khỏi nhiều ứng dụng cùng dùng SSO.
- Nếu chỉ revoke token mà không revoke session, user vẫn có thể còn session đăng nhập ở IdP và xin code mới trong cùng trình duyệt.

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

## 12. Xử Lý Lỗi Chi Tiết

### 12.1 `/oauth2/authorize`

| HTTP | Body lỗi chuẩn | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- | --- |
| 400 | `invalid_request` | Thiếu `response_type`, `client_id`, `redirect_uri`, `state`, `code_challenge`, hoặc tham số không hợp lệ. | Kiểm tra query string, đảm bảo redirect URL đã encode đúng và PKCE dùng `S256`. |
| 401 | `login_required` hoặc redirect sang login UI | User chưa có session IdP hợp lệ. | Cho user đăng nhập lại qua browser session cookie. |
| 403 | `access_denied` | Client không được phép dùng scope hoặc redirect URI này. | Kiểm tra cấu hình client, scope và domain/tenant. |
| 429 | `slow_down` hoặc custom rate-limit body | Quá nhiều request authorize từ cùng user/client/IP. | Giảm retry, áp dụng backoff và kiểm tra rate limit. |
| 500 | `server_error` | Lỗi nội bộ khi tạo authorization code hoặc lưu session. | Kiểm tra log server và thử lại sau. |

Ví dụ body chuẩn cho OAuth2 authorization error:

```json
{
  "error": "invalid_request",
  "error_description": "Missing required parameter: code_challenge"
}
```

### 12.2 `/oauth2/token`

| HTTP | Body lỗi chuẩn | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- | --- |
| 400 | `invalid_grant` | Authorization code đã hết hạn, đã dùng rồi, `code_verifier` sai, hoặc refresh token không hợp lệ. | Dùng code mới, kiểm tra PKCE, đảm bảo exchange chỉ làm một lần. |
| 400 | `unsupported_grant_type` | `grant_type` không được hỗ trợ. | Gửi đúng `authorization_code`, `refresh_token` hoặc `client_credentials`. |
| 401 | `invalid_client` | `client_id`/`client_secret` sai hoặc client không phải confidential client. | Xác thực lại credentials, không đưa `client_secret` vào frontend. |
| 403 | `unauthorized_client` | Client không được phép dùng grant type hoặc scope này. | Điều chỉnh quyền client ở IdP. |
| 429 | `slow_down` hoặc custom rate-limit body | Quá nhiều lần đổi code/refresh token trong thời gian ngắn. | Áp dụng backoff và tránh retry tự động với cùng một code. |
| 500 | `server_error` | Lỗi phát hành hoặc ký token. | Kiểm tra log server, JWKS và cấu hình signing key. |

Ví dụ body chuẩn:

```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code expired or already used"
}
```

### 12.3 `/auth/login`

| HTTP | Body lỗi chuẩn | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- | --- |
| 400 | Định dạng riêng của IdP, ví dụ `{"error":"AUTH_INVALID_REQUEST"...}` | Thiếu `identifier`, `clientId` hoặc `preferredMethod` sai. | Gửi đúng payload và validate trước ở frontend/backend. |
| 401 | `AUTH_INVALID_CREDENTIALS` hoặc tương đương | User không tồn tại, bị khóa, hoặc không thể khởi tạo login. | Kiểm tra trạng thái user và domain. |
| 403 | `AUTH_LOGIN_NOT_ALLOWED` | Domain/tenant chặn local login hoặc user không có quyền. | Kiểm tra cấu hình SSO, domain policy và client policy. |
| 429 | `AUTH_RATE_LIMITED` | Spam login request hoặc brute-force. | Giảm retry, thêm CAPTCHA hoặc throttle ở client. |
| 500 | `AUTH_INTERNAL_ERROR` | Lỗi phát OTP, tạo `authTxId` hoặc gửi email/SMS. | Kiểm tra log, provider email/SMS và cấu hình hệ thống. |

Ví dụ body custom:

```json
{
  "error": "AUTH_RATE_LIMITED",
  "message": "Too many login attempts",
  "details": "Try again after 60 seconds",
  "traceId": "0f7f7d1f0a5e4c9d"
}
```

### 12.4 `/auth/mfa/verify`

| HTTP | Body lỗi chuẩn | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- | --- |
| 400 | Định dạng riêng của IdP, ví dụ `{"error":"AUTH_INVALID_OTP"...}` | Thiếu `authTxId`, `otp`, `credentialId` hoặc `method` không khớp. | Gửi đúng challenge và method mà login step trả về. |
| 401 | `AUTH_MFA_EXPIRED` hoặc tương đương | `authTxId` hết hạn hoặc phiên xác thực không còn hiệu lực. | Bắt đầu lại từ `/auth/login`. |
| 403 | `AUTH_MFA_FAILED` / `AUTH_MFA_NOT_ALLOWED` | OTP sai quá số lần cho phép, user chưa enroll phương thức đó, hoặc policy chặn. | Hướng dẫn user enroll MFA phù hợp hoặc chờ hết lockout. |
| 429 | `AUTH_RATE_LIMITED` | Quá nhiều lần verify sai. | Dừng retry, hiển thị đếm ngược và thử lại sau. |
| 500 | `AUTH_INTERNAL_ERROR` | Lỗi phát token hoặc ghi session. | Kiểm tra log và retry sau khi xác nhận server ổn định. |

Ví dụ body custom:

```json
{
  "error": "AUTH_INVALID_OTP",
  "message": "OTP expired or already used",
  "details": "Please request a new OTP",
  "traceId": "0f7f7d1f0a5e4c9d"
}
```

### 12.5 Các lỗi logic thường gặp

- `redirect_uri is not allowed`: `redirectUri` không khớp với URI đã đăng ký.
- `Unsupported response_type`: client đang gửi flow không được hỗ trợ, thông thường phải là `code`.
- `No authentication method is available for this user`: user chưa có OTP/TOTP/WebAuthn phù hợp.
- `Token is revoked` hoặc `Session is no longer active`: access token hoặc session đã bị thu hồi.

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
