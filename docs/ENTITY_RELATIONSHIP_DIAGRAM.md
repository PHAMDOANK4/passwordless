# AuthCentralA - Complete Entity Relationship Diagram

## Overview

This document describes the complete data model for AuthCentralA (centralized authentication system) that can serve multiple applications with various authentication methods.

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          AuthCentralA Data Model                        │
│                     Centralized Authentication System                    │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│     Domain       │ (Organization/Company)
├──────────────────┤
│ id (PK)          │
│ domainName *     │ e.g., "company.com"
│ displayName      │ e.g., "Company Inc."
│ ownerEmail       │
│ active           │
│ requireMfa       │
│ ssoEnabled       │
│ ssoConfig        │
│ maxUsers         │
│ customLoginUrl   │
│ logoUrl          │
└──────────────────┘
        │
        │ 1:N relationships
        ├───────────────────────────────┐
        │                               │
        ▼                               ▼
┌──────────────────┐            ┌──────────────────┐
│      User        │            │  RegisteredApp   │
├──────────────────┤            ├──────────────────┤
│ id (PK)          │            │ id (PK)          │
│ email *          │            │ name *           │
│ domain_id (FK)   │◄───┐       │ domain_id (FK)   │
│ firstName        │    │       │ apiKeyHash       │
│ lastName         │    │       │ active           │
│ displayName      │    │       │ allowedOrigins   │
│ phoneNumber      │    │       │ rateLimitPerMin  │
│ externalId       │    │       │ rateLimitPerHr   │
│ status           │    │       │ createdBy        │
│ mfaEnabled       │    │       └──────────────────┘
│ preferredMfaMethod│   │               │
│ role             │    │               │
│ profilePictureUrl│    │               ▼
│ locale           │    │       ┌──────────────────┐
│ timezone         │    │       │   AuditLog       │
│ lastLoginAt      │    │       ├──────────────────┤
│ lastLoginIp      │    │       │ id (PK)          │
│ failedLoginAtts  │    │       │ user_id (FK)     │
│ lockedUntil      │    │       │ domain_id (FK)   │
└──────────────────┘    │       │ appId            │
        │               │       │ appName          │
        │ 1:N           │       │ userEmail        │
        ├───────────────┼───────│ eventType        │
        │               │       │ endpoint         │
        ▼               │       │ httpMethod       │
┌──────────────────┐    │       │ ipAddress        │
│    Session       │    │       │ success          │
├──────────────────┤    │       │ errorMessage     │
│ id (PK)          │    │       │ details (JSON)   │
│ sessionId *      │    │       └──────────────────┘
│ user_id (FK)     │◄───┤
│ deviceInfo       │    │
│ deviceFingerprint│    │       ┌──────────────────┐
│ ipAddress        │    │       │   OAuthClient    │
│ location         │    │       ├──────────────────┤
│ createdAt        │    │       │ id (PK)          │
│ expiresAt        │    │       │ clientId *       │
│ lastActivityAt   │    │       │ clientSecret     │
│ revoked          │    │       │ clientName       │
│ authMethod       │    │       │ domain_id (FK)   │
│ authLevel        │    │       │ redirectUris     │
└──────────────────┘    │       │ allowedScopes    │
                        │       │ grantTypes       │
        ┌───────────────┤       │ active           │
        │               │       │ requirePkce      │
        ▼               │       │ accessTokenLife  │
┌──────────────────┐    │       │ refreshTokenLife │
│     Token        │    │       │ idTokenLife      │
├──────────────────┤    │       └──────────────────┘
│ id (PK)          │    │               │
│ user_id (FK)     │◄───┤               │
│ tokenType        │    │               │ 1:N
│ tokenValue       │    │               │
│ scopes           │    │               ▼
│ clientId         │    │       ┌──────────────────┐
│ createdAt        │    │       │AuthorizationCode │
│ expiresAt        │    │       ├──────────────────┤
│ revoked          │    │       │ id (PK)          │
│ deviceInfo       │    │       │ code *           │
│ ipAddress        │    │       │ user_id (FK)     │◄───┐
└──────────────────┘    │       │ clientId         │    │
                        │       │ oauthClient_id   │    │
        ┌───────────────┤       │ redirectUri      │    │
        │               │       │ scopes           │    │
        ▼               │       │ state            │    │
┌──────────────────┐    │       │ codeChallenge    │    │
│   MagicLink      │    │       │ codeChallengeMethod   │
├──────────────────┤    │       │ nonce            │    │
│ id (PK)          │    │       │ createdAt        │    │
│ token *          │    │       │ expiresAt        │    │
│ email            │    │       │ used             │    │
│ user_id (FK)     │◄───┤       │ usedAt           │    │
│ purpose          │    │       │ ipAddress        │    │
│ createdAt        │    │       │ userAgent        │    │
│ expiresAt        │    │       │ authMethod       │    │
│ used             │    │       └──────────────────┘    │
│ usedAt           │    │                               │
│ ipAddress        │    │                               │
│ userAgent        │    │                               │
│ attempts         │    │                               │
└──────────────────┘    │                               │
                        │                               │
        ┌───────────────┤                               │
        │               │                               │
        ▼               │                               │
┌──────────────────┐    │                               │
│    SentOtp       │    │                               │
├──────────────────┤    │                               │
│ sessionId (PK)   │    │                               │
│ otp              │    │                               │
│ expireTime       │    │                               │
│ destination      │    │                               │
│ lastSentAt       │    │                               │
│ attempts         │    │                               │
│ user_id (FK)     │◄───┤                               │
└──────────────────┘    │                               │
                        │                               │
        ┌───────────────┤                               │
        │               │                               │
        ▼               │                               │
┌──────────────────┐    │                               │
│ RegisteredTotp   │    │                               │
├──────────────────┤    │                               │
│ id (PK)          │    │                               │
│ username *       │    │                               │
│ secret           │    │                               │
│ user_id (FK)     │◄───┤                               │
└──────────────────┘    │                               │
                        │                               │
        ┌───────────────┘                               │
        │                                               │
        ▼                                               │
┌──────────────────┐                                    │
│WebAuthnAuthenticator                                  │
├──────────────────┤                                    │
│ id (PK)          │                                    │
│ username         │                                    │
│ credentialId *   │                                    │
│ user_id (FK)     │◄───────────────────────────────────┘
│ authenticator    │
│ attestationType  │
│ transports       │
│ counter          │
│ backupEligible   │
│ backedUp         │
│ uvInitialized    │
│ createdAt        │
│ updatedAt        │
│ lastUsedAt       │
│ userAgent        │
│ deviceName       │
└──────────────────┘
```

## Entity Descriptions

### Core Entities

#### 1. Domain
- **Purpose:** Represents an organization/company in the multi-tenant system
- **Examples:** "company.com", "ecommerce-a.vn"
- **Key Features:** SSO configuration, MFA requirements, user quotas
- **Relationships:** Has many Users, OAuthClients, RegisteredApps

#### 2. User
- **Purpose:** Represents a user account in the system
- **Authentication:** Can use multiple methods (OTP, TOTP, WebAuthn, MagicLink)
- **Key Features:** MFA preferences, role-based access, account locking
- **Relationships:** Belongs to Domain, has many authentication credentials

#### 3. OAuthClient
- **Purpose:** OAuth2 client applications (e.g., Ecommerce A, B, C)
- **Key Features:** PKCE support, token lifetime configuration, scope management
- **Relationships:** Belongs to Domain, generates AuthorizationCodes

### Authentication Entities

#### 4. MagicLink
- **Purpose:** Passwordless login via email links
- **Lifetime:** 15 minutes default
- **Uses:** Login, account recovery, email verification
- **Link to User:** Optional (can be sent before user is fully registered)

#### 5. SentOtp
- **Purpose:** One-time password codes (SMS/Email)
- **Lifetime:** 3 minutes default
- **Features:** Attempt tracking, rate limiting
- **Link to User:** Optional (can be sent before user is identified)

#### 6. RegisteredTotp
- **Purpose:** TOTP secrets for Google Authenticator-style authentication
- **Format:** Base32-encoded secret
- **Link to User:** Optional (registered during enrollment)

#### 7. WebAuthnAuthenticatorEntity
- **Purpose:** WebAuthn/FIDO2 credentials (Touch ID, YubiKey, etc.)
- **Features:** Counter tracking, device info, transport types
- **Link to User:** Optional (registered during enrollment)

### OAuth2 Flow Entities

#### 8. AuthorizationCode
- **Purpose:** Temporary codes for OAuth2 authorization code flow
- **Lifetime:** 10 minutes default
- **Features:** PKCE support, OpenID Connect nonce, one-time use
- **Key for Flow:** Links User authentication to OAuth client

#### 9. Token
- **Purpose:** OAuth2 tokens (access, refresh, ID tokens)
- **Types:** ACCESS (short-lived), REFRESH (long-lived), ID (OpenID Connect)
- **Features:** Revocation support, device tracking

#### 10. Session
- **Purpose:** User sessions for SSO across applications
- **Features:** Device fingerprinting, location tracking, activity tracking
- **Lifetime:** Configurable (hours to days)

### Supporting Entities

#### 11. RegisteredApp
- **Purpose:** Applications registered to use the authentication API
- **Features:** API key management, rate limiting, CORS configuration
- **Link to Domain:** For multi-tenant isolation

#### 12. AuditLog
- **Purpose:** Complete audit trail of all authentication events
- **Features:** User tracking, domain tracking, event details
- **Uses:** Compliance, security monitoring, debugging

## Authentication Flows

### Flow 1: OAuth2 Authorization Code Flow (Ecommerce Integration)

```
User → Ecommerce A
  ↓
Ecommerce A → Redirect to AuthCentralA
  GET /oauth2/authorize
    ?client_id=ecommerce-a
    &redirect_uri=https://ecommerce-a.com/callback
    &response_type=code
    &scope=openid profile email
    &state=xyz
    &code_challenge=abc
    &code_challenge_method=S256
  ↓
AuthCentralA → Authenticate User
  - Check if user has active session
  - If not, prompt for authentication:
    * OTP (SentOtp entity)
    * WebAuthn (WebAuthnAuthenticatorEntity)
    * TOTP (RegisteredTotp)
    * MagicLink (MagicLink entity)
  ↓
AuthCentralA → Create AuthorizationCode
  - Link to User
  - Link to OAuthClient
  - Store PKCE challenge
  - Set 10-minute expiry
  ↓
AuthCentralA → Redirect to Ecommerce A
  https://ecommerce-a.com/callback
    ?code=auth_code_xyz
    &state=xyz
  ↓
Ecommerce A → Exchange code for tokens
  POST /oauth2/token
    code=auth_code_xyz
    client_id=ecommerce-a
    client_secret=***
    grant_type=authorization_code
    redirect_uri=https://ecommerce-a.com/callback
    code_verifier=verifier_abc
  ↓
AuthCentralA → Validate & Issue Tokens
  - Verify code not used
  - Verify code not expired
  - Verify PKCE challenge
  - Verify redirect_uri matches
  - Mark code as used
  - Create Token entities:
    * Access Token (1 hour)
    * Refresh Token (30 days)
    * ID Token (1 hour)
  ↓
Ecommerce A → Use access_token
  - Call Ecommerce A APIs
  - APIs validate token with AuthCentralA
  - Token introspection endpoint
```

### Flow 2: Direct Authentication (WebAuthn Example)

```
User → Visit AuthCentralA /login
  ↓
AuthCentralA → Prompt for username/email
  ↓
User → Enter email: user@company.com
  ↓
AuthCentralA → Lookup User by email
  - Find User entity
  - Check User.domain
  - Check authentication methods available
  ↓
AuthCentralA → Offer authentication options
  - WebAuthn (if WebAuthnAuthenticatorEntity exists)
  - OTP (always available)
  - TOTP (if RegisteredTotp exists)
  - MagicLink (always available)
  ↓
User → Choose WebAuthn
  ↓
AuthCentralA → Generate challenge
  - Create Session entity (pre-auth state)
  - Return challenge to browser
  ↓
Browser → navigator.credentials.get()
  - User touches fingerprint
  - Authenticator signs challenge
  ↓
Browser → Send signed challenge to AuthCentralA
  ↓
AuthCentralA → Verify signature
  - Find WebAuthnAuthenticatorEntity
  - Verify signature matches public key
  - Check counter (prevent replay)
  - Update counter
  - Update lastUsedAt
  ↓
AuthCentralA → Create Session
  - Update Session to authenticated state
  - Set expiry (8 hours default)
  - Return session cookie
  ↓
AuthCentralA → Create AuditLog
  - Record successful login
  - Link to User and Domain
  - Record authentication method (WEBAUTHN)
```

## Database Indexes

All entities include strategic indexes for performance:

### High-Traffic Indexes
- `users.email` (UNIQUE) - Fast user lookup
- `sessions.session_id` (UNIQUE) - Fast session validation
- `tokens.token_value` - Fast token validation
- `authorization_codes.code` (UNIQUE) - Fast code lookup
- `webauthn_authenticators.credential_id` - Fast credential lookup

### Foreign Key Indexes
- All `user_id` foreign keys - Join performance
- All `domain_id` foreign keys - Multi-tenant queries
- `oauth_clients.client_id` - Client lookup

### Time-based Indexes
- `sessions.expires_at` - Cleanup expired sessions
- `tokens.expires_at` - Cleanup expired tokens
- `authorization_codes.expires_at` - Cleanup expired codes
- `sent_otp.expireTime` - Cleanup expired OTPs
- `audit_logs.created_at` - Time-range queries

## Multi-Tenancy

The system supports full multi-tenancy through the Domain entity:

### Domain Isolation
- Each User belongs to one Domain
- Each OAuthClient belongs to one Domain
- Each RegisteredApp belongs to one Domain
- AuditLogs track Domain for compliance

### Benefits
- **Data Isolation:** Company A cannot see Company B's users
- **Custom Branding:** Each domain can have custom login page, logo
- **SSO Configuration:** Per-domain SSO with different IdPs
- **MFA Policies:** Per-domain MFA requirements
- **Quotas:** Per-domain user limits

### Example
```
Domain: ecommerce-a.vn
  └── Users: 
      ├── admin@ecommerce-a.vn
      ├── user1@ecommerce-a.vn
      └── user2@ecommerce-a.vn
  └── OAuthClients:
      ├── mobile-app
      └── web-app
  └── RegisteredApps:
      └── internal-api

Domain: ecommerce-b.vn
  └── Users: 
      ├── admin@ecommerce-b.vn
      └── user@ecommerce-b.vn
  └── OAuthClients:
      └── web-app
```

## Security Features

### 1. Authentication Method Diversity
- **WebAuthn:** Phishing-resistant, hardware-backed
- **TOTP:** Offline, time-based, app-supported
- **OTP:** SMS/Email, user-friendly
- **MagicLink:** Passwordless, simple

### 2. Token Security
- **JWT Access Tokens:** Stateless, signed, short-lived
- **Encrypted Refresh Tokens:** Long-lived, revocable
- **PKCE:** Prevents authorization code interception
- **Token Revocation:** Immediate invalidation support

### 3. Session Security
- **Device Fingerprinting:** Detect device changes
- **Location Tracking:** Detect location changes
- **Activity Tracking:** Idle timeout
- **Multiple Sessions:** Track all active sessions

### 4. Audit Trail
- **Complete Logging:** Every authentication event
- **User Attribution:** Who did what
- **Domain Attribution:** Which organization
- **Compliance:** GDPR, SOC2, HIPAA ready

## Migration Strategy

### For Existing Data

If you have existing data without User/Domain relationships:

#### Step 1: Create Default Domain
```sql
INSERT INTO domains (id, domain_name, display_name, owner_email, active)
VALUES (uuid(), 'default.local', 'Default Organization', 'admin@default.local', true);
```

#### Step 2: Create Users from Existing Authentication Data
```sql
-- From WebAuthn usernames
INSERT INTO users (id, email, domain_id, status)
SELECT uuid(), username, (SELECT id FROM domains WHERE domain_name = 'default.local'), 'ACTIVE'
FROM webauthn_authenticators
WHERE username NOT IN (SELECT email FROM users);

-- From TOTP usernames
INSERT INTO users (id, email, domain_id, status)
SELECT uuid(), username, (SELECT id FROM domains WHERE domain_name = 'default.local'), 'ACTIVE'
FROM registered_totps
WHERE username NOT IN (SELECT email FROM users);
```

#### Step 3: Link Existing Credentials to Users
```sql
-- Link WebAuthn
UPDATE webauthn_authenticators wa
SET user_id = (SELECT id FROM users WHERE email = wa.username);

-- Link TOTP
UPDATE registered_totps rt
SET user_id = (SELECT id FROM users WHERE email = rt.username);

-- Link OTP (by destination/email)
UPDATE sent_otp so
SET user_id = (SELECT id FROM users WHERE email = so.destination);

-- Link MagicLink
UPDATE magic_links ml
SET user_id = (SELECT id FROM users WHERE email = ml.email);
```

## API Endpoints

### OAuth2 Endpoints (To Be Implemented)
- `GET /oauth2/authorize` - Authorization endpoint
- `POST /oauth2/token` - Token endpoint
- `POST /oauth2/introspect` - Token introspection
- `POST /oauth2/revoke` - Token revocation
- `GET /oauth2/.well-known/openid-configuration` - Discovery

### User Management Endpoints (To Be Implemented)
- `POST /api/domains` - Create domain
- `GET /api/domains` - List domains
- `POST /api/domains/{id}/users` - Create user in domain
- `GET /api/domains/{id}/users` - List users in domain
- `GET /api/users/{id}` - Get user details
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Authentication Endpoints (Existing, To Be Enhanced)
- `POST /otp/v1/send` - Send OTP
- `POST /otp/v1/verify` - Verify OTP
- `POST /totp/v1/register` - Register TOTP
- `POST /totp/v1/verify` - Verify TOTP
- `POST /webauthn/v1/register/challenge/{login}` - Start WebAuthn registration
- `POST /webauthn/v1/register/credential` - Complete WebAuthn registration
- `POST /webauthn/v1/login/challenge/{login}` - Start WebAuthn login
- `POST /webauthn/v1/login/credential` - Complete WebAuthn login

### Audit Endpoints (Existing)
- `GET /audit-logs` - Query audit logs
- Filtered by user, domain, date range, event type

## Conclusion

This data model transforms the system into a complete AuthCentralA (centralized authentication) that can:

1. **Serve Multiple Applications:** OAuth2 integration with authorization code flow
2. **Support Multiple Organizations:** Multi-tenant via Domain entity
3. **Offer Multiple Authentication Methods:** OTP, TOTP, WebAuthn, MagicLink
4. **Track Everything:** Complete audit trail for compliance
5. **Scale:** Proper indexes and relationships for performance

All authentication methods are now properly linked to Users and Domains, creating a cohesive, enterprise-grade IAM system.
