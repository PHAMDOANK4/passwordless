# 2. Phân tích và thiết kế hệ thống

## 2.1. Phân tích yêu cầu hệ thống

### 2.1.1. Yêu cầu chức năng

Hệ thống IdP passwordless cần đáp ứng các yêu cầu chức năng sau:

1. Quản lý người dùng và tổ chức (domain):
- Tạo mới người dùng, cập nhật thông tin, khóa/mở khóa tài khoản.
- Quản lý trạng thái người dùng theo từng domain.

2. Xác thực không mật khẩu đa phương thức:
- WebAuthn/FIDO2 (passkey, USB security key).
- TOTP (ứng dụng authenticator).
- OTP (email/SMS) cho kịch bản tương thích và khôi phục.

3. Điều phối luồng đăng nhập:
- Khởi tạo transaction xác thực.
- Chọn phương thức xác thực theo cấu hình hoặc tự động fallback.
- Xác minh MFA và phát hành phiên.

4. Hỗ trợ OAuth2/OIDC:
- OAuth2 Authorization Code + PKCE.
- Cấp phát access token, refresh token, id token.
- Hỗ trợ introspect, revoke, userinfo, discovery và JWKS.

5. Quản lý phiên và token:
- Liệt kê phiên hoạt động.
- Thu hồi theo phiên hoặc thu hồi toàn bộ.
- Làm mới token qua refresh token rotation.

6. Quản trị và vận hành:
- Quản lý OAuth client.
- Quản lý app tích hợp, API key, audit log.
- Dashboard thống kê phục vụ giám sát.

### 2.1.2. Yêu cầu phi chức năng

1. Bảo mật:
- Chống phishing thông qua WebAuthn.
- Chống replay bằng challenge TTL, counter WebAuthn và OTP dùng một lần.
- Hỗ trợ thu hồi token theo thời gian thực (blacklist + session check).

2. Hiệu năng:
- Phản hồi nhanh cho các API xác thực phổ biến.
- Truy cập trạng thái phiên qua Redis để giảm tải DB.

3. Khả năng mở rộng:
- Có thể scale ngang service IdP.
- Hỗ trợ nhiều ứng dụng client và nhiều domain.

4. Tính sẵn sàng và vận hành:
- Chạy được dưới Docker Compose.
- Reverse proxy qua Nginx với TLS và forwarded headers.

5. Khả năng bảo trì:
- Kiến trúc modular monolith, tách lớp controller/service/repository.
- Có test tự động và CI pipeline build/scan.

6. Tính tương thích chuẩn:
- Tuân thủ OAuth2/OIDC cho tích hợp SSO.
- Tuân thủ WebAuthn cho xác thực khóa công khai.

## 2.2. Mô hình hệ thống và các tác nhân

### 2.2.1. Mô hình hệ thống

Hệ thống đóng vai trò là Identity Provider trung tâm. Ứng dụng bên ngoài không xử lý logic xác thực cốt lõi mà ủy quyền cho IdP.

Các thành phần chính:
- IdP Web/API Service (Spring Boot).
- MySQL (lưu trữ bền vững dữ liệu danh tính, phiên, token, OAuth client).
- Redis (lưu trạng thái ngắn hạn: ceremony WebAuthn, session active).
- Nginx (TLS termination, reverse proxy).

### 2.2.2. Các tác nhân

1. Người dùng cuối:
- Đăng ký tài khoản, đăng nhập, cấu hình MFA, quản lý phiên.

2. Quản trị viên:
- Quản lý user, OAuth client, app tích hợp, audit, dashboard.

3. Ứng dụng tích hợp (Relying Party):
- Điều hướng người dùng vào luồng authorize.
- Nhận authorization code và đổi token.

4. Vận hành hệ thống:
- Triển khai, giám sát, cấu hình hạ tầng và bảo mật runtime.

## 2.3. Phân tích luồng nghiệp vụ

### 2.3.1. Luồng đăng ký người dùng

1. Người dùng gửi thông tin đăng ký.
2. Hệ thống tạo user gắn domain tương ứng.
3. Người dùng có thể kích hoạt MFA (TOTP/WebAuthn/Email OTP) sau đăng ký.

### 2.3.2. Luồng đăng nhập passwordless

1. Người dùng gọi API đăng nhập với định danh.
2. Hệ thống tạo auth transaction (TTL, attempt count, context IP/UA).
3. Chọn phương thức xác thực:
- Ưu tiên WebAuthn nếu đã có passkey.
- Fallback TOTP nếu đã đăng ký.
- Fallback OTP nếu chưa có các phương thức trên.
4. Người dùng hoàn tất challenge tương ứng.
5. Hệ thống xác minh thành công thì tạo session và phát hành token.

### 2.3.3. Luồng OAuth2 Authorization Code + PKCE

1. Ứng dụng client chuyển hướng người dùng đến `/oauth2/authorize`.
2. IdP xác thực người dùng và kiểm tra client/redirect URI/scope.
3. IdP cấp authorization code một lần, TTL ngắn.
4. Client gọi `/oauth2/token` với code verifier để đổi token.
5. IdP trả access token, refresh token (và id token khi phù hợp).

### 2.3.4. Luồng quản trị phiên và thu hồi

1. Người dùng/quản trị viên truy vấn danh sách phiên.
2. Chọn thu hồi 1 phiên hoặc toàn bộ phiên.
3. Hệ thống đánh dấu revoked trong DB, cập nhật trạng thái cache và blacklist token liên quan.

## 2.4. Thiết kế kiến trúc hệ thống

### 2.4.1. Kiến trúc logic

1. Presentation Layer:
- IdP Portal (`/idp`) cho người dùng.
- Admin Portal (`/admin`) cho quản trị.

2. API Layer:
- Nhóm endpoint auth, webauthn, otp/totp, oauth2, token, admin, apps.

3. Service Layer:
- Điều phối xác thực.
- Xử lý OAuth2/OIDC.
- Quản lý session/token.
- Audit và rate limiting.

4. Data Layer:
- MySQL cho dữ liệu bền vững.
- Redis cho trạng thái ngắn hạn và tối ưu truy cập.

### 2.4.2. Kiến trúc triển khai

- Nginx đứng trước làm reverse proxy và TLS termination.
- Passwordless service xử lý nghiệp vụ.
- MySQL và Redis cung cấp storage/state.
- Có thể mở rộng nhiều instance app dùng chung DB/Redis.

## 2.5. Thiết kế cơ chế xác thực

### 2.5.1. WebAuthn/FIDO2

Luồng mới sử dụng begin/finish:
- `POST /webauthn/v1/register/begin` và `POST /webauthn/v1/register/finish`.
- `POST /webauthn/v1/login/begin` và `POST /webauthn/v1/login/finish`.

Thiết kế bảo mật:
- Challenge ngẫu nhiên lưu Redis theo transaction, có TTL.
- Xác minh attestation/assertion qua WebAuthn4J.
- Kiểm tra sign counter để phát hiện khả năng replay/cloned authenticator.

### 2.5.2. TOTP

- Sinh secret theo chuẩn TOTP và phát QR cho authenticator app.
- Xác minh theo cửa sổ thời gian hợp lệ.
- Có cơ chế kích hoạt phương thức sau đăng ký.

### 2.5.3. OTP Email/SMS

- Sinh OTP có TTL và giới hạn số lần thử.
- Có chống spam qua resend cooldown.
- OTP hợp lệ sẽ bị vô hiệu ngay sau khi dùng thành công.

### 2.5.4. Chính sách điều phối và an toàn phiên

- Theo dõi số lần thất bại, áp dụng lockout.
- Tạo session sau khi xác thực thành công.
- Access token ký RS256, refresh token xoay vòng và lưu hash.

## 2.6. Thiết kế cơ sở dữ liệu

### 2.6.1. Nhóm bảng chính

1. Danh tính:
- `domains`, `users`.

2. Phương thức xác thực:
- `webauthn_authenticators`, `registered_totp`, `sent_otp`.

3. OAuth2 và phiên:
- `oauth_clients`, `authorization_codes`, `oauth_tokens`, `user_sessions`.

4. Bảo mật và vận hành:
- `token_blacklist`, `registered_apps`, `audit_logs`.

### 2.6.2. Nguyên tắc thiết kế dữ liệu

- Tách dữ liệu bền vững (MySQL) và trạng thái ngắn hạn (Redis).
- Liên kết phiên-token để hỗ trợ revoke đồng bộ.
- Xử lý xóa dữ liệu theo thứ tự phụ thuộc để tránh lỗi khóa ngoại.

### 2.6.3. Bảo mật dữ liệu

- Lưu hash cho refresh token và secret nhạy cảm theo chính sách.
- Khuyến nghị tăng cường mã hóa at-rest cho TOTP secret.
- Chuẩn hóa vòng đời key ký JWT với cơ chế rotation trong môi trường production.

## 2.7. Thiết kế API và giao tiếp hệ thống

### 2.7.1. Nhóm API chính

1. Auth API (`/auth`):
- đăng ký, đăng nhập, verify MFA, profile, session, logout.

2. WebAuthn API (`/webauthn/v1`):
- challenge/credential cũ và begin/finish mới.

3. OAuth2/OIDC API (`/oauth2`, `/.well-known`):
- authorize, token, introspect, revoke, userinfo, discovery, jwks.

4. OTP/TOTP API (`/otp/v1`, `/totp/v1`):
- đăng ký, gửi mã, xác minh mã.

5. Admin và App API (`/admin/api`, `/apps/v1`):
- quản trị người dùng, client, app key, audit.

### 2.7.2. Mô hình giao tiếp

- Client-Server qua HTTPS.
- JSON cho phần lớn endpoint nghiệp vụ.
- `application/x-www-form-urlencoded` cho một số endpoint OAuth2 chuẩn.
- Mã trạng thái HTTP phản ánh đúng kết quả (200/201/400/401/403/404/429).

### 2.7.3. Bảo mật giao tiếp

- TLS tại Nginx.
- API key filter cho một số kênh OTP/TOTP theo app.
- Rate limiting theo app để bảo vệ tài nguyên.
- Audit log cho truy vết vận hành và điều tra sự cố.

### 2.7.4. Nguyên tắc tích hợp liên hệ thống

- Ứng dụng bên ngoài nên dùng OAuth2 Authorization Code + PKCE.
- Không lưu token ở nơi không an toàn phía client.
- Luôn hỗ trợ revoke/introspect để đồng bộ trạng thái đăng xuất hoặc khóa tài khoản.
