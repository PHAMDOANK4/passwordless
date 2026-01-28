# IAM Platform Transformation - Implementation Guide

## Overview

This document describes the transformation of the Passwordless Authentication Service into a full-featured IAM (Identity and Access Management) platform, similar to Google Workspace or Microsoft Azure AD.

## Architecture Changes

### Phase 1: Domain & User Management (IMPLEMENTED)

#### New Entities

1. **Domain Entity** (`iam/models/Domain.java`)
   - Represents an organization/domain (e.g., company.com, organization.vn)
   - Features:
     - Unique domain name
     - Display name and description
     - Owner email
     - Active/inactive status
     - MFA requirements (domain-wide policy)
     - SSO configuration support
     - User quota management
     - Custom branding (logo, login page)

2. **User Entity** (`iam/models/User.java`)
   - Represents individual user accounts within a domain
   - Features:
     - Email-based identification
     - Domain association (many-to-one)
     - User profile (name, phone, avatar)
     - User status (active, suspended, deleted, pending)
     - Role-based access (super_admin, domain_admin, user, guest)
     - MFA preferences
     - Security features (login attempts tracking, account locking)
     - Audit tracking (last login, timestamps)

#### Repositories

- `DomainRepository`: CRUD operations for domains
- `UserRepository`: User management with domain filtering

#### DTOs

- `CreateDomainRequest`: Domain registration request
- `DomainResponse`: Domain information response
- `CreateUserRequest`: User creation request
- `UserResponse`: User information response

## How It Works Like Google/Microsoft IAM

### 1. Domain-Based Organization

**Similar to Google Workspace:**
- Each organization registers a domain (e.g., mycompany.com)
- Users belong to domains (user@mycompany.com)
- Domain admins manage their own users
- Centralized authentication across all apps in the domain

### 2. User Management

**Similar to Azure AD:**
- Admins can create/manage users in their domain
- Users can have different roles and permissions
- Support for external identity providers (SSO)
- MFA enforcement at domain or user level

### 3. Multi-Factor Authentication

**Similar to Both:**
- Multiple MFA methods: TOTP, SMS, Email, WebAuthn
- Domain-wide MFA policies
- User-specific MFA preferences
- Seamless integration with existing auth methods

### 4. Single Sign-On (SSO)

**Planned Features:**
- SAML 2.0 support
- OAuth 2.0/OIDC support
- Custom identity providers
- Session management across applications

## Usage Examples

### 1. Create a Domain (Organization)

```bash
POST /iam/v1/domains
{
  "domainName": "mycompany.com",
  "displayName": "My Company Inc.",
  "description": "Enterprise organization",
  "ownerEmail": "admin@mycompany.com",
  "requireMfa": true,
  "maxUsers": 1000
}
```

### 2. Create Users in Domain

```bash
POST /iam/v1/domains/{domainId}/users
{
  "email": "john.doe@mycompany.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "role": "USER",
  "mfaEnabled": true,
  "preferredMfaMethod": "TOTP"
}
```

### 3. User Authentication Flow

1. User enters email: john.doe@mycompany.com
2. System identifies domain: mycompany.com
3. Checks domain MFA requirements
4. Applies domain-specific authentication policies
5. User authenticates using WebAuthn/OTP/TOTP
6. Session created with domain context

### 4. Domain Admin Actions

```bash
# List all users in domain
GET /iam/v1/domains/{domainId}/users?page=0&size=20

# Suspend a user
PATCH /iam/v1/domains/{domainId}/users/{userId}
{
  "status": "SUSPENDED"
}

# Reset user's MFA
DELETE /iam/v1/domains/{domainId}/users/{userId}/mfa
```

## Next Implementation Phases

### Phase 2: Domain Administration APIs (TODO)

- Domain CRUD endpoints
- User management endpoints
- Domain settings management
- Bulk user operations

### Phase 3: SSO & Federation (TODO)

- SAML 2.0 integration
- OAuth 2.0/OIDC provider
- External IDP configuration
- Session management APIs

### Phase 4: Access Control (TODO)

- RBAC (Role-Based Access Control)
- Permission system
- Resource access policies
- Application integration

### Phase 5: Security & Compliance (TODO)

- Enhanced audit logging
- Compliance reporting (SOC 2, GDPR)
- Security policies
- IP whitelisting/blacklisting

## Integration with Existing Systems

### WebAuthn Integration

Users' WebAuthn authenticators will be linked to User entities:

```java
@ManyToOne
@JoinColumn(name = "user_id")
private User user;
```

### OTP/TOTP Integration

Authentication methods will check user's domain policies:

```java
if (user.getDomain().isRequireMfa() && !user.isMfaEnabled()) {
    throw new SecurityException("MFA required by domain policy");
}
```

### App Registration Integration

Apps can be associated with domains for scoped access:

```java
@ManyToOne
@JoinColumn(name = "domain_id")
private Domain domain;
```

## Benefits

1. **Centralized Management**: Single place to manage all users
2. **Domain Isolation**: Complete separation between organizations
3. **Flexible Policies**: Domain-wide or user-specific settings
4. **Scalability**: Support for multiple domains and thousands of users
5. **Enterprise Ready**: Features expected by enterprise customers
6. **Compliance**: Audit trails and security features for regulations

## Migration Path

For existing deployments:

1. Create a default domain for existing users
2. Migrate existing usernames to user entities
3. Link existing authenticators to users
4. Maintain backward compatibility with old APIs

## Conclusion

This transformation elevates the system from a simple authentication service to a comprehensive IAM platform suitable for enterprise use, similar to Google Workspace or Microsoft Azure AD, while maintaining the core passwordless authentication capabilities.
