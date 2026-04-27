# IdP UI Portal (React + TypeScript)

UI này triển khai các nhóm màn hình cho hệ thống IdP hiện tại:

- End-user: login / MFA challenge / profile / logout
- Admin: dashboard, users, OAuth clients, domain, system config, audit logs, API key
- Developer portal: self-service client registration, Swagger placeholder, token inspector
- OAuth: consent page và error page

## 1. Chạy local

```bash
cd src/main/frontend
npm install
npm run dev
```

Build production:

```bash
npm run build
```

Biến môi trường tùy chọn:

- `IDP_API_BASE`: base URL backend IdP (mặc định rỗng, gọi cùng origin)
- `IDP_USE_MOCK=true`: bật mock toàn phần cho demo UI
- `IDP_ALLOW_ROLE_SWITCH=true`: cho phép role switch thủ công (chỉ dùng khi test)

## 2. Routing chính

- `/idp`
- `/mfa`
- `/profile`
- `/logout`
- `/admin/dashboard`
- `/admin/users`
- `/admin/clients`
- `/admin/domains`
- `/admin/system`
- `/admin/audit`
- `/admin/api-keys`
- `/developer/register-client`
- `/developer/swagger`
- `/developer/token-inspector`
- `/oauth/consent`
- `/oauth/error`

## 3. Kết nối backend thật

Hiện tại app dùng mock qua `src/services/mockApi.ts`.

Mặc định, các flow end-user đã gọi endpoint thật:

- `POST /auth/login`
- `POST /auth/mfa/verify`
- `GET /auth/me`
- `GET /auth/sessions`
- `POST /auth/sessions/{sessionId}/revoke`
- `POST /auth/sessions/revoke-all`
- `POST /oauth2/revoke`

Các module admin đã nối endpoint thật:

- Domains: `GET/POST /admin/api/domains`
- Audit logs: `GET /apps/v1/audit/logs`
- API keys/apps: `GET /apps/v1/list`, `POST /apps/v1/register`, `POST /apps/v1/{id}/deactivate`

Để nối backend thật, cập nhật `src/services/api.ts` theo endpoint thực tế trong tài liệu:

- `POST /auth/login`
- `POST /auth/mfa/verify`
- `GET|POST /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `POST /auth/sessions/{sessionId}/revoke`
- `POST /auth/sessions/revoke-all`
- Các endpoint admin (`/admin/v1/*`) và developer portal

Khuyến nghị:

- Không lưu access/refresh token trong `localStorage`
- Dùng memory state + httpOnly cookie nếu có backend BFF
- Bật CSRF protection cho thao tác nhạy cảm

UI đã có interceptor tự động gắn `X-CSRF-TOKEN`/`X-XSRF-TOKEN` cho request thay đổi dữ liệu (POST/PUT/PATCH/DELETE), đặc biệt ở admin forms. Backend hiện tại đang để CSRF disabled trong security config, nên khi backend bật CSRF policy thì UI này đã sẵn sàng header.

## 7. Session bootstrap và lỗi 401/403

- Khi app reload, UI tự silent call `GET /auth/me` để khôi phục profile/role nếu còn session cookie IdP.
- Route guard sẽ:
	- chuyển đến `/error/401` nếu chưa xác thực
	- chuyển đến `/error/403` nếu không đủ quyền role

## 4. WebAuthn integration notes

Trong UI hiện tại, WebAuthn đang ở mức mô phỏng luồng.

Khi nối backend thật, implement các bước:

1. Gọi endpoint challenge (ví dụ `/auth/mfa/webauthn/challenge`)
2. Dùng `navigator.credentials.get(...)` (login) hoặc `navigator.credentials.create(...)` (register)
3. Gửi assertion/attestation về backend verify endpoint

## 5. Scope triển khai theo đợt

- Đợt 1: login portal + MFA + profile cơ bản
- Đợt 2: session management + WebAuthn UX + admin dashboard + users
- Đợt 3: OAuth client + consent + developer portal
- Đợt 4: audit logs + system config + API key management

## 6. Component dùng chung

- `Navbar`, `Sidebar`, `PageShell`, `ToastHost`, `StatCard`
- API layer tách biệt: `api.ts` (adapter) + `mockApi.ts` (mock mode)
