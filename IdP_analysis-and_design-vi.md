# PHAN TICH VA THIET KE HE THONG
## Xay dung he thong xac thuc khong mat khau cho ung dung Web va Mobile theo mo hinh Identity Provider (IdP)

## Tom tat
Tai lieu nay trinh bay phan tich va thiet ke he thong xac thuc khong mat khau theo mo hinh IdP tap trung, phuc vu cho ung dung web va mobile. He thong tich hop nhieu co che xac thuc gom WebAuthn/FIDO2, TOTP, OTP va cau truc cap phat token theo OAuth2/OpenID Connect. Muc tieu cua nghien cuu la nang cao muc do an toan, giam rui ro tan cong lien quan den mat khau truyen thong, dong thoi bao dam tinh kha dung, kha mo rong va kha tich hop cho he sinh thai nhieu ung dung. Ban thiet ke duoc xay dung tren nen tang Java Spring Boot, MySQL, Redis va mo hinh trien khai container hoa qua Docker + Nginx.

## Tu khoa
Xac thuc khong mat khau, Identity Provider, WebAuthn, FIDO2, TOTP, OTP, OAuth2, OpenID Connect, MFA, quan ly phien.

---

## 1. Gioi thieu
### 1.1 Boi canh va dong luc nghien cuu
Trong cac he thong so hien dai, danh tinh la diem vao bao mat quan trong nhat. Co che dang nhap bang mat khau du da ton tai lau nam nhung bo lo nhieu han che mang tinh cau truc: nguoi dung dat mat khau yeu, tai su dung mat khau, de bi phishing, va ton chi phi van hanh lon cho quy trinh quen mat khau/khoi phuc tai khoan. Dong thoi, xu huong ung dung da nen tang (web, mobile, API) yeu cau mot mo hinh xac thuc thong nhat, tieu chuan va de mo rong.

Mo hinh IdP tap trung giai quyet bai toan nay bang cach tach chuc nang xac thuc khoi tung ung dung nghiep vu. Cac ung dung khach khong can tu xay dung he thong dang nhap, ma uy quyen cho IdP xu ly xac thuc va cap phat token. Cach tiep can khong mat khau tiep tuc nang cap mo hinh nay khi giam phu thuoc vao bi mat chia se (shared secret), tang su dung bang chung so huu thiet bi (passkey) va ma xac thuc mot lan.

### 1.2 Han che cua he thong dua tren mat khau
He thong truyen thong dua tren mat khau gap cac van de chinh:
- Bi mat dung chung: mat khau co the bi danh cap, tai su dung va replay.
- De bi phishing: nguoi dung co the bi lua nhap thong tin vao trang gia mao.
- Credential stuffing: khi ro ri co so du lieu, tai khoan bi tan cong hang loat.
- Chi phi van hanh cao: reset mat khau, lockout, ho tro nguoi dung.
- Trai nghiem kem: nguoi dung kho nho, de dat mat khau don gian.
- Khong dong nhat bao mat: moi ung dung tu trien khai mot kieu.

### 1.3 Muc tieu va pham vi nghien cuu
Muc tieu:
1. Thiet ke IdP tap trung ho tro xac thuc khong mat khau cho web va mobile.
2. Tich hop nhieu phuong thuc xac thuc va co che chon phuong thuc theo chinh sach.
3. Cung cap giao dien OAuth2/OIDC cho cap phat token va lien ket danh tinh.
4. Dam bao quan tri phien, token, audit va van hanh he thong.
5. De xuat lo trinh cai tien bao mat va kha nang mo rong.

Pham vi:
- Bao gom: kien truc, co che xac thuc, mo hinh du lieu, API, bao mat, DevOps.
- Khong bao gom: trien khai day du SAML enterprise va SDK mobile hoan chinh.

---

## 2. Tong quan he thong
### 2.1 Mo ta he thong IdP tap trung
He thong duoc thiet ke la mot nen tang xac thuc trung tam, cung cap:
- Dang ky nguoi dung tu phuc vu.
- Dang nhap khong mat khau voi OTP, TOTP, WebAuthn.
- Dang ky va kich hoat yeu to MFA.
- OAuth2 Authorization Code + PKCE.
- OIDC UserInfo, Discovery, JWKS.
- Quan ly phien dang nhap va thu hoi phien.
- Quan tri ung dung khach va ghi nhat ky audit.

### 2.2 Cac ben lien quan
1. Nguoi dung cuoi
- Tao tai khoan, dang nhap, quan ly MFA va phien.

2. Quan tri vien
- Quan ly user, credential, OAuth client, session va dashboard.

3. Ung dung khach
- Uy quyen xac thuc cho IdP.
- Nhan authorization code, doi token va su dung claim OIDC.

### 2.3 Luong nghiep vu cap cao
1. Tao tai khoan nguoi dung.
2. Bat dau dang nhap va tao giao dich xac thuc tam thoi.
3. Thuc hien challenge theo phuong thuc da chon.
4. Xac minh thanh cong, tao phien va cap token.
5. Uy quyen OAuth2 cho ung dung khach.
6. Doi authorization code lay access/refresh/id token.
7. Thu hoi phien hoac token khi can.

---

## 3. Kien truc he thong
### 3.1 Kieu kien truc
He thong su dung mo hinh IdP tap trung theo kien truc phan lop:
- Lop trinh bay: cong IdP va cong Admin.
- Lop API/Controller: endpoint cho auth, oauth2, totp, otp, webauthn, admin.
- Lop Service: dieu pho luong xac thuc, token, session, rate-limit, audit.
- Lop du lieu: JPA/MySQL va Redis cho trang thai tam thoi.

Thuc te hien tai la modular monolith (don khoi phan module), phu hop cho giai doan nghien cuu-trien khai nhanh, de quan tri giao dich va de kiem thu.

### 3.2 Kien truc logic
#### 3.2.1 Presentation layer
- /idp: dang ky, dang nhap, MFA enrollment, OAuth2 authorize, session control.
- /admin: quan tri user va OAuth client.

#### 3.2.2 Application layer
Cac dich vu cot loi:
- AuthOrchestratorService.
- WebAuthnRegistrationService, WebAuthnLoginService.
- TotpService, OtpService.
- OAuth2AuthorizationService, OAuth2TokenService, OAuth2TokenManagementService.
- SessionService, RefreshTokenService, JwtTokenService, TokenBlacklistService.
- AppRegistrationService, RateLimitService, AuditLogService.

#### 3.2.3 Data layer
- MySQL luu du lieu ben vung: user, domain, credential, token, session, audit.
- Redis luu auth transaction (ngan han) va active-session cache.

### 3.3 Kien truc vat ly (trien khai)
Mo hinh trien khai container:
- Nginx reverse proxy + TLS.
- Service IdP Spring Boot.
- MySQL.
- Redis.
- MailHog/SMTP va provider SMS (neu cau hinh).

Mo hinh nay co the trien khai tren on-premise hoac cloud VM/managed services.

### 3.4 Tuong tac thanh phan
- Web/Mobile -> IdP qua HTTPS.
- IdP -> MySQL/Redis cho xu ly danh tinh va phien.
- IdP -> Email/SMS sender cho OTP.
- Client app -> OAuth2/OIDC endpoint de uy quyen va lay token.

---

## 4. Thiet ke co che xac thuc
### 4.1 WebAuthn/FIDO2
#### 4.1.1 Luong dang ky passkey
1. Client yeu cau challenge dang ky.
2. IdP tra PublicKeyCredentialCreationOptions.
3. Authenticator tao credential va ky du lieu.
4. Client gui attestation ve IdP.
5. IdP xac thuc va luu credential.

#### 4.1.2 Luong dang nhap passkey
1. Client yeu cau challenge dang nhap.
2. IdP tra PublicKeyCredentialRequestOptions.
3. Nguoi dung xac thuc sinh trach hoc/bao mat thiet bi.
4. Client gui assertion ve IdP.
5. IdP xac minh chu ky va counter.

#### 4.1.3 Tinh chat bao mat
- Chong phishing manh nho origin binding.
- Khong truyen mat khau.
- Counter ho tro phat hien replay/cloned authenticator.

### 4.2 TOTP
#### 4.2.1 Dang ky
- Tao secret, sinh URI otpauth va QR code.
- Nguoi dung quet bang app authenticator.

#### 4.2.2 Xac minh
- Nguoi dung nhap ma TOTP theo khung thoi gian.
- Server tinh toan va so sanh ma ky vong.

#### 4.2.3 Luu y
- Secret TOTP can duoc ma hoa khi luu tru de dat muc bao mat san xuat.

### 4.3 OTP (Email/SMS)
#### 4.3.1 Gui ma
- Sinh OTP theo cau hinh do dai, TTL.
- Gui qua kenh email/SMS.
- Gioi han resend va so lan thu de chong abuse.

#### 4.3.2 Xac minh
- Ho tro verify theo destination+otp (uu tien) hoac sessionId+otp (legacy).
- OTP thanh cong se bi xoa de tranh tai su dung.

### 4.4 Sinh trac hoc tren mobile
Trong mo hinh de xuat, sinh trac hoc duoc dai dien boi passkey/platform authenticator:
- Du lieu sinh trac hoc khong roi khoi thiet bi.
- IdP chi nhan bang chung ma hoa da ky.
- Dam bao quyen rieng tu va giam rui ro luu tru du lieu nhay cam.

### 4.5 Tich hop da phuong thuc va chien luoc fallback
Thu tu uu tien de xuat:
1. WebAuthn (neu da dang ky).
2. TOTP.
3. OTP email/SMS cho khoi tao hoac khoi phuc.

### 4.6 Thiet ke MFA
He thong ho tro:
- Co mfaEnabled o user.
- Co preferredMfaMethod.
- Kich hoat method co kiem tra tien dieu kien enrollment.
- Auth level trong session de phan lop muc do dam bao.

---

## 5. Thiet ke co so du lieu
### 5.1 Mo hinh schema quan he
He thong su dung schema quan he xoay quanh user-domain va cac thuc the xac thuc/uy quyen.

### 5.2 Bang du lieu chinh
- domains
- users
- webauthn_authenticators
- registered_totps
- sent_otp
- oauth_clients
- authorization_codes
- oauth_tokens
- user_sessions
- token_blacklist
- registered_apps
- audit_logs

### 5.3 Khoa chinh, khoa ngoai va quan he
- 1 domain - N users.
- 1 user - N credentials/sessions/tokens/auth codes.
- oauth_tokens lien ket session qua session_id de revoke dong bo.

### 5.4 Can nhac bao mat du lieu
#### 5.4.1 Hashing
- API key: BCrypt.
- OAuth client secret: BCrypt.
- Refresh token: SHA-256 hash truoc khi luu.

#### 5.4.2 Ma hoa
- TOTP secret can bo sung ma hoa at-rest.

#### 5.4.3 Quan ly khoa
- JWT signing can nang cap sang KMS/HSM, co rotation va key versioning.

---

## 6. Phan tich use case
### 6.1 Tac nhan
- End User.
- Admin.
- Client Application.

### 6.2 So do use case tong quat (mo ta)
- End User: Dang ky, Dang nhap, Dang ky MFA, Quan ly phien.
- Client App: Authorize, Token exchange, UserInfo, Revoke/Introspect.
- Admin: Quan tri user, credential, session, OAuth client, dashboard.

### 6.3 Use case chi tiet
#### UC-01: Dang ky nguoi dung khong mat khau
Tien dieu kien: email chua ton tai.
Ket qua: tao user va domain mapping.

#### UC-02: Dang nhap bang WebAuthn
Tien dieu kien: da co passkey.
Ket qua: tao session + cap token.

#### UC-03: Dang nhap bang OTP/TOTP
Tien dieu kien: co destination hoac co secret TOTP.
Ket qua: xac thuc thanh cong va cap token.

#### UC-04: Device binding
Tien dieu kien: nguoi dung da xac thuc.
Ket qua: luu passkey moi va co the dat lam phuong thuc uu tien.

#### UC-05: Cap phat token OAuth2/OIDC
Tien dieu kien: user da xac thuc va authorize client.
Ket qua: access/refresh/id token.

#### UC-06: Dang xuat va quan ly phien
Tien dieu kien: phien dang hoat dong.
Ket qua: phien va token bi thu hoi.

---

## 7. Thiet ke API
### 7.1 Cau truc endpoint
- /auth/*
- /oauth2/*
- /.well-known/*
- /webauthn/v1/*
- /otp/v1/*
- /totp/v1/*
- /token/refresh
- /admin/api/*
- /apps/v1/*

### 7.2 Nhom endpoint xac thuc
- register, login, verify, me, logout, sessions.
- mfa enrollment/activation cho TOTP, WebAuthn, Email OTP.

### 7.3 Nhom endpoint uy quyen
- /oauth2/authorize
- /oauth2/token
- /oauth2/introspect
- /oauth2/revoke
- /oauth2/userinfo
- /.well-known/openid-configuration
- /.well-known/jwks.json

### 7.4 Dinh dang request/response
- JSON cho da so endpoint auth.
- Form-urlencoded cho token/introspect/revoke theo OAuth2.
- Can chuan hoa naming convention (snake_case/camelCase) de giam sai khac hop dong API.

### 7.5 Co che bao mat API
- API key filter cho OTP/TOTP server-to-server.
- Rate limiting theo tung app dang ky.
- Validation request DTO.
- Audit log cho su kien xac thuc va goi API.

---

## 8. Phan tich bao mat
### 8.1 Mo hinh de doa
- Phishing.
- Replay attack.
- Credential stuffing.
- MITM.

### 8.2 Co che phong ve
#### 8.2.1 WebAuthn public-key
- Loai bo chia se mat khau.
- Origin-bound challenge response.

#### 8.2.2 Bao mat token
- JWT RS256.
- Refresh token rotation.
- Blacklist theo jti + kiem tra session active.

#### 8.2.3 Bao mat kenh truyen
- HTTPS/TLS qua reverse proxy.
- Forwarded header de bao toan context host/scheme.

### 8.3 So sanh voi xac thuc bang mat khau
Mo hinh IdP khong mat khau co uu the ro rang ve chong phishing, kha nang federation, va quan tri session. Tuy nhien, fallback channel (OTP) va key management van la diem can hardening de dat muc san xuat cao.

---

## 9. Cong nghe va ly do lua chon
### 9.1 Stack cong nghe
- Backend: Java 17, Spring Boot 3.x.
- Security/Auth: Spring Security, WebAuthn4J, Nimbus JOSE JWT.
- Data: MySQL, Redis.
- Deploy: Docker Compose, Nginx.
- CI/SAST: GitHub Actions, CodeQL.

### 9.2 Chuan xac thuc/uy quyen
- FIDO2/WebAuthn.
- OAuth2 Authorization Code + PKCE.
- OpenID Connect (Discovery, JWKS, UserInfo, ID Token).

### 9.3 Ly do thiet ke
- Tieu chuan mo, de lien thong.
- De mo rong da nen tang.
- Dam bao audit va governance tap trung.

---

## 10. Trien khai va DevOps
### 10.1 Quy trinh CI/CD
Hien trang:
- Build workflow cho compile/package.
- CodeQL cho phan tich bao mat tinh.

De xuat nang cap:
- Bat buoc test stage.
- Quet lo hong dependency/container.
- Ky artifact va promotion dev-staging-prod.

### 10.2 Containerization
He thong da san sang trien khai qua Docker voi cac service tach biet: app, mysql, redis, nginx, mailhog.

### 10.3 Kha nang mo rong va san sang cao
- App co the scale ngang.
- Can MySQL/Redis HA cho production.
- Can giai phap session/challenge phan tan cho WebAuthn khi chay da node.

---

## 11. Danh gia va huong phat trien
### 11.1 Uu diem
- Ho tro nhieu phuong thuc xac thuc khong mat khau.
- Kien truc IdP tap trung de quan tri va audit.
- Tich hop tieu chuan OAuth2/OIDC.
- Co san co che revoke session/token.

### 11.2 Han che
- Can tang cuong phan quyen endpoint admin.
- Can ma hoa secret TOTP o muc ung dung.
- Can nang cap quan ly khoa ky JWT.
- Can chuan hoa API contract.

### 11.3 De xuat cai tien va nghien cuu tiep
1. RBAC/ABAC day du cho endpoint nhay cam.
2. Adaptive authentication dua tren rui ro.
3. KMS/HSM cho key lifecycle.
4. DAST/fuzzing/protocol testing trong pipeline.
5. Mo rong huong dan mobile-native passkey.
6. Nghien cuu dinh luong ve can bang bao mat-trai nghiem.

---

## Ket luan
Mo hinh IdP tap trung ket hop xac thuc khong mat khau la huong di phu hop cho he thong web/mobile hien dai. Thiet ke hien tai da dat nen tang quan trong: WebAuthn, OTP/TOTP, OAuth2/OIDC, session governance va deployment thuc te. Neu bo sung hardening ve phan quyen, key management va bao ve secret, he thong co the dat muc san xuat cao va dong vai tro mo hinh tham chieu co gia tri hoc thuat.
