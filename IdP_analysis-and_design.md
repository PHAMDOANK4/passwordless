# System Analysis and Design
## Building a Passwordless Authentication System for Web and Mobile Applications Based on an Identity Provider Model

## Abstract
This document presents a comprehensive system analysis and design for a centralized Identity Provider (IdP) that enables passwordless authentication for web and mobile applications. The analyzed system adopts a multi-method authentication strategy based on WebAuthn/FIDO2, TOTP, and OTP mechanisms, and integrates OAuth2 and OpenID Connect (OIDC) for token-based federation with client applications. The design objective is to improve security posture, usability, and interoperability compared with traditional password-based architectures. The system is implemented using a layered service model with Spring Boot, MySQL, Redis, and containerized deployment through Docker and Nginx. This document covers architecture, data model, use cases, APIs, threat model, technology rationale, deployment strategy, and future research directions suitable for scientific reporting.

## 1. Introduction
### 1.1 Background and Motivation of Passwordless Authentication
Digital systems increasingly operate in hostile and large-scale environments where identity is a primary attack surface. Conventional username and password authentication has demonstrated structural weaknesses, including poor user password hygiene, credential reuse, phishing susceptibility, and high operational overhead from password recovery workflows. At the same time, mobile-first access patterns and cross-platform digital services require a stronger, more seamless identity architecture that can serve browser, native mobile, and API clients consistently.

A centralized IdP model addresses these demands by separating authentication concerns from business applications. Instead of embedding login logic in each client system, applications delegate identity verification to an IdP that enforces standardized flows and policy controls. Passwordless methods further strengthen this model by reducing shared secrets and introducing possession- and inherence-based factors such as FIDO2 passkeys, time-based one-time passwords, and verified OTP channels.

### 1.2 Limitations of Traditional Password-Based Systems
Traditional password systems suffer from both technical and socio-technical limitations:
- Shared-secret dependence: passwords are reusable secrets that can be stolen and replayed.
- Phishing vulnerability: users can be tricked into disclosing credentials on fraudulent interfaces.
- Credential stuffing risk: leaked password databases enable automated account takeover across services.
- Lifecycle burden: password resets, lockouts, and rotation policies increase support cost.
- User friction: memorability constraints lower usability and often reduce effective entropy.
- Poor federation consistency: each application re-implements identity logic with varying quality.

These limitations motivate a passwordless, federated IdP approach where applications trust signed identity assertions and tokens rather than handling primary credential validation locally.

### 1.3 Objectives and Scope of the Research
The research objective is to design and evaluate a centralized passwordless IdP platform for web and mobile ecosystems with the following goals:
- Provide secure user authentication without passwords.
- Support multiple passwordless methods and policy-based method selection.
- Offer standards-based token issuance via OAuth2 and OIDC.
- Enable centralized user, session, and client-application administration.
- Ensure deployability for real environments with containerization, logging, and scaling considerations.

Scope of this analysis:
- Included: IdP architecture, data model, API contracts, security controls, deployment, and DevOps.
- Included: Web portal and API-based integration for client applications.
- Included: Mobile integration model via OAuth2/OIDC and WebAuthn-compatible platform authenticators.
- Excluded: Full native mobile SDK implementation and full SSO federation to external enterprise IdPs (identified as future work).

## 2. System Overview
### 2.1 Description of the IdP-Based Authentication System
The system is a centralized authentication service that provides:
- User self-registration and profile lifecycle management.
- Passwordless login initiation and method-dependent verification.
- MFA factor enrollment and preference activation.
- OAuth2 authorization code flow with PKCE support.
- OIDC capabilities including userinfo and discovery endpoints.
- Session visibility and revocation for users and administrators.
- Application registration with API key issuance for selected server-to-server channels.

Implemented user-facing interfaces include:
- IdP portal at /idp for end-user registration, authentication, MFA enrollment, OAuth2 authorization, and session control.
- Admin portal at /admin for operational user and OAuth client management.

### 2.2 Stakeholders
The system includes three principal stakeholder groups.

1. End Users
- Register accounts and authenticate through passwordless factors.
- Manage active sessions and preferred MFA method.
- Authorize access for client applications in OAuth2 flows.

2. Administrators
- Manage user lifecycle (create, update, suspend, activate, delete).
- Inspect and manage registered factors and sessions.
- Register and govern OAuth clients.
- Monitor dashboard and audit records.

3. Client Applications
- Web and mobile relying parties that delegate authentication to the IdP.
- Consume authorization codes and tokens for downstream access.
- Use introspection and revocation endpoints as needed.

### 2.3 High-Level System Workflow
A high-level user-centric flow is:
1. User is registered in IdP (self-service or admin).
2. User starts authentication by identifier and optional preferred method.
3. IdP chooses or validates method (OTP, TOTP, WebAuthn).
4. User completes verification challenge.
5. IdP creates a user session and issues access plus refresh tokens.
6. For delegated access, user authorizes an OAuth client and an authorization code is issued.
7. Client exchanges code for tokens (authorization_code grant).
8. User or admin can revoke one session or all sessions; token validity follows session and revocation state.

## 3. System Architecture
### 3.1 Architectural Style
The analyzed platform uses a centralized IdP model with layered service decomposition:
- Presentation layer: static web pages and JavaScript clients (IdP and Admin portals).
- API/controller layer: REST controllers for auth, OAuth2, OTP/TOTP, WebAuthn, admin, and app registration.
- Application/service layer: orchestration, token management, session management, client management, and auditing.
- Persistence layer: JPA repositories for relational entities and Redis for short-lived state.

The architecture is modular monolith with clear bounded modules rather than independently deployed microservices. This style is appropriate for rapid iteration, coherent transactional boundaries, and reduced operational complexity in research-stage systems.

### 3.2 Logical Architecture
#### 3.2.1 Presentation Layer
- IdP portal supports complete lifecycle: registration, login/MFA, MFA enrollment, OAuth2 authorization, token refresh, and session management.
- Admin portal supports user and OAuth client administration, dashboard statistics, and key/session operations.

#### 3.2.2 Application Layer
Core services include:
- Authentication orchestration service for registration, login initiation, verification, lockout handling, and session/token issuance.
- WebAuthn registration and authentication services based on challenge-response validation.
- TOTP service for secret registration and code verification.
- OTP service for message delivery, resend control, attempts control, and verification.
- OAuth2 authorization and token services (authorize, token exchange, introspection, revocation, userinfo).
- Session service with relational persistence and Redis active-session cache.
- Token services for JWT issuance, refresh token rotation, and blacklist management.
- App registration, API key validation, and rate limiting services.

#### 3.2.3 Data Layer
Persistent entities include domains, users, TOTP secrets, WebAuthn authenticators, OTP sessions, OAuth clients, authorization codes, OAuth tokens, user sessions, token blacklist entries, registered apps, and audit logs. Redis stores short-lived authentication transactions and session-active cache keys.

### 3.3 Physical Architecture
The implemented deployment model supports containerized operation using:
- Reverse proxy: Nginx with TLS termination and forwarded headers.
- Application service: Spring Boot IdP service.
- Relational database: MySQL.
- In-memory/state data: Redis.
- Development mail service: MailHog.

This can run on-premise or cloud virtual machines. Production adaptation should include managed database/cache services, external certificate management, and secure secret storage.

### 3.4 Component Interactions
#### 3.4.1 Web App and IdP
- Browser is redirected or directly invokes IdP endpoints.
- User authenticates via chosen passwordless factor.
- IdP issues session-bound tokens and OAuth2 artifacts.

#### 3.4.2 Mobile App and IdP
- Mobile client should use OAuth2 Authorization Code with PKCE and system browser or secure authorization user-agent.
- Biometrics are mediated by platform authenticators through passkey/WebAuthn-equivalent experience.
- IdP remains central trust anchor and token issuer.

#### 3.4.3 IdP and Authentication Subsystems
- OTP/TOTP/WebAuthn modules provide mechanism-specific proof validation.
- Orchestrator applies account policy, lockout, and method-selection logic.

#### 3.4.4 IdP and Datastores
- MySQL stores durable identity and authorization records.
- Redis stores ephemeral authentication transactions and active-session cache state.

#### 3.4.5 IdP and External Services
- SMTP and SMS providers (configurable senders) are used for OTP delivery.
- Reverse proxy and optional external monitoring/security tools operate at platform boundary.

## 4. Authentication Mechanisms Design
### 4.1 FIDO2/WebAuthn Flow
#### 4.1.1 Registration Flow
1. Client requests a credential creation challenge from IdP.
2. IdP returns PublicKeyCredentialCreationOptions (RP ID, challenge, user entity, algorithm parameters).
3. Client invokes authenticator (security key or platform authenticator).
4. Client returns attestation payload to IdP.
5. IdP validates attestation and stores credential record with metadata.

#### 4.1.2 Authentication Flow
1. Client requests assertion challenge.
2. IdP provides PublicKeyCredentialRequestOptions with allowed credentials when available.
3. User proves possession via authenticator signature.
4. IdP verifies assertion and signature.
5. Sign counter is validated to detect replay or cloned authenticators.
6. Counter and last-used metadata are updated on success.

#### 4.1.3 Security Properties
- Origin-bound cryptographic assertions reduce phishing risk.
- No shared secret is transmitted.
- Counter checks improve replay and clone detection.

### 4.2 TOTP-Based Authentication
#### 4.2.1 Enrollment
- IdP generates a per-user TOTP secret and otpauth URI.
- QR code is generated and displayed for authenticator app enrollment.
- User verifies with a fresh TOTP code before activation.

#### 4.2.2 Verification
- User submits current TOTP code.
- IdP computes expected value from stored secret and time step.
- Equality verification grants factor success.

#### 4.2.3 Security Properties
- Time-limited code validity reduces replay window.
- Works offline on authenticator devices after enrollment.
- Requires secure at-rest protection of TOTP secrets in server storage.

### 4.3 OTP (Email/SMS)
#### 4.3.1 Issuance
- IdP generates OTP with configurable length and validity window.
- OTP is delivered through configured sender (email/SMS).
- Attempt counters and resend interval controls are enforced.

#### 4.3.2 Verification
- User submits OTP with destination or legacy session id.
- IdP validates expiration and attempt limits.
- On success, OTP record is deleted to prevent reuse.

#### 4.3.3 Security Properties
- Suitable as fallback and broad-compatibility channel.
- Security depends on email/SMS channel integrity and anti-abuse controls.

### 4.4 Biometric Authentication on Mobile
In this architecture, mobile biometrics should be represented as platform-authenticator passkeys rather than raw biometric templates:
- Biometric sensing remains on device secure enclave/TEE.
- IdP receives only signed cryptographic assertions.
- Integration path is OAuth2/OIDC plus platform authenticator flows.

This satisfies privacy-preserving biometric authentication principles because the server does not store biometric raw data.

### 4.5 Comparison and Integration Strategy
| Method | User Experience | Phishing Resistance | Device Dependency | Operational Cost | Recommended Role |
|---|---|---|---|---|---|
| WebAuthn | Very high | Very high | Medium | Medium | Primary method |
| TOTP | High | Medium | Medium | Low | Secondary factor |
| Email OTP | Medium | Low to medium | Low | Medium | Recovery and fallback |
| SMS OTP | Medium | Low to medium | Low | Medium to high | Legacy compatibility |

Integration strategy:
- Default priority: WebAuthn when enrolled.
- Secondary: TOTP when passkey unavailable.
- Fallback: OTP for bootstrapping and account recovery.
- Method auto-selection by enrollment state with optional user override.

### 4.6 Multi-Factor Authentication Design
The platform supports MFA through:
- User-level flag for MFA enablement.
- Preferred MFA method selection and activation checks.
- Session auth level assignment based on method class.
- Domain-level policy fields for mandatory MFA (foundation for policy expansion).

Recommended policy model for production:
- Risk-based step-up MFA by context (new device, geo-anomaly, sensitive action).
- Assurance levels mapped to method strength.
- Enrollment policy requiring at least two factors for recovery resiliency.

## 5. Database Design
### 5.1 Relational Database Schema
The data model is relational with identity-centered normalization:
- Domain and user entities provide tenant-aware identity boundaries.
- Authentication factors are linked to user identity.
- OAuth artifacts and sessions are first-class entities for auditability and control.

### 5.2 Core Tables and Roles
#### 5.2.1 Users and Domain Context
- domains: organization-level identity boundary and policy metadata.
- users: principal identity, status, role, MFA preference, lockout state.

#### 5.2.2 Credentials and Authentication Factors
- webauthn_authenticators: public-key credentials, metadata, counters, backup state.
- registered_totps: per-user TOTP secret and identifier.
- sent_otp: OTP issuance records, attempts, and expiration.

#### 5.2.3 Authorization and Session Tables
- oauth_clients: relying-party registration with redirect URIs and client credentials.
- authorization_codes: short-lived authorization artifacts including PKCE and nonce.
- oauth_tokens: access/refresh token records, revocation state, client and session binding.
- user_sessions: active and revoked session records, auth method, and context.
- token_blacklist: revoked JWT identifiers for immediate invalidation.

#### 5.2.4 Operational and Governance Tables
- registered_apps: API-key-based app registrations for selected APIs.
- audit_logs: authentication and API event trail.
- magic_links: available as extensibility model for link-based flows.

### 5.3 Keys, Foreign Keys, and Relationships
Major relationships:
- One Domain to many Users.
- One Domain to many OAuth Clients and Registered Apps.
- One User to many Sessions, Tokens, Authorization Codes, OTP events, and authenticators.
- Sessions and Tokens are linked by session id for coordinated revocation.

Referential integrity implications:
- User deletion must cascade or be orchestrated by ordered dependency cleanup.
- Authorization and session artifacts should be revoked/deleted before principal removal.

### 5.4 Security Considerations in Data Layer
#### 5.4.1 Hashing and Secret Protection
- API keys are stored as BCrypt hashes.
- OAuth client secrets are stored as BCrypt hashes.
- Refresh tokens are stored as SHA-256 hashes (opaque raw token never persisted in clear form).

#### 5.4.2 Encryption and Sensitive Fields
- TOTP secret storage currently requires explicit encryption-at-rest enhancement.
- Token and authenticator metadata should be covered by database encryption and backup encryption policies.

#### 5.4.3 Key Storage and Cryptographic Management
- JWT signing currently uses RS256 key material derived from configured secret.
- For production-grade security, signing keys should be externalized to HSM/KMS, with rotation and key versioning.

#### 5.4.4 Retention and Privacy
- Audit, OTP, and token artifacts require retention windows and data minimization.
- Device and IP metadata collection should comply with jurisdictional privacy requirements.

## 6. Use Case Analysis
### 6.1 Actors
- End User: authenticates and consents to client access.
- Administrator: governs users, factors, sessions, and clients.
- Client Application: delegates authentication and consumes tokens.

### 6.2 General Use Case Diagram (Textual Description)
The generalized use case model consists of three actor groups interacting with a centralized IdP boundary:
- End User interacts with registration, login, MFA enrollment, and session management use cases.
- Client Application interacts with authorization request, token exchange, token introspection, and token revocation use cases.
- Administrator interacts with user/client governance and audit inspection use cases.

All use cases converge on shared services for authentication verification, token issuance, and policy enforcement, enabling consistent security behavior across channels.

### 6.3 Detailed Use Cases
#### 6.3.1 User Registration (Passwordless)
Preconditions:
- User is not already registered.

Main flow:
1. User submits registration profile.
2. System normalizes identity and resolves domain.
3. User record is created with default or requested MFA configuration.

Alternative flows:
- Existing email causes conflict.
- Invalid input causes validation error.

Postconditions:
- User identity exists and may proceed to enrollment/login.

#### 6.3.2 Login via WebAuthn
Preconditions:
- User has registered passkey.

Main flow:
1. Login transaction is created.
2. Challenge is issued.
3. User completes authenticator assertion.
4. Assertion and counter are validated.
5. Session and tokens are issued.

Alternative flows:
- Credential missing, invalid challenge, signature failure, replay indicator.

Postconditions:
- Authenticated session established with high assurance.

#### 6.3.3 Login via OTP or TOTP
Preconditions:
- OTP destination or TOTP enrollment exists.

Main flow:
1. Login transaction is created.
2. OTP is sent or TOTP prompt is returned.
3. User submits code.
4. System verifies code validity and attempt limits.
5. Session and tokens are issued.

Alternative flows:
- Code expired, attempts exhausted, transaction expired.

Postconditions:
- Session and token pair created on successful verification.

#### 6.3.4 Device Binding
Preconditions:
- Authenticated user context exists.

Main flow:
1. User initiates passkey registration.
2. Device authenticator creates credential.
3. IdP validates and persists credential metadata.
4. User may set passkey as preferred method.

Postconditions:
- Device-bound credential associated with user account.

#### 6.3.5 Token Issuance (JWT and OAuth2)
Preconditions:
- User authenticated and client authorization granted.

Main flow:
1. IdP issues authorization code.
2. Client exchanges code with PKCE verification.
3. IdP issues access token, refresh token, and optional ID token.
4. Token records are persisted with expiration and revocation fields.

Alternative flows:
- Invalid client, redirect mismatch, invalid verifier, expired/used code.

Postconditions:
- Client receives token set for delegated API access.

#### 6.3.6 Logout and Session Management
Preconditions:
- Active bearer token/session exists.

Main flow:
1. User or admin requests single-session or global revocation.
2. Session state is revoked.
3. Related tokens are revoked or blacklisted.
4. Active-session cache is invalidated.

Postconditions:
- Session no longer valid; dependent token usage is denied.

## 7. API Design
### 7.1 RESTful API Structure
Major API groups:
- Auth APIs: /auth/* for register, login, verify, profile, logout, sessions.
- OAuth2/OIDC APIs: /oauth2/* and /.well-known/* for authorize, token, introspect, revoke, userinfo, discovery, JWKS.
- WebAuthn APIs: /webauthn/v1/* for challenge and credential operations.
- OTP/TOTP APIs: /otp/v1/* and /totp/v1/*.
- Token refresh API: /token/refresh.
- App governance APIs: /apps/v1/* and /apps/v1/audit/*.
- Admin APIs: /admin/api/* for user, dashboard, and OAuth client management.

### 7.2 Authentication Endpoints (Register, Login, Verify, Token)
Representative endpoint families:
- Registration and login initiation: /auth/register, /auth/login.
- Factor verification and activation: /auth/mfa/verify and /auth/mfa/*.
- OAuth token exchange: /oauth2/token.
- Refresh workflow: /token/refresh.

Design characteristics:
- Distinct authentication transaction id for challenge-response tracking.
- Method-specific challenge payloads.
- Token responses containing expiry metadata and bearer type.

### 7.3 Authorization Model (OAuth2 and OIDC)
Implemented standards:
- OAuth2 Authorization Code grant.
- PKCE validation (plain and S256, S256 default).
- OIDC support via discovery, JWKS, ID token, and userinfo endpoints.

Client model:
- Confidential clients (client secret validation).
- PKCE-oriented public clients with secret-optional handling.

### 7.4 Request and Response Format
Current API style:
- JSON payloads for most IdP endpoints.
- Form-encoded payload for OAuth2 token/introspection/revocation endpoints.
- Coexistence of camelCase and snake_case response fields across endpoint families.

Recommendation for research-grade consistency:
- Standardize naming conventions per API family and provide strict schema versioning.
- Publish machine-readable OpenAPI profiles for each actor-facing API.

### 7.5 Security Mechanisms in API Layer
Implemented controls:
- API key authentication filter for OTP/TOTP server-to-server endpoints.
- Rate limiting via per-application buckets.
- Input validation through request DTOs.
- Audit logging of authentication and API events.
- Token introspection and revocation endpoints.

Recommended hardening:
- Enforce role-based authorization on all admin and app-governance endpoints.
- Adopt standardized error model and correlation ids.
- Add idempotency keys for sensitive state-changing operations.

## 8. Security Analysis
### 8.1 Threat Model
#### 8.1.1 Phishing
Threat:
- User credential theft through fake login pages.

Mitigation:
- WebAuthn origin-bound signatures significantly reduce phishing feasibility.
- OIDC redirect URI validation and state handling protect authorization flow integrity.

Residual risk:
- OTP channels remain socially engineerable; require anti-phishing UX and risk checks.

#### 8.1.2 Replay Attack
Threat:
- Reuse of captured credentials, assertions, or tokens.

Mitigation:
- One-time authorization codes with short expiry and single-use state.
- OTP deletion after successful verification.
- WebAuthn sign counter checks.
- JWT jti blacklist and session-active validation.

Residual risk:
- Session theft remains possible if token storage is compromised on client side.

#### 8.1.3 Credential Stuffing
Threat:
- Automated login attempts using leaked username/password pairs.

Mitigation:
- No password-based primary authentication in target design.
- Login transaction expiration and lockout counters for repeated failures.

Residual risk:
- Attackers may still attempt OTP flooding or account enumeration.

#### 8.1.4 Man-in-the-Middle (MITM)
Threat:
- Interception and manipulation of network traffic.

Mitigation:
- TLS termination at Nginx and forwarded security context.
- Signed JWT tokens with server-side verification.

Residual risk:
- Development self-signed certificate setup is not sufficient for production trust requirements.

### 8.2 Core Security Mechanisms
#### 8.2.1 Public-Key Cryptography in WebAuthn
- Asymmetric key pairs are generated and retained by authenticators.
- Server stores public credential material only.
- Challenge-response validation proves possession without exposing private keys.

#### 8.2.2 Token Security
- Access tokens are signed with RS256 and include issuer, audience, subject, jti, and optional sid.
- Refresh tokens use rotation and server-side hashed storage.
- Revocation combines token-record flags and JWT jti blacklist checks.

#### 8.2.3 Secure Communication
- HTTPS is enforced through reverse proxy and redirect from HTTP.
- Forwarded header strategy preserves correct issuer/origin semantics behind proxy.

### 8.3 Comparison with Password-Based Authentication
Compared with password systems, this IdP model provides:
- Stronger phishing resistance with passkeys.
- Reduced reliance on user-memorized secrets.
- Better session and token governance through centralized controls.
- Better interoperability across clients through OAuth2/OIDC.

However, secure fallback factors (OTP channels), key management, and endpoint authorization are critical for maintaining this advantage in production.

## 9. Technology Stack and Design Rationale
### 9.1 Backend, Frontend, and Mobile Integration Technologies
- Backend: Java 17, Spring Boot 3.x, Spring MVC, Spring Security, Spring Data JPA, Redis integration.
- Frontend: static HTML/CSS/JavaScript IdP and Admin portals; TypeScript-based web SDK assets.
- Data: MySQL for durable identity and authorization data; Redis for ephemeral state.
- Container runtime: Docker and Docker Compose.

### 9.2 Authentication and Authorization Standards
- FIDO2/WebAuthn for passkey-based authentication.
- OAuth2 Authorization Code with PKCE for delegated authorization.
- OIDC profile capabilities via discovery, JWKS, ID token, and userinfo endpoints.
- JWT for compact signed access assertions.

### 9.3 Design Rationale by Technology Choice
| Technology | Rationale |
|---|---|
| Spring Boot | Mature ecosystem, rapid API development, robust integration stack |
| JPA with MySQL | Strong relational consistency for identity and authorization state |
| Redis | Efficient ephemeral state and fast session-active checks |
| WebAuthn4J | Standards-compliant WebAuthn validation pipeline |
| Bucket4j | Practical per-client rate limiting for abuse mitigation |
| Nimbus JOSE JWT | Strong JWT/JWK support for OIDC-compatible token handling |
| Docker and Nginx | Reproducible deployment and clear reverse-proxy boundary |
| GitHub Actions plus CodeQL | Baseline CI and static security analysis pipeline |

### 9.4 Mobile Design Rationale
For mobile, the preferred architecture is delegated authentication via OAuth2/OIDC with PKCE and platform authenticator support. This minimizes secret handling inside mobile apps and aligns with modern OS security models for biometrics and passkeys.

## 10. Deployment and DevOps
### 10.1 CI/CD Pipeline
Current CI characteristics:
- Build workflow compiles and packages the project on push and pull request.
- CodeQL workflow performs static analysis for Java and JavaScript.

Recommended CI/CD maturation:
- Add mandatory unit and integration test stages.
- Add dependency and container vulnerability scanning.
- Add signed artifact publishing and provenance metadata.
- Add environment promotion flow (dev to staging to production).

### 10.2 Containerization and Runtime Topology
The deployment stack is containerized with:
- IdP application container.
- MySQL and Redis state services.
- Nginx reverse proxy with TLS.
- Optional MailHog for development email validation.

Benefits:
- Consistent environment reproduction.
- Simplified local and pre-production setup.
- Clear network segmentation by service role.

### 10.3 Scalability and High Availability of IdP
Horizontal scaling strategy:
- Application layer is largely stateless with token-based access.
- Shared MySQL and Redis permit multi-instance deployment.

Critical HA considerations:
- WebAuthn challenge currently depends on HTTP session context; multi-instance deployments should use shared session storage or sticky sessions.
- Database replication and backup strategy are required for resilience.
- Redis should be deployed with persistence and failover for production.

Recommended production architecture:
- Multiple IdP instances behind load balancer.
- Managed relational database with replication.
- Redis HA topology.
- Centralized logging, metrics, and alerting.

## 11. Evaluation and Future Work
### 11.1 Advantages of the System
The designed and implemented IdP demonstrates key strengths:
- Centralized identity and authorization governance.
- Support for multiple passwordless factors and method orchestration.
- Standards-based interoperability with OAuth2/OIDC and WebAuthn.
- Session-aware security model with revocation controls.
- Modular architecture with practical deployment model.

### 11.2 Limitations
Current limitations identified during analysis:
- Endpoint-level authorization policy needs stronger enforcement for admin and governance APIs.
- TOTP secret protection requires encryption-at-rest enhancement.
- JWT key management should move to external key infrastructure with rotation.
- API response format consistency can be improved across endpoint families.
- Session consistency for WebAuthn challenge flow requires HA-safe session strategy.
- Magic-link model exists in data layer but is not fully exposed as end-to-end active flow.

### 11.3 Proposed Improvements and Future Research Directions
Priority technical improvements:
1. Implement strict RBAC and policy-based access control for admin and governance endpoints.
2. Encrypt TOTP secrets and sensitive fields using envelope encryption and managed keys.
3. Introduce full key lifecycle management for JWT signing (rotation, rollover, audit).
4. Add adaptive authentication and risk scoring (device reputation, geo-velocity, anomaly models).
5. Extend mobile-native integration guidance and reference client implementations.
6. Standardize API error model and schema versioning for long-term compatibility.
7. Introduce formal security testing pipeline (DAST, fuzzing, protocol-level tests).

Research directions:
- Quantitative comparison of passkey-first vs OTP-first fallback strategies on security and usability.
- Formal verification of authentication state machines and token/session revocation correctness.
- Privacy-preserving telemetry for risk-based authentication without excessive personal data exposure.
- Cross-domain federation with external enterprise IdPs and selective trust brokering.

## Conclusion
A centralized passwordless IdP architecture is a feasible and scientifically robust approach for modern identity systems across web and mobile contexts. The analyzed platform already demonstrates essential building blocks: passwordless factors, OAuth2/OIDC integration, session governance, and operational deployment artifacts. With targeted hardening in authorization policy, key management, and secret protection, the system can evolve from an effective engineering implementation into a production-grade and research-validated identity platform.
