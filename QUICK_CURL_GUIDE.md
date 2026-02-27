# HƯỚNG DẪN NHANH - CURL TESTING

## Quick Start (Bắt đầu nhanh)

### 1. Kiểm tra server
```bash
curl http://localhost:8080/actuator/health
```

### 2. Đăng ký app và lấy API key
```bash
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Test App",
    "description": "Testing",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }' | jq
```

**Lưu API key:**
```bash
export API_KEY="pk_your_api_key_here"
```

### 3. Test OTP Flow

**Gửi OTP:**
```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sender": "sms", "destination": "+84912345678"}' | jq
```

**Xác minh OTP (Google/Microsoft style):**
```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"destination": "+84912345678", "otp": "123456"}' | jq
```

### 4. Test TOTP Flow

**Đăng ký:**
```bash
curl -X POST http://localhost:8080/totp/v1/register \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"username": "user@example.com"}' | jq
```

**Xác minh:**
```bash
curl -X POST http://localhost:8080/totp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"username": "user@example.com", "totp": 123456}' | jq
```

### 5. Test WebAuthn

**Mở trình duyệt (không cần API key):**
```bash
open http://localhost:8080/webauthn/test
```

### 6. Xem Audit Logs

**Tất cả logs:**
```bash
curl "http://localhost:8080/apps/v1/audit/logs?page=0&size=10" | jq
```

**Logs của app:**
```bash
curl "http://localhost:8080/apps/v1/audit/logs/app/$APP_ID" | jq
```

## Common Commands (Lệnh thường dùng)

```bash
# List apps
curl http://localhost:8080/apps/v1/list | jq

# Deactivate app
curl -X POST http://localhost:8080/apps/v1/$APP_ID/deactivate

# Activate app
curl -X POST http://localhost:8080/apps/v1/$APP_ID/activate

# Regenerate API key
curl -X POST http://localhost:8080/apps/v1/$APP_ID/regenerate-key

# Delete app
curl -X DELETE http://localhost:8080/apps/v1/$APP_ID

# Get stats
curl "http://localhost:8080/apps/v1/audit/stats/$APP_ID?hours=24"
```

## Endpoints Summary

| Endpoint | Method | Auth | Mô tả |
|----------|--------|------|-------|
| `/apps/v1/register` | POST | No | Đăng ký app |
| `/apps/v1/list` | GET | No | List apps |
| `/otp/v1/send` | POST | Yes | Gửi OTP |
| `/otp/v1/verify` | POST | Yes | Xác minh OTP |
| `/totp/v1/register` | POST | Yes | Đăng ký TOTP |
| `/totp/v1/verify` | POST | Yes | Xác minh TOTP |
| `/webauthn/test` | GET | No | Test UI |
| `/apps/v1/audit/logs` | GET | No | Audit logs |

## Full Documentation

Xem tài liệu đầy đủ tại: **`docs/CURL_TESTING_GUIDE_VI.md`**

Bao gồm:
- ✅ Hướng dẫn chi tiết từng bước
- ✅ Script tự động hoàn chỉnh  
- ✅ Troubleshooting guide
- ✅ Best practices
- ✅ Production tips

