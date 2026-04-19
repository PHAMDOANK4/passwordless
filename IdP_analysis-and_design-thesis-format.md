# THESIS FORMAT REPORT
## SYSTEM ANALYSIS AND DESIGN OF A PASSWORDLESS IDENTITY PROVIDER
### Extended Version with Research Question Alignment and Evaluation Criteria

---

## Abstract
This report presents a thesis-formatted system analysis and design of a centralized passwordless Identity Provider (IdP) for web and mobile ecosystems. The analyzed implementation combines WebAuthn/FIDO2, TOTP, OTP, OAuth2 Authorization Code + PKCE, OpenID Connect metadata endpoints, session lifecycle governance, and app-level API-key controls. The architecture is implemented as a modular monolith on Spring Boot with MySQL, Redis, Docker, and Nginx.

This second version is structured for academic traceability with:
1. strict chapter numbering,
2. explicit research questions (RQs),
3. direct RQ-to-design mapping,
4. formal evaluation criteria and measurable indicators,
5. evidence-based discussion of strengths, limitations, and future work.

The study concludes that the platform already demonstrates a strong practical baseline for passwordless federation and identity governance, while key hardening priorities remain in endpoint authorization policy, key lifecycle management, and distributed runtime controls.

## Keywords
Passwordless authentication, Identity Provider, WebAuthn, FIDO2, OAuth2, OpenID Connect, PKCE, session security, token lifecycle, IAM.

---

## Chapter 1. Introduction

### 1.1 Background
Identity systems are foundational to modern digital platforms, but password-dependent models continue to suffer from structural weaknesses: phishing susceptibility, credential reuse, account takeover automation, and high operational overhead for reset/recovery flows. As applications increasingly span browser and mobile channels, decentralized authentication logic across multiple products creates inconsistent security and governance quality.

A centralized IdP model addresses this by moving authentication and token issuance to a dedicated trust service. Passwordless methods further reduce shared-secret exposure by leveraging public-key cryptography (WebAuthn), time-bound one-time codes (TOTP/OTP), and policy-controlled session/token governance.

### 1.2 Problem Statement
This thesis addresses the following problem:

How can a practical centralized IdP be designed and implemented to provide secure passwordless authentication and interoperable authorization for web/mobile applications while maintaining operational feasibility and extensibility?

### 1.3 Research Objectives
The study objectives are:
1. Design a centralized passwordless authentication architecture for heterogeneous clients.
2. Integrate multiple authentication methods with robust method orchestration.
3. Provide standards-aligned delegated authorization and identity claims delivery.
4. Design secure token and session lifecycle controls.
5. Define and apply evaluation criteria that measure technical and security readiness.

### 1.4 Scope and Boundaries
In scope:
- system architecture, module decomposition, API surface,
- authentication and authorization protocol design,
- data and state model (MySQL + Redis),
- deployment and CI posture,
- security and quality evaluation.

Out of scope:
- complete native mobile SDK implementation,
- external enterprise federation connectors (for example SAML brokerage),
- full compliance-program mapping (for example SOC2 control matrix).

### 1.5 Thesis Contributions
This report contributes:
1. A full implementation-grounded architecture review of the current codebase.
2. A formal RQ-traceable design analysis.
3. A measurable evaluation framework with acceptance criteria.
4. A prioritized hardening roadmap for production maturation.

---

## Chapter 2. Research Questions and Methodology

### 2.1 Research Questions
RQ1. How should a centralized passwordless IdP be architected to support web and mobile clients with consistent trust boundaries?

RQ2. How can WebAuthn, TOTP, and OTP be orchestrated in one authentication workflow without compromising security or recoverability?

RQ3. How should OAuth2/OIDC, session state, and token lifecycle be designed to provide revocation-aware delegated authorization?

RQ4. Which security controls are implemented effectively in the current system, and where are the highest-priority residual risks?

RQ5. Which evaluation criteria and observable metrics can be used to assess technical readiness and guide next-stage hardening?

### 2.2 Methodology
The methodology is implementation-driven architectural analysis:
1. Source-level review of controllers, services, repositories, and models.
2. Runtime and infrastructure review of configuration and container topology.
3. API and flow analysis across user, admin, and app-governance domains.
4. Security control inspection at protocol, application, and operational layers.
5. Validation review using available tests and recent execution outcomes.

### 2.3 Evidence Sources
Primary evidence sources include:
- core backend modules under `src/main/java/org/openidentityplatform/passwordless/**`,
- static frontends under `src/main/resources/static/idp` and `src/main/resources/static/admin`,
- runtime files (`application.yml`, `docker-compose.yaml`, `nginx/nginx.conf`, `Dockerfile`),
- database changelog (`src/main/resources/db/changelog/db.changelog-master.xml`),
- CI definitions (`.github/workflows/build.yml`, `.github/workflows/codeql-analysis.yml`),
- test configuration and executed suite outcomes.

### 2.4 RQ Traceability Strategy
Each major chapter explicitly states how findings map to RQ1-RQ5. A consolidated traceability matrix is provided in Chapter 8.

---

## Chapter 3. System Requirements and Context Analysis

### 3.1 Stakeholder Analysis
1. End users:
- self-register,
- authenticate with passwordless methods,
- manage sessions and MFA preferences.

2. Administrators:
- govern users, factors, sessions, OAuth clients,
- inspect dashboard and audit views,
- perform revocation/remediation actions.

3. Relying applications:
- request authorization,
- exchange codes for tokens,
- introspect or revoke tokens.

4. Platform operators:
- deploy and maintain runtime,
- monitor health and security.

### 3.2 Functional Requirements (FR)
FR-1. User registration and profile management.
FR-2. Login initiation with method selection.
FR-3. OTP/TOTP/WebAuthn verification.
FR-4. MFA enrollment and activation.
FR-5. OAuth2 authorization code + PKCE flow.
FR-6. Access, refresh, and ID token issuance.
FR-7. Session listing and revocation.
FR-8. Admin governance for users and OAuth clients.
FR-9. App registration with API-key lifecycle.
FR-10. Audit and rate-limit telemetry.

### 3.3 Non-Functional Requirements (NFR)
NFR-1 Security: phishing resistance, replay protection, revocation capability.
NFR-2 Interoperability: OAuth2/OIDC and WebAuthn compatibility.
NFR-3 Availability: practical multi-service deployment with state durability.
NFR-4 Maintainability: modular package decomposition and testability.
NFR-5 Operability: containerized runtime and CI baseline.

### 3.4 Constraints
1. Modular monolith architecture retained for implementation coherence.
2. Existing endpoint contracts and legacy compatibility endpoints preserved.
3. Current runtime defaults prioritize local/development convenience.

RQ mapping: RQ1, RQ2.

---

## Chapter 4. Architecture and Design Model

### 4.1 Architectural Style
The system is implemented as a layered modular monolith:
- presentation and static web UI,
- REST controller/API layer,
- orchestration and domain services,
- JPA persistence plus Redis state/cache.

This style favors rapid evolution and transactional consistency while remaining deployable as a single service behind a reverse proxy.

### 4.2 Layered Component Decomposition

1. Presentation layer:
- End-user IdP portal at `/idp`.
- Admin portal at `/admin`.

2. Controller layer:
- Auth (`/auth`), WebAuthn (`/webauthn/v1`), OAuth2 (`/oauth2`), OTP/TOTP, Token refresh, Apps, Admin.

3. Service layer:
- `AuthOrchestratorService`,
- WebAuthn registration/login/ceremony services,
- OAuth2 authorization/token/management services,
- session and token lifecycle services,
- app registration, rate limiting, audit services.

4. Data/state layer:
- MySQL durable entities,
- Redis ephemeral ceremony/session state.

### 4.3 Runtime Topology
Default compose topology:
- `nginx` TLS termination and reverse proxy,
- `passwordless-service` application,
- `mysql` durable store,
- `redis` state/cache with AOF,
- `mailhog` development mail sink.

Nginx uses Docker DNS runtime resolution and forwards `X-Forwarded-*` headers; app consumes forwarded headers through framework strategy.

### 4.4 Trust Boundaries
Boundary B1: Internet/browser to reverse proxy.
Boundary B2: reverse proxy to IdP application.
Boundary B3: IdP service to data plane (MySQL/Redis).
Boundary B4: IdP to external channels (SMTP/SMS providers).

RQ mapping: RQ1, RQ4.

---

## Chapter 5. Protocol and Workflow Design

### 5.1 Authentication Orchestration Model
`/auth/login` creates an auth transaction with TTL, selected method, attempt tracking, and context metadata. Verification is completed through `/auth/mfa/verify` where factor-specific proof is validated.

Method selection priority (automatic mode):
1. WebAuthn (if passkeys exist),
2. TOTP (if enrolled),
3. OTP (if destination available).

### 5.2 WebAuthn/FIDO2 Workflow Design

#### 5.2.1 Legacy Compatibility Path
- challenge and credential endpoints are preserved for backward compatibility.

#### 5.2.2 Production Ceremony Path (USB Security Key Included)
Registration:
1. `POST /webauthn/v1/register/begin`
2. challenge generation and Redis ceremony persistence,
3. client creates credential via WebAuthn API,
4. `POST /register/finish` verifies attestation and persists authenticator.

Authentication:
1. `POST /webauthn/v1/login/begin`
2. challenge issuance + ceremony state,
3. client returns assertion,
4. `POST /login/finish` validates assertion and counter behavior.

Counter rule:
- decreased counter is treated as security anomaly,
- equal counter is tolerated for some platform authenticators,
- increased counter is expected path.

### 5.3 OTP and TOTP Workflow Design
OTP:
- sender-specific configuration,
- resend throttling and attempt limits,
- destination-based verify path,
- successful verification invalidates OTP.

TOTP:
- secret generation,
- otpauth URI + QR generation,
- timestep-based verification.

### 5.4 OAuth2/OIDC Workflow Design

Authorization (`/oauth2/authorize`):
- validates client, redirect URI, scopes,
- enforces PKCE when required,
- requires valid user bearer token and active session,
- issues one-time authorization code.

Token (`/oauth2/token`):
- supports `authorization_code` and `refresh_token` grants,
- validates PKCE verifier and code usage semantics,
- creates/reuses session context and issues token set.

OIDC endpoints:
- discovery metadata,
- JWKS publication,
- userinfo claims endpoint.

### 5.5 Session and Token Lifecycle Design
Session:
- created after successful verification,
- persisted in MySQL and cached active in Redis,
- revocable singly or globally.

Access tokens:
- RS256 JWT with `jti` and optional `sid`.

Refresh tokens:
- opaque, hashed at rest,
- rotated on refresh.

Revocation:
- JTI blacklist + DB revocation fields + session active checks.

RQ mapping: RQ2, RQ3, RQ4.

---

## Chapter 6. Data and State Design

### 6.1 Durable Data Model (MySQL)
Entity families:
1. Identity:
- `domains`, `users`.

2. Factors:
- `webauthn_authenticators`, `registered_totp`, `sent_otp`.

3. Authorization and session:
- `oauth_clients`, `authorization_codes`, `oauth_tokens`, `user_sessions`.

4. Security and governance:
- `token_blacklist`, `registered_apps`, `audit_logs`.

### 6.2 Ephemeral State Model (Redis)
1. WebAuthn ceremony keys:
- `webauthn:ceremony:{transactionId}` with TTL.

2. Session activeness keys:
- `session:active:{sessionId}` with TTL to session expiry.

### 6.3 Referential Integrity and Deletion Semantics
Admin deletion path removes dependent authorization/session/factor records before principal deletion to avoid FK conflicts.

### 6.4 Migration Governance Observation
Liquibase changelog exists but runtime currently uses JPA auto-update with Liquibase disabled in default config. This is suitable for development velocity but should be replaced with strict migration governance for production.

RQ mapping: RQ1, RQ3.

---

## Chapter 7. Security Control Analysis

### 7.1 Implemented Security Controls
1. WebAuthn challenge-response + authenticator counter validation.
2. OTP resend throttling and attempt controls.
3. Account lockout policy.
4. Refresh token hashing and rotation.
5. Access token JTI blacklist checks.
6. Session-active verification for token operations.
7. API-key filter and per-app rate limiting for OTP/TOTP APIs.
8. Audit logging of authentication and API events.
9. TLS reverse proxy model with forwarded header handling.

### 7.2 Risk and Gap Analysis

Critical gaps:
1. Broad `permitAll` route policy in global security chain leaves admin and sensitive endpoints underprotected.
2. Role-based authorization boundaries are not strongly enforced centrally.

High-priority gaps:
3. TOTP secret protection requires encryption-at-rest.
4. JWT key lifecycle lacks managed rotation and externalized key custody.
5. Rate limiting is process-local and not globally consistent in multi-node setups.

Medium-priority gaps:
6. JIT provisioning in OTP/TOTP flows may create implicit identities without explicit governance checks.
7. API error schema consistency across modules can be improved.

### 7.3 Threat Mapping
- Phishing: strongly reduced with WebAuthn, weaker in OTP fallback channels.
- Replay: mitigated via OTP invalidation, authorization code single-use, counter checks, token blacklist.
- Credential stuffing: reduced by passwordless model.
- Token theft: reduced by revocation and session binding, still requires client-side storage hardening.
- Privilege abuse: primary exposure tied to permissive endpoint authorization.

RQ mapping: RQ4.

---

## Chapter 8. Evaluation Framework, Criteria, and RQ Alignment

### 8.1 Evaluation Design
Evaluation is criteria-based and evidence-driven. Each criterion includes:
1. definition,
2. measurement approach,
3. target threshold,
4. observed evidence/status.

### 8.2 Evaluation Criteria

#### C1. Authentication Breadth and Orchestration
Definition: support and orchestration quality across WebAuthn, TOTP, OTP.
Measurement: endpoint and service-path verification, method selection rules.
Target: all three methods available with deterministic fallback order.
Status: achieved.

#### C2. WebAuthn Ceremony Correctness
Definition: explicit challenge lifecycle for begin/finish ceremonies.
Measurement: challenge generation, state TTL, type-safe transaction validation.
Target: begin/finish for register and login with expiry and replay-aware checks.
Status: achieved.

#### C3. OAuth2/OIDC Interoperability
Definition: standards-aligned delegated authorization and discovery support.
Measurement: presence and behavior of authorize/token/introspect/revoke/userinfo/discovery/JWKS.
Target: full authorization code + PKCE and OIDC metadata surface.
Status: achieved.

#### C4. Session and Token Governance
Definition: revocation-aware lifecycle controls.
Measurement: session revocation, blacklist checks, refresh rotation.
Target: single and bulk revocation, JTI blacklist, rotated refresh tokens.
Status: achieved.

#### C5. Security Boundary Enforcement
Definition: endpoint authorization and least-privilege control quality.
Measurement: security filter chain policy review.
Target: privileged endpoints require explicit authorization roles.
Status: not yet achieved (priority gap).

#### C6. Secret and Key Management Maturity
Definition: protection level for TOTP secrets and JWT signing keys.
Measurement: storage model and key lifecycle design.
Target: encrypted secrets and managed key rotation.
Status: partially achieved.

#### C7. Operability and Deployability
Definition: practical reproducible deployment model.
Measurement: compose topology, reverse proxy TLS, CI jobs.
Target: one-command local deployment and CI build/static scan baseline.
Status: achieved.

#### C8. Verification and Test Coverage
Definition: ability to validate critical behaviors through automated tests.
Measurement: focused and regression test outcomes.
Target: core auth/OAuth/admin/WebAuthn flows covered and green.
Status: achieved (recent full suite reported 86 tests, 0 failures).

### 8.3 RQ-to-Criteria Alignment Matrix

| Research Question | Primary Criteria | Alignment Summary |
|---|---|---|
| RQ1 (centralized architecture) | C1, C3, C7 | Architecture and standards integration are practically realized with deployable topology. |
| RQ2 (multi-method orchestration) | C1, C2 | Multi-method flow is implemented with explicit USB-key capable ceremony model. |
| RQ3 (token/session lifecycle) | C3, C4 | OAuth2/OIDC and revocation-aware session/token controls are implemented and observable. |
| RQ4 (control effectiveness and risk) | C5, C6 | Strong controls exist, but endpoint authorization and key/secret maturity remain top gaps. |
| RQ5 (evaluation readiness) | C7, C8 | Practical criteria and measurable evidence can be applied and tracked for maturation. |

### 8.4 Overall Evaluation Outcome
The system meets most functional and interoperability criteria and demonstrates strong protocol-level implementation depth. The principal readiness blockers are authorization hardening and cryptographic governance maturity.

RQ mapping: RQ1-RQ5.

---

## Chapter 9. Discussion, Limitations, and Threats to Validity

### 9.1 Discussion
The platform is technically advanced for a centralized passwordless IdP implementation and already supports realistic end-user and relying-party workflows. The modular monolith approach remains appropriate at this stage, given the need for coherent transactional and policy behavior.

### 9.2 Limitations
1. Security policy layer is currently too permissive for sensitive endpoints.
2. TOTP and key governance hardening is incomplete.
3. Rate limiting does not yet provide distributed consistency.
4. Migration governance in runtime config is not production-strict by default.

### 9.3 Threats to Validity
1. Findings are implementation-time snapshots and may change with new commits.
2. Performance benchmarking was not the primary evaluation axis in this study.
3. Some operational assumptions are inferred from current configuration defaults rather than external production telemetry.

---

## Chapter 10. Conclusion and Future Work

### 10.1 Conclusion
This thesis confirms that a centralized passwordless IdP architecture is both feasible and effective for modern web/mobile ecosystems when grounded in standards and stateful governance. The implementation already provides:
1. multi-method passwordless authentication,
2. OAuth2/OIDC delegated authorization,
3. revocation-aware session/token lifecycle,
4. practical deployment and operational baseline.

### 10.2 Prioritized Future Work
Phase 1 (immediate hardening):
1. enforce explicit authorization policies for privileged endpoints,
2. add role-based protections for admin and governance APIs,
3. encrypt TOTP secrets,
4. standardize API error contracts.

Phase 2 (security maturity):
1. externalize JWT key lifecycle to KMS/HSM,
2. implement distributed rate limiting,
3. add adaptive risk and step-up logic.

Phase 3 (platform evolution):
1. formal API versioning and compatibility policy,
2. SIEM-oriented audit export,
3. optional external federation bridge patterns.

---

## Appendix A. Structured Endpoint Catalog

### A.1 Auth
- POST `/auth/register`
- POST `/auth/login`
- POST `/auth/mfa/verify`
- POST `/auth/mfa/totp/register`
- POST `/auth/mfa/totp/activate`
- POST `/auth/mfa/webauthn/activate`
- POST `/auth/mfa/email/activate`
- GET `/auth/me`
- POST `/auth/logout`
- GET `/auth/sessions`
- POST `/auth/sessions/{sessionId}/revoke`
- POST `/auth/sessions/revoke-all`

### A.2 WebAuthn
- GET `/webauthn/v1/register/challenge/{username}`
- POST `/webauthn/v1/register/credential`
- GET `/webauthn/v1/login/challenge/{username}`
- GET `/webauthn/v1/login/challenge/`
- POST `/webauthn/v1/login/credential`
- POST `/webauthn/v1/register/begin`
- POST `/webauthn/v1/register/finish`
- POST `/webauthn/v1/login/begin`
- POST `/webauthn/v1/login/finish`

### A.3 OAuth2/OIDC
- GET `/oauth2/authorize`
- POST `/oauth2/authorize`
- POST `/oauth2/token`
- POST `/oauth2/introspect`
- POST `/oauth2/revoke`
- GET `/oauth2/userinfo`
- GET `/oauth2/.well-known/openid-configuration`
- GET `/oauth2/jwks`
- GET `/.well-known/openid-configuration`
- GET `/.well-known/jwks.json`

### A.4 OTP, TOTP, Token
- POST `/otp/v1/register`
- POST `/otp/v1/send`
- POST `/otp/v1/verify`
- POST `/totp/v1/register`
- POST `/totp/v1/verify`
- POST `/token/refresh`

### A.5 App Governance and Admin
- `/apps/v1/**`
- `/apps/v1/audit/**`
- `/admin/api/users/**`
- `/admin/api/oauth2/clients/**`
- `/admin/api/dashboard/stats`
