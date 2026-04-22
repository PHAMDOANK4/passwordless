# Tài liệu mô tả code dự án (module + hàm)

## 1) Tổng quan kiến trúc mã nguồn

Mã backend nằm tại `src/main/java/org/openidentityplatform/passwordless`, tổ chức theo hướng **module nghiệp vụ**:

- `auth`: điều phối đăng ký/đăng nhập/MFA/session người dùng cuối.
- `oauth2`: Authorization Code + PKCE, token endpoint, introspect/revoke/userinfo.
- `token`: phát hành/kiểm tra JWT, refresh token rotation, blacklist.
- `webauthn`: đăng ký/đăng nhập passkey (FIDO2/WebAuthn), ceremony state.
- `otp`: gửi/verify OTP email/SMS.
- `totp`: đăng ký và verify TOTP.
- `apps`: đăng ký app tích hợp, API key auth, rate limit, audit log.
- `admin`: API quản trị user/client/session.
- `iam`: domain/user entity và repository.
- `exceptions`, `configuration`: xử lý lỗi và cấu hình hệ thống.

Thiết kế lớp chủ đạo: **Controller -> Service -> Repository -> Model/Entity**.

---

## 2) Module `auth`

### 2.1 `AuthController`
- `register(...)`: tạo user mới qua `AuthOrchestratorService.register`.
- `login(...)`: khởi tạo transaction xác thực + challenge theo MFA method.
- `verify(...)`: xác thực OTP/TOTP/WebAuthn và cấp token/session.
- `registerTotp(...)`: tạo URI/QR đăng ký TOTP cho user hiện tại.
- `activateTotp(...)`: verify mã TOTP đầu tiên để bật TOTP làm MFA.
- `activateWebAuthn(...)`: bật WebAuthn làm MFA nếu user đã có passkey.
- `activateEmailOtp(...)`: bật Email OTP làm MFA.
- `me(...)`: lấy hồ sơ user từ access token.
- `logout(...)`: blacklist access token + revoke session hiện tại.
- `sessions(...)`: liệt kê session đang active của user.
- `revokeSession(...)`: thu hồi 1 session cụ thể.
- `revokeAllSessions(...)`: thu hồi toàn bộ session của user.

### 2.2 `AuthOrchestratorService` (service lõi điều phối)
- `register(...)`: chuẩn hóa email, kiểm tra trùng, resolve/create domain theo email, lưu user + MFA preference ban đầu.
- `registerTotp(...)`: lấy current user từ bearer token, gọi `TotpService.register`, sinh QR.
- `activateTotp(...)`: verify TOTP hợp lệ rồi cập nhật `mfaEnabled=true`, `preferredMfaMethod=TOTP`.
- `activateWebAuthn(...)`: yêu cầu user đã có passkey, sau đó bật phương thức WEBAUTHN.
- `activateEmailOtp(...)`: bật phương thức EMAIL OTP nếu user có email.
- `login(...)`: 
  - kiểm tra user lock/status,
  - chọn method bằng `selectMethod(...)` (ưu tiên passkey -> totp -> otp),
  - tạo `AuthTransactionState`,
  - với OTP: gửi mã,
  - với TOTP: trả hướng dẫn nhập mã,
  - với WebAuthn: sinh challenge + trả assertion options.
- `verify(...)`:
  - lấy transaction và kiểm tra TTL,
  - route verify theo method (OTP/TOTP/WebAuthn),
  - nếu fail: `onVerificationFailed(...)` tăng đếm thất bại + lock user theo policy,
  - nếu pass: reset failed attempts, tạo session, phát hành access+refresh token, xóa transaction.
- `me(...)`: validate token + trạng thái session, trả thông tin user.
- `logout(...)`: blacklist JWT theo `jti` + revoke session `sid`.
- `sessions(...)`: map danh sách `Session` -> `AuthSessionResponse`.
- `revokeSession(...)`: đảm bảo session thuộc current user rồi revoke.
- `revokeAllSessions(...)`: revoke tất cả session user.
- `onVerificationFailed(...)`: cập nhật failed attempts và lockout.
- `selectMethod(...)`: kiểm tra đăng ký thực tế cho từng method trước khi chọn.
- `extractBearerToken(...)`, `validateBearerClaims(...)`: chuẩn hóa validate token + blacklist + session active.
- `resolveCurrentUser(...)`: lấy user từ bearer.
- `resolveDomainForEmail(...)`: tách domain từ email và JIT create domain nếu chưa có.
- `toUserMfaMethod(...)`: map `AuthMethod` sang enum MFA của User.
- `generateWebAuthnChallenge(...)`, `encodeWebAuthnChallenge(...)`, `decodeWebAuthnChallenge(...)`: quản lý challenge bytes và fallback tương thích cũ.

### 2.3 `AuthTransactionService`
- `create(...)`: tạo transaction với TTL, method, attempts.
- `find(...)`: tìm transaction theo id.
- `save(...)`: cập nhật state transaction.
- `delete(...)`: xóa transaction sau khi xong/hết hạn.

---

## 3) Module `otp`

### 3.1 `OtpRestController`
- `send(...)`: gửi OTP theo sender + destination.
- `verify(...)`: hỗ trợ verify theo `destination+otp` (mới) hoặc `sessionId+otp` (legacy).
- `resolveRegistrationSender(...)`: chuẩn hóa sender mặc định cho endpoint đăng ký.

### 3.2 `OtpService`
- `send(type, destination)`:
  - tải cấu hình sender,
  - sinh OTP (`OtpGenerator`),
  - chặn gửi quá thường xuyên (`validateFrequentSending`),
  - gửi qua `OtpSender`,
  - set số lần thử,
  - JIT provisioning domain/user phục vụ dashboard IAM,
  - lưu `SentOtp`, trả metadata session.
- `validateFrequentSending(...)`: enforce cooldown resend theo config.
- `createMessage(...)`: thay template `${otp}` bằng mã thật.
- `verifyByDestination(destination, otp)`: tìm OTP hợp lệ mới nhất theo destination+otp, kiểm tra TTL/attempts, sai thì trừ attempts, đúng thì xóa OTP để chống reuse.
- `verify(sessionId, otp)`: luồng legacy theo sessionId (không xóa record khi verify đúng như luồng mới).

### 3.3 sender/generator/config
- `OtpGenerator.generateSentOTP(...)`: tạo OTP record (mã, sessionId, TTL, timestamp).
- `OtpGenerator.generateOtpCode(...)`: sinh mã ngẫu nhiên theo policy chữ/số.
- `DummyOtpSender.sendOTP(...)`: sender giả lập (log).
- `EmailOtpSender.sendOTP(...)`: gửi qua email provider.
- `TwilioOtpSender.init()/sendOTP(...)`: khởi tạo + gửi SMS qua Twilio.
- `OtpConfiguration.getSetting(...)`: lấy OTP setting theo id.
- `OtpSettings.getOtpSender(...)`: resolve bean sender theo tên bean cấu hình.

---

## 4) Module `totp`

### 4.1 `TotpRestController`
- `register(...)`: đăng ký key TOTP cho username.
- `verify(...)`: verify mã TOTP cho username.

### 4.2 `TotpService`
- `register(username)`:
  - tìm/tạo bản ghi `RegisteredTotp`,
  - sinh secret mới,
  - JIT provisioning domain/user nếu cần,
  - lưu secret,
  - trả URI `otpauth://...` để app authenticator quét.
- `generateKey()`: sinh secret Base32 dựa theo thuật toán generator.
- `restoreKey(keyStr)`: decode Base32 thành `SecretKey`.
- `verify(username, totp)`: lấy secret user, sinh mã hiện tại và so khớp.

### 4.3 QR
- `QrService.generateQr(...)`: render QR base64 từ otpauth URI.

---

## 5) Module `webauthn`

### 5.1 `WebAuthnController`
Hỗ trợ **2 nhóm API**:
1) Legacy (`/register/challenge`, `/register/credential`, `/login/challenge`, `/login/credential`) dùng session HTTP.
2) Begin/Finish mới (`/register/begin|finish`, `/login/begin|finish`) dùng `transactionId` lưu Redis.

Hàm chính:
- `challenge(...)`: tạo credential creation options cho đăng ký.
- `registerCredential(...)`: verify credential đăng ký và lưu authenticator.
- `credentialRequest(...)`, `credentialAnonRequest(...)`: tạo assertion options.
- `assertCredential(...)`: verify assertion cho login.
- `registerBegin(...)`: tạo transaction đăng ký + options.
- `registerFinish(...)`: verify đăng ký với challenge lưu ở ceremony state.
- `loginBegin(...)`: tạo transaction đăng nhập + options.
- `loginFinish(...)`: verify assertion và kết thúc transaction.
- `normalizeUsername(...)`: chuẩn hóa username.
- `resolveAuthenticatorAttachment(...)`: map query value -> `AuthenticatorAttachment`.
- `resolveUserVerification(...)`: map query value -> `UserVerificationRequirement`.

### 5.2 `WebAuthnRegistrationService`
- `requestCredentials(...)`: tạo `PublicKeyCredentialCreationOptions` với rp/user/challenge/algorithms/selection criteria.
- `processCredentials(...)`: parse + validate attestation response, trả `CredentialRecord` để lưu DB.

### 5.3 `WebAuthnLoginService`
- `requestCredentials(...)`: tạo `PublicKeyCredentialRequestOptions`, include allowCredentials nếu user có passkey.
- `processCredentials(...)`:
  - parse assertion,
  - tìm credential tương ứng,
  - validate WebAuthn data/signature,
  - kiểm tra counter (phát hiện replay/cloned authenticator khi counter giảm),
  - cập nhật counter mới vào DB.

### 5.4 `WebAuthnCeremonyService`
- `generateChallenge()`: sinh challenge ngẫu nhiên.
- `create(...)`: tạo state ceremony (type/username/challenge/TTL) và lưu Redis.
- `findByType(...)`: tìm transaction + kiểm tra đúng loại ceremony.
- `delete(...)`: xóa ceremony state.
- `decodeChallenge(...)`: decode challenge base64url.
- `save(...)`, `find(...)`, `redisKey(...)`: helper lưu/đọc state với TTL, tự dọn state hỏng/hết hạn.

### 5.5 repository lớp WebAuthn
- `UserAuthenticatorRDBMSRepository.save/load/updateCounter`: persist credential, load theo username, update sign counter.
- `AuthenticatorEntity.fromCredentialRecord/toCredentialRecord`: chuyển đổi giữa entity DB và credential domain.
- `UserAuthenticatorInMemoryRepository.save/load`: repository in-memory (test/dev).

---

## 6) Module `oauth2`

### 6.1 `OAuth2Controller`
- `authorize(...)` (GET): chuẩn OAuth2 redirect flow.
- `authorizeJson(...)` (POST JSON): trả code/state dưới dạng JSON cho client không muốn follow redirect trực tiếp.
- `token(...)`: cấp token cho `authorization_code` hoặc `refresh_token`.
- `introspect(...)`: kiểm tra trạng thái token.
- `revoke(...)`: thu hồi token.
- `userInfo(...)`: trả claims người dùng từ access token.
- `openIdConfiguration(...)`: trả OIDC discovery metadata.
- `jwks(...)`: trả public key JWKS.
- `resolveIssuer(...)`, `parseQuery(...)`: helper dựng issuer và parse query redirect.

### 6.2 `OAuth2AuthorizationService`
- `authorize(...)`:
  - validate response_type/client/redirect/scope,
  - bắt buộc PKCE nếu client yêu cầu,
  - validate bearer access token của user,
  - kiểm tra blacklist + session active,
  - tạo và lưu authorization code (TTL 10 phút),
  - trả redirect URI chứa code (+state).
- `validateRedirectUri(...)`: redirect URI phải nằm trong allowlist client.
- `validateScope(...)`: scope yêu cầu phải thuộc allowlist client.
- `extractBearerToken(...)`: chuẩn hóa header.
- `generateCode()`: sinh authorization code random.
- `urlEncode(...)`: encode query param.
- `resolveAuthMethodFromClaims(...)`: lấy auth method từ session để lưu vào auth code metadata.

### 6.3 `OAuth2TokenService`
- `token(...)`: dispatcher theo grant type.
- `handleAuthorizationCodeGrant(...)`:
  - validate code/client/redirect,
  - verify PKCE,
  - mark auth code used,
  - tạo IdP session,
  - issue access+refresh token và id_token.
- `handleRefreshTokenGrant(...)`: validate client (nếu có), rotate refresh token, trả token mới.
- `validateClient(...)`: hỗ trợ cả confidential client và public client (PKCE + không cần secret).
- `validatePkce(...)`: hỗ trợ `plain` và `S256`.
- `s256(...)`: hash verifier theo RFC.

### 6.4 `OAuth2TokenManagementService`
- `introspect(...)`: validate client, introspect access/refresh theo hint hoặc fallback tự động.
- `revoke(...)`: revoke access/refresh theo hint hoặc thử cả hai theo RFC7009.
- `introspectAccessToken(...)`: verify JWT + DB token record + blacklist + session active.
- `introspectRefreshToken(...)`: verify hash token record + expiry + session.
- `revokeAccessToken(...)`: blacklist `jti` + mark record revoked.
- `revokeRefreshToken(...)`: mark refresh token hashed record revoked.
- `validateClient(...)`: xác thực client tương tự token endpoint policy.

### 6.5 `OAuth2ClientManagementService`
- `listClients()/getClient(...)`: truy vấn client cho admin.
- `createClient(...)`: sanitize input, generate `client_id`/`client_secret`, hash secret, resolve domain, lưu.
- `updateClient(...)`: cập nhật có chọn lọc (name, redirect URIs, scopes, grants, PKCE, active, domain).
- `rotateSecret(...)`: cấp secret mới và hash lưu DB.
- `activateClient()/deactivateClient()/deleteClient(...)`: quản trị vòng đời client.
- helper quan trọng:
  - `findClient(...)`: parse UUID + load.
  - `resolveDomain(...)`: load/create domain.
  - `normalizeClientId(...)`: tạo client_id duy nhất từ tên.
  - `sanitizeUris()/validateUri(...)`: lọc URI hợp lệ (absolute http/https).
  - `sanitizeScopes()/sanitizeGrantTypes(...)`: chuẩn hóa và gán default.

### 6.6 `OAuth2UserInfoService`
- `userInfo(...)`: validate bearer token + session + blacklist, trả claim `sub/email/name/...`.
- `extractBearerToken(...)`: helper parse header.

### 6.7 `SessionService`
- `createSession(...)`: tạo session với TTL + fingerprint + auth level.
- `isSessionActive(...)`: ưu tiên Redis cache, fallback DB.
- `findActiveSession(...)`, `findActiveSessionsByUser(...)`: truy vấn session active.
- `revokeSession(...)`: revoke 1 session + revoke token theo session + xóa cache.
- `revokeAllUserSessions(...)`: revoke hàng loạt + xóa cache.
- `cacheSession(...)`, `redisKey(...)`, `fingerprint(...)`: helper cache và định danh thiết bị.

---

## 7) Module `token`

### 7.1 `TokenController`
- `refresh(...)`: API refresh token cho luồng `/token/refresh`.

### 7.2 `JwtTokenService`
- `issueAccessToken(...)`: phát JWT access token chứa issuer/sub/client_id/scope/jti/sid.
- `issueIdToken(...)`: phát ID token OIDC cho client.
- `getAccessTokenLifetimeSeconds()`: đọc TTL access token từ config.
- `validateAccessToken(...)`: parse + verify chữ ký RSA + exp + issuer.
- `getJwks()`: xuất RSA public key ở định dạng JWKS.
- `sign(...)`: ký JWT bằng RS256.
- `getKeyPair()/buildDeterministicKeyPair()`: sinh/lưu keypair từ `signingSecret` (deterministic trong môi trường hiện tại).
- `generateOpaqueRefreshToken()`: sinh chuỗi refresh token ngẫu nhiên.
- `stripUnsigned(...)`: loại byte dấu khi encode modulus/exponent.

### 7.3 `RefreshTokenService`
- `refresh(...)`: verify refresh token hash, check expiry/session/client, revoke token cũ, issue cặp token mới (rotation).
- `issueInitialTokenPair(...)`: tạo access token + refresh token cho đăng nhập thành công.
- `getRefreshTokenLifetimeSeconds()`: TTL refresh token.
- `hash(...)`: SHA-256 refresh token trước khi lưu DB.

### 7.4 `TokenBlacklistService`
- `blacklist(...)`: lưu `jti` vào blacklist tới thời điểm hết hạn.
- `isBlacklisted(...)`: kiểm tra token đã bị thu hồi chưa.
- `cleanupExpired(...)`: dọn bản ghi blacklist hết hạn.

---

## 8) Module `apps` (App registry + API key)

### 8.1 `AppRegistrationController`
- `registerApp(...)`: đăng ký app, cấp API key.
- `listApps()`: danh sách app.
- `getApp(id)`: chi tiết app.
- `deactivateApp(id)`, `activateApp(id)`: bật/tắt app.
- `deleteApp(id)`: xóa app.
- `regenerateApiKey(id)`: cấp API key mới.

### 8.2 `AppRegistrationService`
- `registerApp(...)`: kiểm tra trùng tên, sinh key + hash, lưu app với default rate limit.
- `findById/findByName/listAllApps/getActiveApps`: truy vấn app.
- `validateApiKey(...)`: so khớp API key với hash, cập nhật `lastUsedAt`.
- `getAppByApiKey(...)`: trả app tương ứng key hợp lệ.
- `deactivateApp/activateApp/deleteApp/regenerateApiKey`: quản trị vòng đời app.
- `generateApiKey()`: random key prefix `pk_...`.

### 8.3 `ApiKeyAuthenticationFilter`
- `doFilterInternal(...)`:
  - bỏ qua `/apps/v1` và non-API endpoint,
  - yêu cầu `X-API-Key` cho `/otp/v1` và `/totp/v1`,
  - validate key, enforce rate limit,
  - ghi audit thành công/thất bại,
  - set authentication vào SecurityContext.
- `isApiEndpoint(...)`: chỉ OTP/TOTP endpoint cần API key.
- `getClientIpAddress(...)`: lấy IP thật ưu tiên `X-Forwarded-For`.

### 8.4 `RateLimitService`
- `allowRequest(app)`: consume token từ bucket phút + giờ.
- `createMinuteBucket(...)`, `createHourBucket(...)`: tạo policy bucket4j.
- `resetBuckets(appId)`: reset bucket app.

### 8.5 `AuditLogService`
- async log:
  - `logAuthenticationAttempt(...)`
  - `logApiRequest(...)`
  - `logRateLimitExceeded(...)`
- truy vấn:
  - `getAuditLogs(...)`
  - `getAuditLogsByApp(...)`
  - `getAuditLogsByEventType(...)`
  - `getAuditLogsInRange(...)`
  - `countRecentRequests(...)`

---

## 9) Module `admin`

### 9.1 `AdminUserController`
Nhóm hàm chính:
- User CRUD: `createUser`, `listUsers`, `getUserDetail`, `updateUser`, `suspendUser`, `activateUser`, `deleteUser`.
- Auth registration summary: `getAuthRegistrations`, `setPreferredMfa`.
- TOTP key ops: `getTotpKeys`, `deleteTotpKey`.
- Passkey ops: `getPasskeys`, `deletePasskey`.
- OTP sessions: `getOtpSessions`.
- IdP sessions: `getUserSessions`, `revokeUserSession`, `revokeAllUserSessions`.
- Helper mapping/resolution: `toUserSummary`, `toUserDetail`, `resolveDomainForEmail`, `parsePreferredMfaMethod`.

Logic tổng thể: tập trung cho quản trị dữ liệu người dùng và khóa xác thực, không thay đổi core auth flow; có xử lý xóa phụ thuộc (authorization_code/token/session/totp/passkey) để tránh vi phạm khóa ngoại.

### 9.2 `AdminOAuthClientController`
- `listClients/getClient/createClient/updateClient/rotateSecret/activateClient/deactivateClient/deleteClient`:
  wrapper cho `OAuth2ClientManagementService` dành cho dashboard admin.

### 9.3 `AdminDashboardController`
- `getStats(...)`: tổng hợp số liệu users/sessions/tokens/clients cho dashboard.

### 9.4 `AdminPageController`
- `adminIndex()`: trả trang admin UI.

---

## 10) Module `iam` và model/repository

### 10.1 Entity chính
- `User`:
  - lifecycle: `onCreate`, `onUpdate`.
  - trạng thái: `isLocked()`, `isActive()`.
- `Domain`:
  - lifecycle: `onCreate`, `onUpdate`.

### 10.2 Repository
- `UserRepository`, `DomainRepository`: truy vấn user/domain cho toàn hệ thống.
- Các repository khác (`TokenRepository`, `SessionRepository`, `AuthorizationCodeRepository`, `RegisteredTotpRepository`, `SentOtpRepository`, `UserAuthenticatorJPARepository`, ...): persistence chuyên biệt cho từng module.

---

## 11) Module cấu hình & exception

### 11.1 Cấu hình
- `OpenApiConfiguration.customOpenAPI()`: cấu hình OpenAPI metadata + security scheme.
- `JacksonConfiguration.objectMapper()`: cấu hình ObjectMapper dùng chung.
- `SecurityConfiguration.filterChain(...)`: bật filter chain cho apps API key.
- `AppSecurityConfiguration.passwordEncoder()/secureRandom()`: bean bảo mật cơ bản.
- `WebAuthnConfiguration`, `TokenProperties`, `AuthProperties`, `OtpConfiguration`, `TotpConfiguration`: map config từ `application.yml`.

### 11.2 Exception handling
- `ControllerAdvice`:
  - `handleNotFoundException(...)`
  - `handleSendOtpException(...)` (2 overload)
  - `handleCommonException(...)`

Mục đích: chuẩn hóa HTTP error response giữa các module.

---

## 12) Luồng logic end-to-end chính

1. **Đăng nhập Passwordless**
   - `/auth/login` tạo auth transaction + challenge theo method.
   - `/auth/mfa/verify` verify method, tạo session, issue access/refresh token.

2. **OAuth2 Authorization Code + PKCE**
   - `/oauth2/authorize` xác thực token người dùng, tạo authorization code.
   - `/oauth2/token` đổi code -> access/refresh/id token.
   - `/oauth2/introspect` và `/oauth2/revoke` quản trị vòng đời token.

3. **Session & Token Security**
   - Session active được kiểm tra qua Redis + DB.
   - Access token có `jti` để blacklist khi logout/revoke.
   - Refresh token lưu dạng hash và dùng rotation.

4. **API key cho app tích hợp**
   - OTP/TOTP endpoint được chặn bằng `ApiKeyAuthenticationFilter`.
   - Mỗi app có rate limit riêng (phút/giờ) + audit log.

---

## 13) Ghi chú về phạm vi tài liệu

- Tài liệu tập trung vào **logic nghiệp vụ và hàm thực thi chính** trong service/controller/repository adapter.
- Các DTO/entity getter/setter do Lombok hoặc hàm ánh xạ đơn giản không liệt kê chi tiết từng dòng, nhưng đã nêu vai trò trong từng module.
