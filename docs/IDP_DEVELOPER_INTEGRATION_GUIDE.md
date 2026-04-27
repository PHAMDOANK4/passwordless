# Hướng Dẫn Tích Hợp IdP Cho Ứng Dụng

Tài liệu này dành cho các nhà phát triển muốn tích hợp hệ thống xác thực IdP của dự án vào ứng dụng hoặc phần mềm của mình.

Mục tiêu là giúp bạn:

1. Đăng ký người dùng vào IdP.
2. Khởi tạo đăng nhập bằng OTP, TOTP hoặc WebAuthn.
3. Xác thực MFA và nhận access token / refresh token.
4. Tích hợp OAuth2 Authorization Code + PKCE cho ứng dụng client.
5. Gọi API bảo vệ bằng token và quản lý phiên đăng nhập.

## 1. Tổng Quan Kiến Trúc

Hệ thống hỗ trợ hai kiểu tích hợp phổ biến:

1. Tích hợp theo kiểu portal/central login: ứng dụng của bạn chuyển người dùng sang IdP, xác thực xong rồi dùng token trả về để gọi API.
2. Tích hợp theo kiểu OAuth2 client: ứng dụng của bạn đăng ký như một OAuth2 client, dùng Authorization Code + PKCE để lấy token.

Nếu bạn xây dựng web app hoặc SPA, khuyến nghị dùng OAuth2 + PKCE.
Nếu bạn cần xác thực người dùng trong portal IdP nội bộ, dùng các API `/auth/*` trực tiếp.

## 2. URL Quan Trọng

Khi chạy local qua Nginx hoặc Docker Compose, các endpoint thường có dạng:

```text
https://passwordless.actvn
```

Một số đường dẫn chính:

- `GET /idp` - mở giao diện IdP portal
- `POST /auth/register` - tạo người dùng
- `POST /auth/login` - bắt đầu đăng nhập
- `POST /auth/mfa/verify` - xác thực OTP/TOTP/WebAuthn
- `POST /auth/mfa/totp/register` - tạo TOTP enrollment
- `POST /auth/mfa/totp/activate` - kích hoạt TOTP
- `POST /auth/mfa/email/activate` - bật Email OTP làm MFA ưu tiên
- `GET|POST /oauth2/authorize` - xin authorization code
- `POST /oauth2/token` - đổi code hoặc refresh token lấy token mới
- `POST /oauth2/revoke` - thu hồi refresh token hoặc token theo chuẩn OAuth2
- `GET /oauth2/userinfo` - lấy thông tin user từ access token

## 3. Luồng Tích Hợp Khuyến Nghị

### 3.1 Đăng ký người dùng

Ứng dụng của bạn tạo user trên IdP bằng API đăng ký:

```bash
curl -X POST https://passwordless.actvn/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "firstName": "Alice",
    "lastName": "Nguyen",
    "mfaEnabled": false,
    "preferredMethod": "OTP"
  }'
```

Ví dụ response:

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

Lưu ý:

- `email`, `firstName`, `lastName` là bắt buộc.
- `preferredMethod` có thể là `OTP`, `TOTP`, hoặc `WEBAUTHN`.
- Nếu `preferredMethod` là `OTP`, hệ thống hiện tại sẽ set MFA ưu tiên tương ứng với Email OTP.

### 3.2 Bắt đầu đăng nhập

Sau khi có user, ứng dụng gửi yêu cầu login.

Request khuyến nghị dùng trường `identifier`:

```bash
curl -X POST https://passwordless.actvn/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "alice@example.com",
    "preferredMethod": "OTP",
    "clientId": "passwordless-web"
  }'
```

Ví dụ response khi dùng OTP:

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

Nếu ứng dụng cũ đang gửi `email` thay vì `identifier`, backend hiện tại vẫn hỗ trợ tương thích ngược.

### 3.3 Xác thực MFA

Khi người dùng nhập OTP/TOTP hoặc hoàn thành WebAuthn, gọi API verify:

```bash
curl -X POST https://passwordless.actvn/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{
    "authTxId": "6ff77f7d-dfac-45b8-a2d6-6b7ddff7f4d5",
    "method": "OTP",
    "otp": "123456"
  }'
```

Ví dụ response thành công:

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

Sau khi nhận token:

- Lưu `accessToken` ở phía client hoặc backend theo mô hình bảo mật của bạn.
- Dùng `refreshToken` để làm mới phiên khi cần.
- Dùng `sessionId` để hiển thị hoặc quản lý phiên đăng nhập.

## 4. Tích Hợp OAuth2 Cho Ứng Dụng Riêng

Nếu bạn có web app, mobile app hoặc SPA riêng, nên dùng OAuth2 Authorization Code + PKCE.

### 4.1 Đăng ký OAuth2 client

Tạo client trong hệ thống quản trị rồi lấy `clientId` và `clientSecret` nếu cần.

Các giá trị cần có:

- `clientId`
- `redirectUri`
- `allowedScopes`
- `grantTypes`
- `requirePkce=true`

### 4.2 Xin authorization code

Người dùng nên được redirect bằng trình duyệt đến authorize URL thực tế:

```text
https://passwordless.actvn/oauth2/authorize?response_type=code&client_id=web-pkce-client&redirect_uri=https%3A%2F%2Fmyapp.example.com%2Fcallback&scope=openid%20profile%20email&state=state-123&code_challenge=<pkce-challenge>&code_challenge_method=S256&nonce=nonce-123
```

Endpoint `/oauth2/authorize` hỗ trợ cả `GET` và `POST`, nhưng với web app/SPA nên dùng `GET` + session cookie của IdP.

Ví dụ frontend:

```js
window.location.href = 'https://passwordless.actvn/oauth2/authorize?response_type=code&client_id=web-pkce-client&redirect_uri=https%3A%2F%2Fmyapp.example.com%2Fcallback&scope=openid%20profile%20email&state=state-123&code_challenge=<pkce-challenge>&code_challenge_method=S256&nonce=nonce-123';
```

### 4.3 Đổi code lấy token

```bash
curl -X POST https://passwordless.actvn/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=<AUTH_CODE>" \
  --data-urlencode "client_id=web-pkce-client" \
  --data-urlencode "redirect_uri=https://myapp.example.com/callback" \
  --data-urlencode "code_verifier=<PKCE_VERIFIER>"
```

Lưu ý: nếu ứng dụng của bạn là public client như SPA hoặc mobile app, không được đặt `clientSecret` trong frontend. Khi đó phải dùng PKCE và chỉ dùng `client_id` công khai.

### 4.4 Phân biệt token trực tiếp và OAuth2 Authorization Code

| Trường hợp | Cơ chế phù hợp | Ghi chú |
| --- | --- | --- |
| Portal nội bộ, UI do bạn kiểm soát hoàn toàn | Token trực tiếp từ `POST /auth/mfa/verify` | Dùng khi bạn muốn IdP trả token ngay sau bước MFA để giảm số bước tích hợp. |
| Web app, SPA, mobile, ứng dụng bên thứ ba | OAuth2 Authorization Code + PKCE | Đây là luồng chuẩn cho SSO, tránh lộ secret và hỗ trợ tích hợp đa ứng dụng. |
| Backend service | `client_credentials` | Không dùng user session. |

### 4.5 Logout và thu hồi session

Khi user đăng xuất khỏi ứng dụng, nên thực hiện cả thu hồi token lẫn kết thúc session:

1. Gọi `POST /oauth2/revoke` để thu hồi refresh token hoặc access token cần vô hiệu hóa.
2. Gọi `POST /auth/sessions/{sessionId}/revoke` nếu muốn logout một phiên cụ thể.
3. Gọi `POST /auth/sessions/revoke-all` nếu muốn logout toàn bộ thiết bị hoặc toàn bộ phiên của user.

Nếu triển khai OIDC RP-initiated logout, có thể redirect user tới:

```text
GET /oauth2/logout?id_token_hint=<ID_TOKEN>&post_logout_redirect_uri=https%3A%2F%2Fmyapp.example.com%2Flogged-out
```

Phân biệt:

- Thu hồi token: chặn token đã phát hành, thường áp dụng cho refresh token để ngăn lấy access token mới.
- Kết thúc session IdP: ngắt trạng thái đăng nhập ở IdP, tác động tới SSO và các ứng dụng khác cùng phiên.
- Nếu chỉ thu hồi token mà không kết thúc session, user vẫn có thể còn đăng nhập ở IdP trong trình duyệt hiện tại.

## 5. Gọi API Từ Ứng Dụng Của Bạn

Khi đã có `accessToken`, gọi các API cần xác thực bằng header:

```http
Authorization: Bearer <accessToken>
```

Ví dụ lấy thông tin user:

```bash
curl https://passwordless.actvn/oauth2/userinfo \
  -H "Authorization: Bearer <accessToken>"
```

Ví dụ gọi các endpoint hồ sơ:

- `GET /auth/me`
- `GET /auth/sessions`
- `POST /auth/sessions/{sessionId}/revoke`
- `POST /auth/sessions/revoke-all`

### 5.1 Tích Hợp Server-to-Server Bằng API Key

Nếu backend của bạn cần gọi trực tiếp các API OTP/TOTP, trước tiên hãy đăng ký application để lấy `X-API-Key`.

Ví dụ đăng ký app:

```bash
curl -X POST https://passwordless.actvn/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Backend App",
    "description": "Backend service integrating IdP",
    "rateLimitPerMinute": 60,
    "rateLimitPerHour": 1000
  }'
```

Sau đó dùng API key này cho các endpoint như:

- `POST /otp/v1/send`
- `POST /otp/v1/verify`
- `POST /totp/v1/register`
- `POST /totp/v1/verify`

Ví dụ gọi OTP:

```bash
curl -X POST https://passwordless.actvn/otp/v1/send \
  -H "X-API-Key: <API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "sms",
    "destination": "+1234567890"
  }'
```

## 6. Tích Hợp Trong Frontend

Nếu frontend của bạn gọi trực tiếp các endpoint IdP:

1. Gọi `POST /auth/register` để tạo user.
2. Gọi `POST /auth/login` để lấy `authTxId` và `challenge`.
3. Hiển thị ô nhập OTP/TOTP hoặc khởi tạo WebAuthn.
4. Gọi `POST /auth/mfa/verify` để lấy token.
5. Dùng token cho các request tiếp theo.

Ví dụ JavaScript tối giản:

```js
async function login(email) {
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

## 7. Tích Hợp Trong Backend

Nếu backend của bạn là nơi giữ bí mật, nên để backend:

1. Nhận credential từ frontend.
2. Gọi IdP để đăng nhập/xác thực.
3. Nhận access token và refresh token.
4. Lưu token theo chính sách bảo mật của hệ thống bạn.

Không nên để secret hoặc refresh token xuất hiện trong log công khai.

## 8. Các Trường Hợp Cần Lưu Ý

- `authTxId` là bắt buộc khi verify MFA.
- `preferredMethod` có thể làm hệ thống chọn OTP/TOTP/WebAuthn theo nhu cầu.
- Nếu user chưa enroll TOTP hoặc WebAuthn, các chế độ đó sẽ bị từ chối.
- Nếu domain bật SSO, local register/login sẽ bị chặn.

## 9. Khuyến Nghị Bảo Mật

1. Luôn dùng HTTPS trong môi trường thật.
2. Không hardcode `clientSecret` trong frontend.
3. Lưu `refreshToken` ở backend hoặc cơ chế an toàn tương đương.
4. Dùng `state` và `nonce` trong OAuth2.
5. Bật PKCE cho mọi client công khai.

## 10. Kiểm Tra Nhanh Khi Tích Hợp

Khi phát triển local, thứ tự kiểm tra nhanh là:

1. `POST /auth/register`
2. `POST /auth/login`
3. `POST /auth/mfa/verify`
4. `GET /oauth2/userinfo`
5. `GET|POST /oauth2/authorize` và `POST /oauth2/token`

Nếu bước login trả lỗi, hãy kiểm tra payload gửi lên phải chứa `identifier`, `preferredMethod`, và `clientId`.
