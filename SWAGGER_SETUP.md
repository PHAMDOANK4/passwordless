# SWAGGER SETUP - QUICK REFERENCE

Tài liệu tham khảo nhanh về Swagger/OpenAPI đã được tích hợp vào project.

---

## ✅ Đã Hoàn Thành

### 1. Dependencies
- ✅ Upgraded to `springdoc-openapi-starter-webmvc-ui` 2.3.0
- ✅ Compatible với Spring Boot 3.2.5

### 2. Configuration Files
- ✅ `OpenApiConfiguration.java` - API documentation config
- ✅ `application.yml` - Swagger UI settings

### 3. Controller Annotations
- ✅ `AppRegistrationController.java` - Fully documented example

### 4. Documentation
- ✅ `docs/SWAGGER_INTEGRATION_GUIDE_VI.md` - Comprehensive guide (17KB)

---

## 🚀 Quick Start

### Access Swagger UI:

**Local:**
```
http://localhost:8080/swagger-ui/
```

**Production:**
```
https://authentication.k4.vn/swagger-ui/
```

### Test Without API Key:

```
1. Open Swagger UI
2. Find "App Management" → "POST /apps/v1/register"
3. Click "Try it out"
4. Fill body:
   {
     "name": "Test App",
     "description": "Testing",
     "rateLimitPerMinute": 60,
     "rateLimitPerHour": 1000
   }
5. Click "Execute"
6. Copy API key from response
```

### Test With API Key:

```
1. Click "Authorize" button (top right)
2. Paste API key: pk_xxx...
3. Click "Authorize" in dialog
4. Close dialog
5. Test OTP: "POST /otp/v1/send"
6. API key auto-included!
```

---

## 📁 File Locations

### Code:
```
src/main/java/org/openidentityplatform/passwordless/
├── configuration/
│   └── OpenApiConfiguration.java    ← Swagger config
├── apps/controllers/
│   └── AppRegistrationController.java    ← Example with annotations
```

### Config:
```
src/main/resources/
└── application.yml    ← Springdoc settings (bottom of file)
```

### Dependencies:
```
pom.xml
└── springdoc-openapi-starter-webmvc-ui (line 138-141)
```

### Documentation:
```
docs/
└── SWAGGER_INTEGRATION_GUIDE_VI.md    ← Complete guide
```

---

## 🎯 Features

- ✅ Interactive API testing
- ✅ Auto-generated documentation
- ✅ API key authorization
- ✅ Multiple servers (local/production)
- ✅ Search and filter
- ✅ Copy curl commands
- ✅ Request/response schemas
- ✅ OpenAPI 3.0 JSON spec

---

## 📖 Documentation

**Full Guide:** `/docs/SWAGGER_INTEGRATION_GUIDE_VI.md`

**Contents:**
1. Giới thiệu Swagger/OpenAPI
2. Truy cập và sử dụng UI
3. Test APIs step-by-step
4. Tính năng Swagger UI
5. OpenAPI JSON spec
6. Tích hợp vào code
7. Troubleshooting
8. Best practices

---

## 🔗 Important URLs

| URL | Purpose |
|-----|---------|
| `/swagger-ui/` | Interactive UI |
| `/v3/api-docs` | OpenAPI JSON |
| `/swagger-ui/index.html` | Alternative path |

---

## 💡 Common Tasks

### Register App & Get API Key:
```
POST /apps/v1/register
→ Copy "apiKey" from response
```

### Authorize for Protected Endpoints:
```
Click [Authorize] → Paste API key → Authorize
```

### Test OTP:
```
POST /otp/v1/send
Body: {"sender": "sms", "destination": "+84912345678"}
```

### Copy cURL Command:
```
Execute request → Click copy icon
```

---

## 🐛 Troubleshooting

**Issue:** Swagger UI not loading
**Fix:** Check `mvn clean install` and restart

**Issue:** 401 Unauthorized with API key
**Fix:** Click [Authorize], paste key, click [Authorize] button

**Issue:** Try it out not working
**Fix:** Clear browser cache, try different browser

**More solutions:** See full guide in `/docs/SWAGGER_INTEGRATION_GUIDE_VI.md`

---

## 📚 Next Steps

### Optional Enhancements:

1. **Add annotations to more controllers:**
   - OtpRestController
   - TotpRestController
   - WebAuthnController
   - AuditLogController

2. **Enhance documentation:**
   - Add more examples
   - Add operation IDs
   - Document error schemas

3. **Generate client code:**
   ```bash
   openapi-generator-cli generate \
     -i http://localhost:8080/v3/api-docs \
     -g typescript-axios \
     -o ./client
   ```

---

## ✅ Verification

**Check if working:**
```bash
# 1. Start app
mvn spring-boot:run

# 2. Open browser
http://localhost:8080/swagger-ui/

# 3. Should see:
- "Passwordless Authentication API" title
- Multiple API groups (App Management, OTP, etc.)
- [Authorize] button
- Working "Try it out" buttons
```

---

## 🎉 Summary

**What you got:**
- ✅ Full Swagger/OpenAPI integration
- ✅ Interactive API testing UI
- ✅ Comprehensive Vietnamese guide
- ✅ Production-ready setup
- ✅ API key authentication
- ✅ Multi-environment support

**Ready to use immediately!**

Just start the app and go to `/swagger-ui/` 🚀

---

**For detailed information, read:** `/docs/SWAGGER_INTEGRATION_GUIDE_VI.md`
