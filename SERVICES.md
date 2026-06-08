# Service URLs

> Base URL: `https://passwordless.actvn` hoặc `https://192.168.79.1`
> Local dev: `http://localhost:8080`

---

## Truy cập chính

| Service | IP | Domain |
|---|---|---|
| HTTP (redirect HTTPS) | http://192.168.79.1 | http://passwordless.actvn |
| HTTPS (entry point) | https://192.168.79.1 | https://passwordless.actvn |
| Local (không Docker) | http://localhost:8080 | — |

---

## Trang người dùng (GET — mở trên trình duyệt)

| Trang | IP | Domain |
|---|---|---|
| Login | https://192.168.79.1/login | https://passwordless.actvn/login |
| Register | https://192.168.79.1/register | https://passwordless.actvn/register |
| Xác minh OTP | https://192.168.79.1/verify-otp | https://passwordless.actvn/verify-otp |
| Cài đặt phương thức xác thực | https://192.168.79.1/setup-auth-methods | https://passwordless.actvn/setup-auth-methods |
| Identity Portal (IdP) | https://192.168.79.1/idp | https://passwordless.actvn/idp |
| OAuth2 Tester | https://192.168.79.1/oauth2-tester | https://passwordless.actvn/oauth2-tester |
| Admin Dashboard | https://192.168.79.1/admin | https://passwordless.actvn/admin |

---

## Tài liệu API

| Service | IP | Domain |
|---|---|---|
| Swagger UI | https://192.168.79.1/swagger-ui | https://passwordless.actvn/swagger-ui |
| OpenAPI Spec (JSON) | https://192.168.79.1/v3/api-docs | https://passwordless.actvn/v3/api-docs |

---

## Trang Test (Dev only)

| Trang | IP | Domain |
|---|---|---|
| OTP Test | https://192.168.79.1/otp/test | https://passwordless.actvn/otp/test |
| OTP Test Register | https://192.168.79.1/otp/test/register | https://passwordless.actvn/otp/test/register |
| TOTP Test | https://192.168.79.1/totp/test | https://passwordless.actvn/totp/test |
| WebAuthn Test | https://192.168.79.1/webauthn/test | https://passwordless.actvn/webauthn/test |

---

## API Xác thực — Auth

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/auth/register` | Đăng ký tài khoản |
| POST | `/auth/login` | Đăng nhập |
| POST | `/auth/logout` | Đăng xuất |
| GET | `/auth/me` | Thông tin người dùng hiện tại |
| GET | `/auth/sessions` | Danh sách phiên đang hoạt động |
| POST | `/auth/sessions/{sessionId}/revoke` | Thu hồi một phiên |
| POST | `/auth/sessions/revoke-all` | Thu hồi tất cả phiên |
| POST | `/auth/mfa/verify` | Xác minh MFA |
| POST | `/auth/mfa/totp/register` | Tạo TOTP enrollment |
| POST | `/auth/mfa/totp/activate` | Kích hoạt TOTP làm MFA chính |
| POST | `/auth/mfa/webauthn/activate` | Kích hoạt WebAuthn làm MFA chính |
| POST | `/auth/mfa/email/activate` | Kích hoạt Email OTP làm MFA chính |

---

## API OTP

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/otp/v1/register` | Đăng ký cấu hình OTP |
| POST | `/otp/v1/send` | Gửi OTP (SMS/Email) |
| POST | `/otp/v1/verify` | Xác minh mã OTP |

---

## API TOTP

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/totp/v1/register` | Đăng ký TOTP (trả về QR code) |
| POST | `/totp/v1/verify` | Xác minh mã TOTP |

---

## API WebAuthn (FIDO2)

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/webauthn/v1/register/begin` | Bắt đầu đăng ký passkey |
| POST | `/webauthn/v1/register/finish` | Hoàn tất đăng ký passkey |
| POST | `/webauthn/v1/login/begin` | Bắt đầu đăng nhập passkey |
| POST | `/webauthn/v1/login/finish` | Hoàn tất đăng nhập passkey |
| GET | `/webauthn/v1/register/challenge/{username}` | Lấy challenge đăng ký (legacy) |
| POST | `/webauthn/v1/register/credential` | Submit credential đăng ký (legacy) |
| GET | `/webauthn/v1/login/challenge/{username}` | Lấy challenge đăng nhập (legacy) |
| POST | `/webauthn/v1/login/credential` | Submit credential đăng nhập (legacy) |

---

## API OAuth2 / OIDC

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/oauth2/authorize` | Authorization endpoint (browser redirect) |
| GET | `/oauth2/authorize/callback` | Callback sau xác thực |
| POST | `/oauth2/authorize` | Authorization (JSON body) |
| POST | `/oauth2/token` | Lấy access token |
| POST | `/oauth2/introspect` | Kiểm tra token |
| POST | `/oauth2/revoke` | Thu hồi token |
| GET | `/oauth2/userinfo` | Thông tin người dùng (OIDC) |
| GET | `/oauth2/jwks` | Public keys (JWK Set) |
| GET | `/oauth2/consent` | Trang đồng ý quyền truy cập |
| POST | `/oauth2/consent/approve` | Chấp nhận consent |
| POST | `/oauth2/consent/deny` | Từ chối consent |
| POST | `/token/refresh` | Làm mới access token |

---

## OIDC Discovery

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/.well-known/openid-configuration` | OIDC metadata |
| GET | `/.well-known/jwks.json` | Public keys (JWK Set) |

---

## API Recovery (Khôi phục tài khoản)

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/recovery/request` | Yêu cầu khôi phục |
| POST | `/recovery/verify` | Xác minh token khôi phục |
| POST | `/recovery/reset-mfa` | Đặt lại MFA |
| POST | `/recovery/backup-codes` | Tạo backup codes |
| GET | `/recovery/backup-codes/count` | Số backup codes còn lại |

---

## API Quản lý ứng dụng (Apps)

> Yêu cầu header: `X-API-Key: <api_key>`

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/apps/v1/register` | Đăng ký ứng dụng mới |
| GET | `/apps/v1/list` | Danh sách ứng dụng |
| GET | `/apps/v1/{id}` | Chi tiết ứng dụng |
| POST | `/apps/v1/{id}/activate` | Kích hoạt ứng dụng |
| POST | `/apps/v1/{id}/deactivate` | Vô hiệu hóa ứng dụng |
| DELETE | `/apps/v1/{id}` | Xóa ứng dụng |
| POST | `/apps/v1/{id}/regenerate-key` | Tạo lại API key |
| GET | `/apps/v1/audit/logs` | Toàn bộ audit logs |
| GET | `/apps/v1/audit/logs/app/{appId}` | Logs theo ứng dụng |
| GET | `/apps/v1/audit/logs/event/{eventType}` | Logs theo loại sự kiện |
| GET | `/apps/v1/audit/logs/range` | Logs theo khoảng thời gian |
| GET | `/apps/v1/audit/stats/{appId}` | Thống kê request |

---

## API Admin

> Yêu cầu JWT Bearer token (khi `security.admin.enabled=true`)

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/admin/api/me` | Thông tin admin hiện tại |
| GET | `/admin/api/dashboard/stats` | Thống kê tổng quan |
| GET | `/admin/api/keys` | Danh sách signing keys |
| POST | `/admin/api/keys/rotate` | Rotate signing key |
| GET | `/admin/api/domains` | Danh sách domain |
| POST | `/admin/api/domains` | Tạo domain mới |
| GET | `/admin/api/domains/{id}` | Chi tiết domain |
| PUT | `/admin/api/domains/{id}` | Cập nhật domain |
| DELETE | `/admin/api/domains/{id}` | Xóa domain |
| GET | `/admin/api/oauth2/clients` | Danh sách OAuth2 client |
| POST | `/admin/api/oauth2/clients` | Tạo OAuth2 client |
| GET | `/admin/api/oauth2/clients/{id}` | Chi tiết client |
| PUT | `/admin/api/oauth2/clients/{id}` | Cập nhật client |
| DELETE | `/admin/api/oauth2/clients/{id}` | Xóa client |
| POST | `/admin/api/oauth2/clients/{id}/rotate-secret` | Rotate client secret |
| POST | `/admin/api/oauth2/clients/{id}/activate` | Kích hoạt client |
| POST | `/admin/api/oauth2/clients/{id}/deactivate` | Vô hiệu hóa client |
| GET | `/admin/api/users` | Danh sách người dùng |
| POST | `/admin/api/users` | Tạo người dùng |
| GET | `/admin/api/users/{id}` | Chi tiết người dùng |
| PUT | `/admin/api/users/{id}` | Cập nhật người dùng |
| DELETE | `/admin/api/users/{id}` | Xóa người dùng |
| POST | `/admin/api/users/{id}/suspend` | Tạm khóa người dùng |
| POST | `/admin/api/users/{id}/activate` | Kích hoạt lại người dùng |
| GET | `/admin/api/users/{id}/auth-registrations` | Các phương thức xác thực đã đăng ký |
| PUT | `/admin/api/users/{id}/preferred-mfa` | Đặt MFA ưu tiên |
| GET | `/admin/api/users/{id}/totp-keys` | Danh sách TOTP keys |
| DELETE | `/admin/api/users/{id}/totp-keys/{keyId}` | Xóa TOTP key |
| GET | `/admin/api/users/{id}/passkeys` | Danh sách passkeys |
| DELETE | `/admin/api/users/{id}/passkeys/{keyId}` | Xóa passkey |
| GET | `/admin/api/users/{id}/otp-sessions` | Danh sách OTP sessions |
| GET | `/admin/api/users/{id}/sessions` | Danh sách phiên đăng nhập |
| POST | `/admin/api/users/{id}/sessions/{sessionId}/revoke` | Thu hồi phiên |
| POST | `/admin/api/users/{id}/sessions/revoke-all` | Thu hồi tất cả phiên |

---

## Hạ tầng / DevOps

| Service | IP | Domain | Ghi chú |
|---|---|---|---|
| MailHog UI | http://192.168.79.1:8025 | — | Xem email test (OTP, magic links) |
| MailHog SMTP | 192.168.79.1:1025 | — | SMTP server nội bộ |
| MySQL | 192.168.79.1:3307 | — | user: root / pass: 123456 |
| Redis | 192.168.79.1:6379 (internal) | — | Cache & session store |
| Health Check | https://192.168.79.1/actuator/health | https://passwordless.actvn/actuator/health | Kiểm tra trạng thái |
| Metrics | https://192.168.79.1/actuator/metrics | https://passwordless.actvn/actuator/metrics | |
| App Info | https://192.168.79.1/actuator/info | https://passwordless.actvn/actuator/info | |

---

## Tài khoản mặc định

| Thông tin | Giá trị |
|---|---|
| Admin email | `admin@system.local` |
| Admin password | xem trong `src/main/resources/application.yml` hoặc biến môi trường |

---

## Lưu ý

- SSL certificate là self-signed, trình duyệt sẽ cảnh báo — chọn **Advanced → Proceed** để tiếp tục.
- Để truy cập qua domain `passwordless.actvn`, thêm vào `C:\Windows\System32\drivers\etc\hosts`:
  ```
  192.168.79.1  passwordless.actvn
  ```
- JavaScript SDK: `https://passwordless.actvn/js/passwordless-sdk.js`

---

## Tổng hợp nhanh (Quick Reference)

| Service | IP | Domain | Mô tả |
|---|---|---|---|
| Ứng dụng chính | https://192.168.79.1 | https://passwordless.actvn | Entry point chính qua Nginx |
| Login Page | https://192.168.79.1/login | https://passwordless.actvn/login | Trang đăng nhập |
| Identity Portal (IdP) | https://192.168.79.1/idp | https://passwordless.actvn/idp | Portal đăng ký/đăng nhập cho end-user |
| Admin Dashboard | https://192.168.79.1/admin | https://passwordless.actvn/admin | Quản lý users, OAuth clients, domains |
| Swagger UI | https://192.168.79.1/swagger-ui | https://passwordless.actvn/swagger-ui | Tài liệu API tương tác |
| OpenAPI Spec | https://192.168.79.1/v3/api-docs | https://passwordless.actvn/v3/api-docs | Spec dạng JSON |
| MailHog UI | http://192.168.79.1:8025 | — | Xem email test (OTP, magic links) |
| MailHog SMTP | 192.168.79.1:1025 | — | SMTP server nội bộ |
| MySQL | 192.168.79.1:3307 | — | Database (user: root / pass: 123456) |
| Redis | 192.168.79.1:6379 (internal) | — | Cache & session store |
| Health Check | https://192.168.79.1/actuator/health | https://passwordless.actvn/actuator/health | Kiểm tra trạng thái |

### Tài khoản mặc định

| Thông tin | Giá trị |
|---|---|
| Admin email | `admin@system.local` |
| Admin password | xem trong `src/main/resources/application.yml` hoặc biến môi trường |

### Hosts file (Windows)

Thêm vào `C:\Windows\System32\drivers\etc\hosts`:
```
192.168.79.1  passwordless.actvn
```
