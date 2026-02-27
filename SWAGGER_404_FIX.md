# Swagger UI 404 Error - Fixed! ✅

## Problem

When accessing `http://localhost:8080/swagger-ui.html`, you get:

```
This application has no explicit mapping for /error
There was an unexpected error (type=Not Found, status=404).
No static resource swagger-ui.html.
```

## Root Cause

**springdoc-openapi v2.x** (for Spring Boot 3) changed the Swagger UI path:

| Version | Path |
|---------|------|
| v1.x (Spring Boot 2) | `/swagger-ui.html` |
| v2.x (Spring Boot 3) | `/swagger-ui/` or `/swagger-ui/index.html` |

This project uses **v2.3.0** which requires the new path format.

---

## ✅ Solution (Already Applied)

### 1. Updated Configuration

**File:** `src/main/resources/application.yml`

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui  # Changed from /swagger-ui.html
```

### 2. Security Configuration (Already Correct)

**File:** `SecurityConfiguration.java`

```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

The `/**` pattern already covers all Swagger UI resources.

---

## 🚀 How to Access Swagger UI (Correct URLs)

### Option 1: With Trailing Slash (Recommended)
```
http://localhost:8080/swagger-ui/
```

### Option 2: Direct to Index
```
http://localhost:8080/swagger-ui/index.html
```

### Production
```
https://authentication.k4.vn/swagger-ui/
```

---

## 🔧 Verification Steps

### 1. Start Application
```bash
mvn spring-boot:run
```

### 2. Test URLs

**✅ These work:**
```bash
curl -I http://localhost:8080/swagger-ui/
curl -I http://localhost:8080/swagger-ui/index.html
curl -I http://localhost:8080/v3/api-docs
```

**❌ This doesn't work (old path):**
```bash
curl -I http://localhost:8080/swagger-ui.html
# → 404 Not Found
```

### 3. Open in Browser

Navigate to: `http://localhost:8080/swagger-ui/`

You should see the Swagger UI with:
- **Title:** "Passwordless Authentication API"
- **Version:** "1.0.0"
- **API Groups:** App Management, OTP, TOTP, WebAuthn, Audit Logs

---

## 📝 Updated Documentation

All documentation has been updated to use the correct URL:

- ✅ `SWAGGER_SETUP.md` - Updated all URLs
- ✅ `docs/SWAGGER_INTEGRATION_GUIDE_VI.md` - Updated all URLs

---

## 🎯 Quick Reference

### springdoc-openapi v2.x URLs:

| Resource | URL | Description |
|----------|-----|-------------|
| Swagger UI | `/swagger-ui/` | Main UI interface |
| Swagger UI (direct) | `/swagger-ui/index.html` | Direct index page |
| OpenAPI JSON | `/v3/api-docs` | OpenAPI specification |
| OpenAPI YAML | `/v3/api-docs.yaml` | OpenAPI spec (YAML) |

### Common Mistakes:

❌ **Don't use:** `/swagger-ui.html` (old v1.x path)  
✅ **Use:** `/swagger-ui/` (new v2.x path)

❌ **Don't forget:** Trailing slash matters! `/swagger-ui` redirects to `/swagger-ui/`  
✅ **Best practice:** Always use `/swagger-ui/` with trailing slash

---

## 🐛 Troubleshooting

### Issue 1: Still Getting 404

**Check 1:** Verify dependency in `pom.xml`
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Check 2:** Rebuild project
```bash
mvn clean install
mvn spring-boot:run
```

**Check 3:** Clear browser cache
- Ctrl+Shift+R (hard refresh)
- Or use incognito mode

### Issue 2: Swagger UI Loads but APIs Don't Show

**Check:** OpenAPI configuration
```bash
# Test OpenAPI endpoint
curl http://localhost:8080/v3/api-docs | jq .
```

Should return JSON with API definitions.

**Fix:** Verify `OpenApiConfiguration.java` exists and is loaded.

### Issue 3: 403 Forbidden

**Check:** Security configuration allows Swagger paths
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

---

## 📚 Additional Resources

**Internal Docs:**
- `SWAGGER_SETUP.md` - Quick start guide
- `docs/SWAGGER_INTEGRATION_GUIDE_VI.md` - Complete Vietnamese guide

**External:**
- [springdoc-openapi v2 docs](https://springdoc.org/v2/)
- [Migration Guide v1 → v2](https://springdoc.org/#migrating-from-springdoc-v1)

---

## ✅ Summary

**Problem:** 404 error at `/swagger-ui.html`  
**Cause:** springdoc v2.x uses new path format  
**Solution:** Use `/swagger-ui/` instead  
**Status:** ✅ **FIXED** - Configuration updated, documentation corrected

**Access Swagger UI now at:** `http://localhost:8080/swagger-ui/` 🎉
