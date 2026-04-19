# Diagram-Rich System Analysis and Design
## Building a Passwordless Authentication System for Web and Mobile Applications Based on an Identity Provider Model

## 1. Purpose of This Version
This version complements the main analysis by emphasizing architecture and workflow visualization. It is intended for thesis chapters, seminar presentations, and design-defense sessions where visual traceability between requirements, components, and security controls is required.

## 2. System Context and High-Level Architecture
### 2.1 Context Diagram
```mermaid
flowchart LR
    U[End User] --> WA[Web Application]
    U --> MA[Mobile Application]
    WA -->|OAuth2/OIDC| IDP[Centralized IdP Platform]
    MA -->|OAuth2/OIDC + PKCE| IDP

    subgraph IDP[Identity Provider]
      APIGW[REST Controllers]
      AUTH[Auth Orchestrator]
      MFA[MFA Engines: WebAuthn, TOTP, OTP]
      OAUTH[OAuth2/OIDC Services]
      SESS[Session Service]
      TOK[Token Services]
      ADMIN[Admin Services]
      AUDIT[Audit Service]
    end

    IDP --> DB[(MySQL)]
    IDP --> REDIS[(Redis)]
    IDP --> SMTP[Email Provider]
    IDP --> SMS[SMS Provider]

    NGINX[Nginx TLS Reverse Proxy] --> IDP
```

### 2.2 Layered Logical Architecture
```mermaid
flowchart TB
  subgraph L1[Presentation Layer]
    P1[IdP Portal /idp]
    P2[Admin Portal /admin]
    P3[External Client Apps]
  end

  subgraph L2[Application Layer]
    C1[AuthController]
    C2[OAuth2Controller]
    C3[WebAuthnController]
    C4[OtpRestController]
    C5[TotpRestController]
    C6[AdminUserController]
    C7[AdminOAuthClientController]
  end

  subgraph L3[Domain and Security Services]
    S1[AuthOrchestratorService]
    S2[OAuth2AuthorizationService]
    S3[OAuth2TokenService]
    S4[SessionService]
    S5[RefreshTokenService]
    S6[JwtTokenService]
    S7[RateLimitService]
    S8[AuditLogService]
  end

  subgraph L4[Persistence Layer]
    R1[(MySQL: users, sessions, oauth, factors)]
    R2[(Redis: auth tx, active session cache)]
  end

  L1 --> L2 --> L3 --> L4
```

### 2.3 Trust Boundary Diagram
```mermaid
flowchart LR
  subgraph B1[Untrusted Zone]
    BROWSER[Browser]
    MOBILE[Mobile App]
    CLIENTBE[Client Backend]
  end

  subgraph B2[DMZ / Edge]
    NGINX[Nginx + TLS Termination]
  end

  subgraph B3[Trusted App Zone]
    APP[IdP Spring Boot Service]
  end

  subgraph B4[Data Zone]
    SQL[(MySQL)]
    CACHE[(Redis)]
  end

  BROWSER -->|HTTPS| NGINX
  MOBILE -->|HTTPS| NGINX
  CLIENTBE -->|HTTPS| NGINX
  NGINX -->|Internal Network| APP
  APP --> SQL
  APP --> CACHE
```

## 3. Authentication Design Diagrams
### 3.1 User Registration and TOTP Enrollment
```mermaid
sequenceDiagram
    autonumber
    participant U as End User
    participant IDP as IdP Portal/API
    participant A as Auth Orchestrator
    participant DB as MySQL
    participant T as TOTP Service

    U->>IDP: POST /auth/register
    IDP->>A: register(request)
    A->>DB: create user + domain mapping
    DB-->>A: userId
    A-->>IDP: registration response
    IDP-->>U: account created

    U->>IDP: POST /auth/mfa/totp/register (Bearer)
    IDP->>A: registerTotp()
    A->>T: generate secret + URI + QR
    T->>DB: save registered_totps
    T-->>A: uri, qr
    A-->>IDP: enrollment material
    IDP-->>U: display QR
```

### 3.2 Login via WebAuthn (Passkey-First)
```mermaid
sequenceDiagram
    autonumber
    participant U as End User
    participant IDP as IdP Portal/API
    participant O as Auth Orchestrator
    participant W as WebAuthn Service
    participant DB as MySQL
    participant RS as Redis

    U->>IDP: POST /auth/login (identifier, preferred=WEBAUTHN)
    IDP->>O: login()
    O->>RS: create auth transaction
    O->>W: request assertion challenge
    W-->>O: challenge
    O-->>IDP: authTxId + challenge
    IDP-->>U: browser WebAuthn prompt

    U->>IDP: POST /auth/mfa/verify (assertion)
    IDP->>O: verify()
    O->>W: validate assertion + counter
    W->>DB: update authenticator counter
    O->>DB: create user session + token records
    O-->>IDP: accessToken + refreshToken + sessionId
    IDP-->>U: authenticated
```

### 3.3 Login via OTP or TOTP
```mermaid
sequenceDiagram
    autonumber
    participant U as End User
    participant IDP as IdP Portal/API
    participant O as Auth Orchestrator
    participant OTP as OTP Service
    participant TOTP as TOTP Service
    participant DB as MySQL
    participant RS as Redis

    U->>IDP: POST /auth/login
    IDP->>O: login()
    O->>RS: create auth transaction

    alt selectedMethod=OTP
      O->>OTP: send email OTP
      OTP->>DB: save sent_otp
      OTP-->>O: destination + attempts
    else selectedMethod=TOTP
      O->>TOTP: expect local authenticator code
    end

    U->>IDP: POST /auth/mfa/verify
    IDP->>O: verify(authTxId, code)
    O->>RS: load and validate tx state
    O->>DB: verify factor, create session, issue tokens
    O-->>IDP: auth success payload
    IDP-->>U: login success
```

## 4. OAuth2 and OIDC Design Diagrams
### 4.1 Authorization Code Flow with PKCE
```mermaid
sequenceDiagram
    autonumber
    participant U as End User
    participant C as Client Application
    participant I as IdP
    participant DB as MySQL

    U->>C: Open protected resource
    C->>I: /oauth2/authorize (client_id, redirect_uri, code_challenge, state)
    I->>I: Validate user bearer token/session
    I->>DB: Persist authorization_codes (short-lived)
    I-->>C: Redirect with code + state

    C->>I: POST /oauth2/token (code, code_verifier, client credentials)
    I->>I: Validate code + redirect_uri + PKCE
    I->>DB: Mark code used, create session and oauth_tokens
    I-->>C: access_token + refresh_token + id_token

    C->>I: GET /oauth2/userinfo (Bearer access_token)
    I->>I: Validate JWT + blacklist + session activity
    I-->>C: OIDC claims
```

### 4.2 Refresh and Revocation Flow
```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant I as IdP
    participant DB as MySQL
    participant R as Redis

    C->>I: POST /token/refresh (refreshToken)
    I->>DB: lookup hashed refresh token
    I->>I: validate client/session binding
    I->>DB: rotate refresh token + issue new access token
    I-->>C: new token pair

    C->>I: POST /oauth2/revoke
    I->>DB: revoke token record
    I->>DB: add jti to token_blacklist
    I-->>C: revocation success

    C->>I: GET /oauth2/userinfo (old token)
    I->>DB: blacklist/session check fails
    I-->>C: inactive/unauthorized
```

## 5. Data Model Diagram
### 5.1 Core ER Diagram
```mermaid
erDiagram
    DOMAINS ||--o{ USERS : contains
    DOMAINS ||--o{ OAUTH_CLIENTS : owns
    DOMAINS ||--o{ REGISTERED_APPS : governs

    USERS ||--o{ WEBAUTHN_AUTHENTICATORS : has
    USERS ||--o{ REGISTERED_TOTPS : has
    USERS ||--o{ SENT_OTP : receives
    USERS ||--o{ USER_SESSIONS : opens
    USERS ||--o{ OAUTH_TOKENS : owns
    USERS ||--o{ AUTHORIZATION_CODES : authorizes
    USERS ||--o{ AUDIT_LOGS : performs

    OAUTH_CLIENTS ||--o{ AUTHORIZATION_CODES : issues_for
    USER_SESSIONS ||--o{ OAUTH_TOKENS : binds

    TOKEN_BLACKLIST {
      string id
      string jti
      string subject
      datetime expires_at
      datetime revoked_at
      string reason
    }

    DOMAINS {
      string id
      string domain_name
      string owner_email
      boolean require_mfa
      boolean sso_enabled
    }

    USERS {
      string id
      string email
      string domain_id
      string status
      boolean mfa_enabled
      string preferred_mfa_method
      int failed_login_attempts
      datetime locked_until
    }

    USER_SESSIONS {
      string id
      string session_id
      string user_id
      string auth_method
      int auth_level
      boolean revoked
      datetime expires_at
    }

    OAUTH_TOKENS {
      string id
      string user_id
      string token_type
      string token_value
      string client_id
      string session_id
      boolean revoked
      datetime expires_at
    }
```

## 6. Security Control Mapping Diagram
```mermaid
flowchart TB
    A[Phishing] --> C1[WebAuthn origin-bound assertions]
    B[Replay attacks] --> C2[One-time auth codes + OTP deletion]
    B --> C3[WebAuthn signature counter validation]
    D[Token theft] --> C4[JTI blacklist + session active check]
    E[Abuse/flooding] --> C5[Rate limiting per registered app]
    F[Bruteforce] --> C6[Failed-attempt lockout policy]
    G[Transport interception] --> C7[TLS via Nginx + HTTPS redirect]
    H[Unauthorized client access] --> C8[PKCE + client secret validation]
```

## 7. Deployment Diagram
```mermaid
flowchart LR
    Internet --> RP[Nginx Reverse Proxy]
    RP --> IDP[IdP Spring Boot Service]
    IDP --> MYSQL[(MySQL)]
    IDP --> REDIS[(Redis)]
    IDP --> MAIL[SMTP / MailHog]
    IDP --> SMS[SMS Gateway]

    subgraph CI[CI/CD]
      GH[GitHub Actions Build]
      CQ[CodeQL Static Analysis]
    end

    GH --> IDP
    CQ --> IDP
```

## 8. Design Interpretation and Architecture Decisions
### 8.1 Why Centralized IdP
- Reduces duplicated authentication logic in client applications.
- Provides uniform policy enforcement and auditability.
- Simplifies standards-based federation through OAuth2/OIDC.

### 8.2 Why Multi-Method Passwordless
- WebAuthn offers highest resistance to phishing and replay.
- TOTP offers robust offline fallback.
- OTP offers universal compatibility for account bootstrap and recovery.

### 8.3 Why Session and Token Dual Control
- Session table enables governance and device-level revocation.
- Token records and blacklist enable immediate cryptographic invalidation paths.

## 9. Recommended Enhancements (Diagram-Driven)
### 9.1 Authorization Boundary Hardening
- Add strict RBAC around all admin and app-governance endpoints.
- Introduce policy-engine decisions at controller/service entry.

### 9.2 Cryptographic Key Lifecycle
- Replace local deterministic signing key derivation with KMS/HSM-backed key management.
- Add key rotation timeline and multi-key JWKS rollover.

### 9.3 High-Availability WebAuthn Session Strategy
- Move challenge/session state to distributed session store or enforce sticky sessions.
- Add explicit multi-node challenge validation tests.

### 9.4 Secret Protection
- Encrypt TOTP secrets at application level using envelope encryption.
- Add key-id metadata for transparent re-encryption migration.

## 10. Suggested Thesis Usage
- Use this file for architecture and sequence figures.
- Use the strict thesis version for narrative chapters and formal structure.
- Use the Vietnamese version for localized academic submission.
