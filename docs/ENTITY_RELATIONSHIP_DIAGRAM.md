# Database Entity Relationship Diagram (ERD)

This document provides an up-to-date ER diagram of the current Passwordless IdP data model based on entity mappings under src/main/java.

## 1. Mermaid ER Diagram

```mermaid
erDiagram
    DOMAINS {
        uuid id PK
        varchar domain_name UK
        varchar display_name
        varchar owner_email
        boolean active
        boolean require_mfa
        boolean sso_enabled
        text sso_config
        int max_users
        timestamp created_at
        timestamp updated_at
    }

    USERS {
        uuid id PK
        varchar email UK
        uuid domain_id FK
        varchar first_name
        varchar last_name
        varchar display_name
        varchar phone_number
        varchar external_id
        varchar status
        boolean mfa_enabled
        varchar preferred_mfa_method
        varchar role
        timestamp created_at
        timestamp updated_at
    }

    OAUTH_CLIENTS {
        uuid id PK
        varchar client_id UK
        varchar client_secret
        varchar client_name
        uuid domain_id FK
        text redirect_uris
        varchar allowed_scopes
        varchar grant_types
        boolean active
        boolean require_pkce
        int access_token_lifetime_seconds
        int refresh_token_lifetime_seconds
        int id_token_lifetime_seconds
        timestamp created_at
        timestamp updated_at
    }

    AUTHORIZATION_CODES {
        uuid id PK
        varchar code UK
        uuid user_id FK
        varchar client_id
        uuid oauth_client_id FK
        varchar redirect_uri
        varchar scopes
        varchar state
        varchar code_challenge
        varchar code_challenge_method
        timestamp expires_at
        boolean used
    }

    OAUTH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_type
        varchar token_value
        varchar client_id
        varchar session_id
        timestamp created_at
        timestamp expires_at
        boolean revoked
    }

    USER_SESSIONS {
        uuid id PK
        varchar session_id UK
        uuid user_id FK
        varchar ip_address
        timestamp created_at
        timestamp expires_at
        timestamp last_activity_at
        boolean revoked
        varchar auth_method
        int auth_level
    }

    MAGIC_LINKS {
        uuid id PK
        varchar token UK
        varchar email
        uuid user_id FK
        varchar purpose
        timestamp created_at
        timestamp expires_at
        boolean used
    }

    SENT_OTP {
        uuid session_id PK
        varchar otp
        bigint expire_time
        varchar destination
        int attempts
        uuid user_id FK
    }

    REGISTERED_TOTPS {
        uuid id PK
        varchar username UK
        varchar secret
        uuid user_id FK
    }

    WEBAUTHN_AUTHENTICATORS {
        bigint id PK
        varchar username
        varchar credential_id
        text authenticator
        bigint counter
        uuid user_id FK
        timestamp created_at
        timestamp updated_at
    }

    REGISTERED_APPS {
        uuid id PK
        varchar name UK
        uuid domain_id FK
        varchar api_key_hash
        boolean active
        int rate_limit_per_minute
        int rate_limit_per_hour
        timestamp created_at
    }

    AUDIT_LOGS {
        uuid id PK
        varchar app_id
        varchar app_name
        uuid user_id FK
        uuid domain_id FK
        varchar user_email
        varchar event_type
        varchar endpoint
        boolean success
        timestamp created_at
    }

    TOKEN_BLACKLIST {
        uuid id PK
        varchar jti UK
        varchar subject
        timestamp expires_at
        timestamp revoked_at
        varchar reason
    }

    DOMAINS ||--o{ USERS : "contains"
    DOMAINS ||--o{ OAUTH_CLIENTS : "owns"
    DOMAINS ||--o{ REGISTERED_APPS : "owns"
    DOMAINS ||--o{ AUDIT_LOGS : "scopes"

    USERS ||--o{ AUTHORIZATION_CODES : "issues"
    USERS ||--o{ OAUTH_TOKENS : "owns"
    USERS ||--o{ USER_SESSIONS : "has"
    USERS ||--o{ MAGIC_LINKS : "receives"
    USERS ||--o{ SENT_OTP : "receives"
    USERS ||--o{ REGISTERED_TOTPS : "enrolls"
    USERS ||--o{ WEBAUTHN_AUTHENTICATORS : "registers"
    USERS ||--o{ AUDIT_LOGS : "acts_in"

    OAUTH_CLIENTS ||--o{ AUTHORIZATION_CODES : "authorizes"
```

## 2. Notes on Relationship Types

### 2.1 Physical FK relationships
The following relationships are modeled as JPA ManyToOne and materialized as FK columns:
- users.domain_id -> domains.id
- oauth_clients.domain_id -> domains.id
- authorization_codes.user_id -> users.id
- authorization_codes.oauth_client_id -> oauth_clients.id
- oauth_tokens.user_id -> users.id
- user_sessions.user_id -> users.id
- magic_links.user_id -> users.id
- sent_otp.user_id -> users.id
- registered_totps.user_id -> users.id
- webauthn_authenticators.user_id -> users.id
- registered_apps.domain_id -> domains.id
- audit_logs.user_id -> users.id
- audit_logs.domain_id -> domains.id

### 2.2 Logical (non-FK) references
The model also contains string-based references used by application logic:
- oauth_tokens.client_id -> oauth_clients.client_id (logical link)
- oauth_tokens.session_id -> user_sessions.session_id (logical link)
- authorization_codes.client_id -> oauth_clients.client_id (logical link)
- audit_logs.app_id/app_name -> registered_apps.id/name (logical link)

## 3. Scope of This Diagram

This ERD focuses on persistent relational entities currently mapped with JPA. Redis-based ephemeral state (for example WebAuthn ceremony state and active-session cache keys) is intentionally excluded because it is key-value state, not relational schema.
