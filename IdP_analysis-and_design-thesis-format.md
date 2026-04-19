# THESIS FORMAT REPORT
## SYSTEM ANALYSIS AND DESIGN
### Topic: Building a Passwordless Authentication System for Web and Mobile Applications Based on an Identity Provider Model

## Abstract
This report presents the formal system analysis and design of a centralized Identity Provider (IdP) for passwordless authentication in web and mobile ecosystems. The proposed model integrates WebAuthn/FIDO2, TOTP, and OTP mechanisms and exposes OAuth2/OpenID Connect interfaces for delegated authorization and identity federation. The architecture is evaluated from functional, security, data, API, and deployment perspectives. The resulting design demonstrates improved resilience against phishing and credential stuffing, reduced identity-fragmentation across applications, and practical implementation feasibility using Java Spring Boot, MySQL, Redis, and containerized deployment.

## Keywords
Passwordless authentication, Identity Provider, WebAuthn, FIDO2, TOTP, OTP, OAuth2, OpenID Connect, MFA, session security.

---

## 1. Introduction
### 1.1 Research Background
Password-based authentication remains dominant but introduces significant security and usability burdens. Large-scale breaches repeatedly expose credential datasets, enabling account takeover through password reuse and credential stuffing. Moreover, phishing campaigns continue to exploit user dependence on shared secrets. In parallel, multi-platform application ecosystems increasingly require federated identity models and robust trust boundaries.

A centralized IdP architecture addresses these challenges by decoupling authentication from business services. Instead of each application implementing custom identity controls, authentication is provided by a dedicated trust service that enforces policy, logs security events, and issues standardized tokens for relying parties.

### 1.2 Problem Statement
Traditional password-centric systems face the following critical constraints:
- Shared-secret exposure and replay risk.
- High phishing susceptibility.
- Significant operational cost for reset, recovery, and lockout handling.
- Inconsistent security quality across client applications.
- Poor interoperability in heterogeneous web and mobile environments.

### 1.3 Research Objectives
The objectives of this study are:
1. To design a centralized passwordless IdP suitable for web and mobile client applications.
2. To integrate multiple authentication methods with policy-driven method orchestration.
3. To provide standards-based authorization and identity interfaces using OAuth2 and OIDC.
4. To design a secure and auditable session-token lifecycle model.
5. To define a deployment and DevOps strategy for practical operation.

### 1.4 Scope
Included scope:
- IdP architecture, data model, API design, security model, and deployment model.
- Passwordless method integration (WebAuthn, TOTP, OTP).
- OAuth2/OIDC token issuance and session governance.

Excluded scope:
- Full enterprise SAML federation implementation.
- Complete production mobile SDK implementation details.

---

## 2. System Overview
### 2.1 Conceptual Model
The system is designed as a centralized IdP platform serving three actor classes: end users, administrators, and client applications. End users authenticate using passwordless factors. Client applications consume OAuth2/OIDC tokens. Administrators manage users, credentials, sessions, and OAuth clients.

### 2.2 Stakeholders
#### 2.2.1 End Users
- Register user profiles.
- Authenticate without passwords.
- Enroll factors and manage active sessions.

#### 2.2.2 Administrators
- Manage user lifecycle and factor assignments.
- Operate OAuth client registry.
- Inspect dashboard metrics and audit trails.

#### 2.2.3 Client Applications
- Initiate delegated authentication.
- Exchange authorization codes for tokens.
- Validate or revoke tokens where required.

### 2.3 Operational Workflow Summary
1. User registration in IdP.
2. Login initiation with method selection (explicit or automatic).
3. Factor challenge and verification.
4. Session creation and token issuance.
5. OAuth2 authorization and code exchange for relying applications.
6. Session and token revocation by user/admin governance.

---

## 3. System Architecture
### 3.1 Architectural Pattern
The implementation follows a modular layered architecture under a centralized IdP model:
- Presentation layer: IdP and Admin web portals.
- Controller/API layer: REST endpoints for auth, OAuth2, MFA, admin, and app management.
- Service layer: orchestration, token, session, and security services.
- Persistence layer: relational entities plus Redis state caching.

This pattern provides high maintainability while preserving coherent transactional boundaries needed for identity systems.

### 3.2 Logical Architecture
#### 3.2.1 Presentation Layer
- End-user portal for full identity lifecycle.
- Admin portal for operational governance.
- External app integration endpoints.

#### 3.2.2 Application Layer
Core components:
- Auth orchestration service.
- WebAuthn registration and login services.
- OTP and TOTP services.
- OAuth2 authorization and token services.
- Session management service.
- Token refresh and blacklist services.
- Rate limiting and auditing services.

#### 3.2.3 Data Layer
Persistent stores include users, domains, authenticators, OTP records, sessions, OAuth artifacts, app registry, and audit logs. Redis is used for short-lived authentication transaction state and active session cache acceleration.

### 3.3 Physical Architecture
Deployment topology:
- Nginx reverse proxy with TLS.
- IdP application service (Spring Boot).
- MySQL for durable storage.
- Redis for ephemeral and cache state.
- SMTP/SMS providers for OTP channels.

### 3.4 Integration Architecture
- Web and mobile applications consume IdP via OAuth2/OIDC.
- Browser and native clients rely on challenge-response workflows.
- Client backends use token endpoints for delegated authorization.

---

## 4. Authentication Mechanisms Design
### 4.1 WebAuthn/FIDO2 Design
#### 4.1.1 Registration
- The IdP generates a credential creation challenge.
- The authenticator creates key material and returns attestation.
- The server validates attestation and persists credential metadata.

#### 4.1.2 Authentication
- The IdP issues assertion challenge options.
- The authenticator signs challenge using private key.
- The IdP validates signature and updates authenticator counter.

#### 4.1.3 Security Properties
- Strong phishing resistance via origin binding.
- No password transmission.
- Replay detection through counter behavior validation.

### 4.2 TOTP Design
#### 4.2.1 Enrollment
- TOTP secret is generated and represented as otpauth URI.
- QR code is rendered for authenticator-app binding.

#### 4.2.2 Verification
- User submits time-based code.
- Server computes expected code from shared secret and time-step.

### 4.3 OTP Design (Email/SMS)
#### 4.3.1 Issuance and Delivery
- OTP is generated with configurable TTL and attempts.
- Delivery occurs through configured sender channels.
- Resend-throttling and anti-flood controls are enforced.

#### 4.3.2 Verification
- Validation supports destination-based or legacy session-based mode.
- Successful verification invalidates OTP to prevent reuse.

### 4.4 Biometric Authentication for Mobile
Biometric authentication should be implemented through platform-authenticator passkeys where the biometric operation remains local to device secure hardware. The server receives only signed assertions, preserving biometric privacy and reducing sensitive data handling.

### 4.5 Method Integration and Fallback Policy
A policy-guided method hierarchy is recommended:
1. WebAuthn as primary high-assurance method.
2. TOTP as strong fallback.
3. OTP as broad compatibility and recovery channel.

### 4.6 MFA Model
The system supports user-level MFA toggles and preferred method activation. Session assurance levels can be mapped to factor strength and later extended for risk-based adaptive authentication.

---

## 5. Database Design
### 5.1 Relational Schema Overview
The schema is identity-centered and normalized around user-domain relationships, with specialized tables for factors, sessions, and OAuth artifacts.

### 5.2 Core Tables
#### 5.2.1 Identity and Organization
- domains
- users

#### 5.2.2 Authentication Factors
- webauthn_authenticators
- registered_totps
- sent_otp

#### 5.2.3 Authorization and Sessions
- oauth_clients
- authorization_codes
- oauth_tokens
- user_sessions
- token_blacklist

#### 5.2.4 Governance
- registered_apps
- audit_logs

### 5.3 Relationships and Constraints
- One domain to many users and clients.
- One user to many factors, sessions, tokens, and authorization codes.
- Session-token binding by session identifier supports coordinated revocation.

### 5.4 Data Security Design
#### 5.4.1 Hashing
- API key hashes via BCrypt.
- OAuth client secret hashes via BCrypt.
- Refresh token storage via SHA-256 hash.

#### 5.4.2 Encryption Requirements
- TOTP secrets require encryption-at-rest enhancement.
- Sensitive metadata should be protected by database encryption and key governance.

#### 5.4.3 Key Management
JWT signing should transition from local-secret-derived keys to managed KMS/HSM infrastructure with rotation and auditability.

---

## 6. Use Case Analysis
### 6.1 Actor Model
- End User
- Administrator
- Client Application

### 6.2 General Use Case Description
- End User performs registration, login, enrollment, and session control.
- Client Application performs authorization and token exchange.
- Administrator performs lifecycle governance and security operations.

### 6.3 Detailed Use Cases
#### 6.3.1 UC-01: Passwordless User Registration
Input: profile attributes.
Output: active user identity and domain linkage.

#### 6.3.2 UC-02: Login with WebAuthn
Input: identifier and assertion.
Output: authenticated session and token pair.

#### 6.3.3 UC-03: Login with OTP/TOTP
Input: identifier and one-time code.
Output: authenticated session and token pair.

#### 6.3.4 UC-04: Device Binding (Passkey Enrollment)
Input: registration challenge and attestation.
Output: persisted authenticator credential.

#### 6.3.5 UC-05: OAuth2 Token Issuance
Input: valid authorization code and PKCE verifier.
Output: access token, refresh token, optional ID token.

#### 6.3.6 UC-06: Logout and Session Revocation
Input: session revocation request.
Output: inactive session and revoked/invalidated tokens.

---

## 7. API Design
### 7.1 API Resource Structure
- /auth
- /oauth2
- /.well-known
- /webauthn/v1
- /otp/v1
- /totp/v1
- /token
- /admin/api
- /apps/v1

### 7.2 Authentication API Design
Key operations:
- register
- login
- verify
- factor activation
- profile retrieval
- logout and sessions

### 7.3 Authorization API Design
OAuth2/OIDC capabilities include:
- authorization endpoint
- token endpoint
- userinfo endpoint
- introspection endpoint
- revocation endpoint
- discovery and JWKS endpoints

### 7.4 Request and Response Semantics
The design uses JSON for most identity flows and form-encoded inputs for OAuth2 token-management endpoints. Schema consistency should be improved via stricter contract versioning.

### 7.5 API Security Controls
- API key filter for selected server-to-server channels.
- Per-app rate limiting.
- Validation and audit logging.
- Token introspection and revocation support.

---

## 8. Security Analysis
### 8.1 Threat Model
- Phishing
- Replay attacks
- Credential stuffing
- Man-in-the-middle interception

### 8.2 Implemented Controls
#### 8.2.1 WebAuthn Public-Key Authentication
Eliminates password sharing and strengthens phishing resistance.

#### 8.2.2 Token and Session Security
- Signed JWT access tokens.
- Refresh token rotation.
- Revocation with blacklist and session-active checks.

#### 8.2.3 Transport Security
- HTTPS and TLS via reverse proxy.
- Forwarded-header correctness for issuer/origin handling.

### 8.3 Comparative Assessment
The centralized passwordless IdP outperforms password-based architectures in phishing resistance, federated consistency, and session governance. Residual risk remains in fallback channels and operational key governance, requiring targeted hardening.

---

## 9. Technology Stack and Design Rationale
### 9.1 Implementation Stack
- Java 17, Spring Boot, Spring Security, Spring Data JPA.
- WebAuthn4J for FIDO2/WebAuthn validation.
- Nimbus JOSE JWT for JWT/JWKS handling.
- MySQL and Redis for persistence and state.
- Docker and Nginx for deployment and reverse proxying.

### 9.2 Standards Selection Rationale
- WebAuthn/FIDO2 for high-assurance passwordless authentication.
- OAuth2 Authorization Code + PKCE for web/mobile federation.
- OIDC for standardized identity claims exchange.

### 9.3 Trade-off Discussion
- Modular monolith favors coherence and operational simplicity.
- Distributed microservices may be considered after policy and observability maturity.

---

## 10. Deployment and DevOps
### 10.1 CI/CD Baseline
- Build workflow for compilation and packaging.
- CodeQL workflow for static security analysis.

### 10.2 Containerization Strategy
- Runtime services are isolated in Docker containers.
- Reverse proxy and backend services are network-segmented.

### 10.3 Scalability and Availability Considerations
- Horizontal application scaling with shared data stores.
- HA requirements for MySQL and Redis in production.
- Distributed challenge/session strategy required for WebAuthn in multi-node environments.

---

## 11. Evaluation and Future Work
### 11.1 Strengths
- Standards-based interoperability.
- Multi-method passwordless support.
- Centralized governance and auditing.
- Practical deployability.

### 11.2 Current Limitations
- Endpoint authorization hardening is still required for admin-sensitive APIs.
- TOTP secret encryption should be strengthened.
- JWT signing key lifecycle management should be externalized.
- API contract consistency can be improved.

### 11.3 Future Research and Engineering Directions
1. Risk-adaptive authentication and step-up policies.
2. Formal verification of authentication state transitions.
3. KMS-backed key management and cryptographic rotation frameworks.
4. Advanced security testing pipeline (DAST, protocol fuzzing, adversarial simulations).
5. Deeper mobile-native SDK and passkey lifecycle research.

---

## Conclusion
This thesis-format analysis confirms that a centralized passwordless IdP architecture is technically viable, security-enhancing, and suitable for modern web/mobile application ecosystems. The current design establishes a strong foundation with WebAuthn, OTP/TOTP, OAuth2/OIDC, and session governance. With focused hardening in authorization policy and cryptographic operations, the platform can evolve into a production-grade and academically robust reference implementation.
