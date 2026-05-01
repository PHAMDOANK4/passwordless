# Production Runbook — Passwordless IdP

This runbook documents production-ready operational procedures and security controls for deploying and operating the passwordless IdP.

**Scope:** key rotation, signing key backups, secrets management, disabling dev conveniences (JIT provisioning), refresh-token & session migration, monitoring and incident response.

## 1. Secrets & Keys — Recommended setup
- Use a managed Key Management Service (KMS/HSM) for private keys (AWS KMS, Azure Key Vault, Google KMS, or on-prem HSM).
- Do NOT store private signing keys in source control or in plain files in the deployment host.
- If KMS/HSM is not available, encrypt private keys at rest with a server-side envelope key whose root is stored in KMS.
- Store application configuration and credentials in a secrets store (Vault, Key Vault, AWS SSM Parameter Store) and inject them at runtime via CI/CD.

## 2. Signing Key Management & Rotation
Goals: rotate keys without service disruption, preserve verification for existing tokens, and provide emergency rotation steps.

Normal rotation procedure:
1. Create a new RSA key pair (2048/3072/4096 bits; prefer 3072+ or use ECDSA P-256 if supported by clients).
2. Add the new public key to the JWKS (persist as a `SigningKey` record with `kid` and `status=active`).
3. Deploy the new private key to the signing service using KMS/HSM or encrypted secret injection.
4. Start issuing tokens signed with the new `kid` while keeping old keys in the JWKS for verification.
5. Monitor for clients that reject tokens; leave old keys in JWKS until all tokens signed with old `kid` expire (token TTL + safety window).
6. After the safety window, revoke and delete the old key from the JWKS and rotate storage accordingly.

Emergency compromise procedure:
1. Immediately mark compromised key(s) as `revoked` in DB and remove from JWKS publication.
2. Generate a new key pair and add to JWKS.
3. Revoke all active sessions (see Session Revocation below) and revoke refresh tokens.
4. Notify dependent clients and perform a forced re-authentication campaign.
5. Rotate any other secrets that might have been exposed.

Notes:
- Keep an auditable trail of key creation/rotation events.
- Test the rotation process in staging before production.

## 3. Refresh Token Storage & Migration
- Refresh tokens are stored hashed (SHA-256 hex) in DB; never store raw refresh tokens.
- If changing hashing algorithm (e.g., move to bcrypt/HKDF), implement a migration that accepts old hashes while re-hashing when tokens are used:
  - On token use: verify against old hash algorithm; if valid, store new-hash(newAlg) and delete old hash entry.
  - Log the migration events and monitor for failed verification spikes.

Refresh token reuse detection:
- If reuse detected (an old token is presented after rotation), revoke the associated session and all related refresh tokens.
- Emit an alert and create an investigation ticket with the offending `sid` and client info.

## 4. Session Management & Revocation
- Session lifecycle lives in DB and Cache (Redis). Ensure revocation invalidates both:
  - DB: mark session `revoked=true`.
  - Redis: delete `session:active:{sid}` cache entry.
  - Token blacklist: add current access token signatures or session identifier to the blacklist if immediate invalidation required.

Revocation workflow (operator-triggered):
1. Mark session(s) revoked in DB.
2. Flush/expire session caches in Redis for the affected `sid`(s).
3. Optionally push token blacklist entries (short TTL equals token remaining lifetime).
4. Notify user via admin UI/email if applicable.

## 5. Disabling JIT Provisioning (production hardening)
- The codebase contains JIT provisioning used for dev convenience (creates `default.com` domain/user automatically).
- Add a runtime config flag `feature.jitProvisioning.enabled=false` in `application-prod.yml` and ensure the code checks this flag before creating domains/users.
- Audit the code paths: `OtpService`, `TotpService`, and WebAuthn registration flows to ensure they respect the flag.
- Run integration tests with `feature.jitProvisioning.enabled=false` to validate error paths and user-friendly messages.

## 6. Backups & Key Persistence
- Back up the signing key metadata and encrypted private keys (or KMS references) to a secure, access-restricted backup sink.
- Periodically export JWKS public keys for client cache reconciliation.
- Test key restoration process: spin up a recovery environment and validate tokens/signing using restored keys.

## 7. Deployment Checklist (production)
- Use a production-grade RDBMS (managed MySQL/Aurora) and configure connection pooling and backups.
- Configure Redis with persistence (AOF/RDB) and high availability if session caching is critical.
- Use HTTPS/TLS terminated at a trusted reverse proxy (ALB, Cloud Load Balancer, or Nginx with certs managed by ACME or automated tooling).
- Environment variables and secrets delivered via Vault/Key Vault and not via repository or Docker image.
- Health checks: `/health`, readiness, and liveness endpoints in the application.
- Metrics: expose Prometheus metrics and instrument key events (token issuance, refresh reuse, key rotation).

Docker Compose vs Kubernetes:
- For staging and small deployments, Compose may be acceptable. For production, use orchestrators (Kubernetes, ECS) with secret injection and pod restarts on config changes.

## 8. Monitoring & Alerts
- Alerts to configure:
  - `refresh_token_reuse` -> high priority.
  - `webauthn_counter_anomaly` -> medium priority.
  - `signing_key_rotation` events -> informational + scheduled reminders.
  - `session_revocations` spikes -> investigate for potential compromise.
- Collect logs for:
  - Token issuance with `kid` and `sid` (sanitized; do not log raw tokens).
  - Failed token validations and signature errors (to detect stale JWKS).

## 9. Testing & Validation
- Smoke test after deployment:
  - Obtain JWKS: `GET /.well-known/jwks.json` and verify public keys.
  - Complete passwordless flow (OTP/TOTP/WebAuthn) in staging and verify session creation.
  - Exchange authorization code with PKCE and validate tokens using JWKS.
- Run automated integration tests in CI which cover key rotation scenarios and refresh token rotation.

## 10. Incident Response Examples
- Refresh token reuse detected:
  1. Identify affected `sid` and client.
  2. Revoke session & all refresh tokens.
  3. Notify security on-call and start investigation.
  4. Consider requiring MFA/WebAuthn re-registration for affected accounts.

- Signing key compromise:
  1. Emergency rotate signing key (see Emergency compromise procedure above).
  2. Revoke sessions and require re-authentication.
  3. Notify affected clients and partners.

## Appendix — Useful Commands
- View JWKS locally:

```bash
curl -sS http://localhost:8080/.well-known/jwks.json | jq .
```

- Health check:

```bash
curl -sS http://localhost:8080/actuator/health
```

- Revoke session (example SQL):

```sql
UPDATE session SET revoked = true WHERE sid = '...';
```


---

Placeholders and follow-ups:
- I can add exact property names for `application.yml` keys if you want (e.g., `feature.jitProvisioning.enabled`).
- I can produce a checklist PR that modifies default prod configuration to disable JIT provisioning and wire secrets to KMS.
- I can scaffold automated rotation scripts (Kubernetes Job or GH Actions) to create keys and update JWKS.
