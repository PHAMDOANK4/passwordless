# HƯỚNG DẪN TEST HỆ THỐNG BẰNG CURL

## Mục lục
1. [Giới thiệu](#giới-thiệu)
2. [Chuẩn bị](#chuẩn-bị)
3. [Quản lý Ứng dụng](#1-quản-lý-ứng-dụng-app-management)
4. [Xác thực OTP](#2-xác-thực-otp)
5. [Xác thực TOTP](#3-xác-thực-totp-google-authenticator)
6. [WebAuthn/FIDO2](#4-webauthn-fido2)
7. [Audit Logs](#5-audit-logs-nhật-ký-kiểm-toán)
8. [Script Test Tự Động](#6-script-test-tự-động)
9. [Troubleshooting](#7-troubleshooting)

---

## Giới thiệu

Tài liệu này hướng dẫn chi tiết cách sử dụng `curl` để test tất cả các chức năng của **Centralized Passwordless Authentication System**.

**Địa chỉ mặc định:** `http://localhost:8080`

**Yêu cầu:**
- curl (đã cài đặt)
- jq (tùy chọn, để format JSON đẹp hơn)

---

## Chuẩn bị

### Kiểm tra server đang chạy

```bash
curl -X GET http://localhost:8080/actuator/health
```

**Kết quả mong đợi:**
```json
{
  "status": "UP"
}
```

### Cài đặt jq (tùy chọn)

```bash
# Ubuntu/Debian
sudo apt-get install jq

# macOS
brew install jq

# Windows (với choco)
choco install jq
```

**Cách sử dụng jq:**
```bash
curl http://localhost:8080/apps/v1/list | jq
```

---

## 1. Quản lý Ứng dụng (App Management)

### 1.1 Đăng ký App mới

**Mục đích:** Tạo ứng dụng mới và nhận API key để xác thực

```bash
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Test App",
    "description": "Application for testing",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }' | jq
```

**Kết quả:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Test App",
  "description": "Application for testing",
  "apiKey": "pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5",
  "active": true,
  "createdAt": "2026-01-28T10:00:00Z",
  "rateLimitPerMinute": 100,
  "rateLimitPerHour": 5000
}
```

**⚠️ QUAN TRỌNG:** Lưu lại `apiKey` ngay! Nó chỉ hiển thị một lần duy nhất.

**Lưu API key vào biến:**
```bash
export API_KEY="pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5"
export APP_ID="550e8400-e29b-41d4-a716-446655440000"
```

### 1.2 Liệt kê tất cả Apps

```bash
curl -X GET http://localhost:8080/apps/v1/list | jq
```

**Kết quả:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "My Test App",
    "description": "Application for testing",
    "active": true,
    "createdAt": "2026-01-28T10:00:00Z",
    "lastUsedAt": null,
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }
]
```

### 1.3 Xem chi tiết một App

```bash
curl -X GET http://localhost:8080/apps/v1/$APP_ID | jq
```

### 1.4 Tạm ngừng App (Deactivate)

```bash
curl -X POST http://localhost:8080/apps/v1/$APP_ID/deactivate
```

**Kết quả:** HTTP 200 OK (không có body)

**Kiểm tra:**
```bash
curl -X GET http://localhost:8080/apps/v1/$APP_ID | jq '.active'
# Kết quả: false
```

### 1.5 Kích hoạt lại App (Activate)

```bash
curl -X POST http://localhost:8080/apps/v1/$APP_ID/activate
```

### 1.6 Tạo lại API Key (Regenerate)

```bash
curl -X POST http://localhost:8080/apps/v1/$APP_ID/regenerate-key
```

**Kết quả:**
```
pk_bmV3Z2VuZXJhdGVka2V5Zm9ydGVzdGluZw==
```

**Cập nhật biến:**
```bash
export API_KEY="pk_bmV3Z2VuZXJhdGVka2V5Zm9ydGVzdGluZw=="
```

### 1.7 Xóa App

```bash
curl -X DELETE http://localhost:8080/apps/v1/$APP_ID
```

**Kết quả:** HTTP 204 No Content

---

## 2. Xác thực OTP

### 2.1 Gửi OTP qua SMS

```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "sms",
    "destination": "+84912345678"
  }' | jq
```

**Kết quả:**
```json
{
  "operationId": "993e61be-23cf-412d-8273-f02e316e8689"
}
```

**Lưu operationId:**
```bash
export SESSION_ID="993e61be-23cf-412d-8273-f02e316e8689"
```

### 2.2 Gửi OTP qua Email

```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq
```

### 2.3 Xác minh OTP (Phương thức 1: Theo Destination)

**Phương thức này giống Google/Microsoft - không cần sessionId**

```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "+84912345678",
    "otp": "123456"
  }' | jq
```

**Kết quả:**
```json
{
  "verified": true
}
```

### 2.4 Xác minh OTP (Phương thức 2: Theo SessionId - Legacy)

```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "'$SESSION_ID'",
    "otp": "123456"
  }' | jq
```

### 2.5 Test Rate Limiting

**Gửi nhiều request liên tiếp:**
```bash
for i in {1..5}; do
  curl -X POST http://localhost:8080/otp/v1/send \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"sender": "sms", "destination": "+84912345678"}'
  echo ""
  sleep 1
done
```

---

## 3. Xác thực TOTP (Google Authenticator)

### 3.1 Đăng ký TOTP cho user

```bash
curl -X POST http://localhost:8080/totp/v1/register \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john.doe@example.com"
  }' | jq
```

**Kết quả:**
```json
{
  "uri": "otpauth://totp/Passwordless:john.doe@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Passwordless",
  "qr": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
}
```

**Cách sử dụng:**
1. Lưu `uri` để import vào Google Authenticator
2. Hoặc hiển thị `qr` code (base64 image) để quét

**Lưu username:**
```bash
export TOTP_USER="john.doe@example.com"
```

### 3.2 Xác minh TOTP

**Lấy mã TOTP từ Google Authenticator app (6 chữ số)**

```bash
curl -X POST http://localhost:8080/totp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "'$TOTP_USER'",
    "totp": 879580
  }' | jq
```

**Kết quả:**
```json
{
  "valid": true
}
```

**❌ Nếu mã sai:**
```json
{
  "valid": false
}
```

---

## 4. WebAuthn (FIDO2)

### 4.1 Test qua Web Interface

**WebAuthn yêu cầu trình duyệt - không test được bằng curl thuần túy**

**Truy cập giao diện test:**
```bash
# Mở trong trình duyệt
open http://localhost:8080/webauthn/test

# Hoặc trên Linux
xdg-open http://localhost:8080/webauthn/test

# Hoặc Windows
start http://localhost:8080/webauthn/test
```

**Giao diện test bao gồm:**
- ✅ Đăng ký WebAuthn (Register)
- ✅ Đăng nhập WebAuthn (Login)
- ✅ Activity log real-time
- ✅ Hỗ trợ Touch ID, Windows Hello, USB Security Key

**⚠️ Lưu ý:** WebAuthn test interface **KHÔNG CẦN** API key vì nó dành cho user authentication, không phải server-to-server.

### 4.2 API Endpoints (Dùng từ JavaScript)

**Lấy challenge để đăng ký:**
```bash
curl -X GET http://localhost:8080/webauthn/v1/register/challenge/user@example.com
```

**Lấy challenge để đăng nhập:**
```bash
curl -X POST http://localhost:8080/webauthn/v1/login/challenge \
  -H "Content-Type: application/json" \
  -d '{"username": "user@example.com"}'
```

**📝 Ghi chú:** Các endpoint register/login finish cần data từ WebAuthn API của browser, không thể test trực tiếp bằng curl.

---

## 5. Audit Logs (Nhật ký kiểm toán)

### 5.1 Xem tất cả Audit Logs

```bash
curl -X GET "http://localhost:8080/apps/v1/audit/logs?page=0&size=10" | jq
```

**Với custom sorting:**
```bash
curl -X GET "http://localhost:8080/apps/v1/audit/logs?page=0&size=10&sortBy=createdAt&direction=DESC" | jq
```

**Kết quả:**
```json
{
  "content": [
    {
      "id": "log-id-123",
      "appId": "550e8400-e29b-41d4-a716-446655440000",
      "eventType": "API_REQUEST",
      "endpoint": "/otp/v1/send",
      "ipAddress": "192.168.1.100",
      "success": true,
      "createdAt": "2026-01-28T10:15:00Z",
      "errorMessage": null
    }
  ],
  "pageable": { ... },
  "totalElements": 150,
  "totalPages": 15,
  "number": 0,
  "size": 10
}
```

### 5.2 Xem logs của một App cụ thể

```bash
curl -X GET "http://localhost:8080/apps/v1/audit/logs/app/$APP_ID?page=0&size=20" | jq
```

### 5.3 Xem logs theo loại Event

**Các loại event:**
- `AUTHENTICATION` - Xác thực người dùng
- `API_REQUEST` - Request API
- `RATE_LIMIT_EXCEEDED` - Vượt giới hạn rate

```bash
# Xem logs AUTHENTICATION
curl -X GET "http://localhost:8080/apps/v1/audit/logs/event/AUTHENTICATION?page=0&size=20" | jq

# Xem logs RATE_LIMIT_EXCEEDED
curl -X GET "http://localhost:8080/apps/v1/audit/logs/event/RATE_LIMIT_EXCEEDED" | jq
```

### 5.4 Xem logs trong khoảng thời gian

```bash
# Định dạng thời gian: ISO 8601
START_TIME="2026-01-28T00:00:00Z"
END_TIME="2026-01-28T23:59:59Z"

curl -X GET "http://localhost:8080/apps/v1/audit/logs/range?start=$START_TIME&end=$END_TIME" | jq
```

**Hoặc dùng date command:**
```bash
# Logs của 24h qua
START=$(date -u -d "24 hours ago" +"%Y-%m-%dT%H:%M:%SZ")
END=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

curl -X GET "http://localhost:8080/apps/v1/audit/logs/range?start=$START&end=$END" | jq
```

### 5.5 Thống kê số lượng request

```bash
# Số request trong 24h qua
curl -X GET "http://localhost:8080/apps/v1/audit/stats/$APP_ID?hours=24"

# Số request trong 1h qua
curl -X GET "http://localhost:8080/apps/v1/audit/stats/$APP_ID?hours=1"
```

**Kết quả:**
```
150
```

---

## 6. Script Test Tự động

### 6.1 Script Test Hoàn chỉnh

Tạo file `test_passwordless.sh`:

```bash
#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"

echo "========================================"
echo "  PASSWORDLESS SYSTEM TEST SUITE"
echo "========================================"
echo ""

# Test 1: Register App
echo -e "${YELLOW}[TEST 1]${NC} Registering new app..."
REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Auto Test App",
    "description": "Automated testing application",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }')

API_KEY=$(echo $REGISTER_RESPONSE | jq -r '.apiKey')
APP_ID=$(echo $REGISTER_RESPONSE | jq -r '.id')

if [ "$API_KEY" != "null" ] && [ "$API_KEY" != "" ]; then
  echo -e "${GREEN}✓ App registered successfully${NC}"
  echo "  App ID: $APP_ID"
  echo "  API Key: $API_KEY"
else
  echo -e "${RED}✗ App registration failed${NC}"
  exit 1
fi
echo ""

# Test 2: List Apps
echo -e "${YELLOW}[TEST 2]${NC} Listing all apps..."
APP_COUNT=$(curl -s -X GET $BASE_URL/apps/v1/list | jq '. | length')
echo -e "${GREEN}✓ Found $APP_COUNT apps${NC}"
echo ""

# Test 3: Send OTP
echo -e "${YELLOW}[TEST 3]${NC} Sending OTP..."
OTP_RESPONSE=$(curl -s -X POST $BASE_URL/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "sms",
    "destination": "+84912345678"
  }')

SESSION_ID=$(echo $OTP_RESPONSE | jq -r '.operationId')

if [ "$SESSION_ID" != "null" ] && [ "$SESSION_ID" != "" ]; then
  echo -e "${GREEN}✓ OTP sent successfully${NC}"
  echo "  Session ID: $SESSION_ID"
else
  echo -e "${RED}✗ OTP sending failed${NC}"
  echo "  Response: $OTP_RESPONSE"
fi
echo ""

# Test 4: Register TOTP
echo -e "${YELLOW}[TEST 4]${NC} Registering TOTP..."
TOTP_RESPONSE=$(curl -s -X POST $BASE_URL/totp/v1/register \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test.user@example.com"
  }')

TOTP_URI=$(echo $TOTP_RESPONSE | jq -r '.uri')

if [ "$TOTP_URI" != "null" ] && [ "$TOTP_URI" != "" ]; then
  echo -e "${GREEN}✓ TOTP registered successfully${NC}"
  echo "  URI: $TOTP_URI"
else
  echo -e "${RED}✗ TOTP registration failed${NC}"
fi
echo ""

# Test 5: Check Audit Logs
echo -e "${YELLOW}[TEST 5]${NC} Checking audit logs..."
LOG_COUNT=$(curl -s -X GET "$BASE_URL/apps/v1/audit/logs/app/$APP_ID?page=0&size=10" | jq '.content | length')
echo -e "${GREEN}✓ Found $LOG_COUNT audit log entries${NC}"
echo ""

# Test 6: Get Stats
echo -e "${YELLOW}[TEST 6]${NC} Getting request statistics..."
STATS=$(curl -s -X GET "$BASE_URL/apps/v1/audit/stats/$APP_ID?hours=1")
echo -e "${GREEN}✓ Request count (last hour): $STATS${NC}"
echo ""

# Test 7: Deactivate App
echo -e "${YELLOW}[TEST 7]${NC} Deactivating app..."
curl -s -X POST $BASE_URL/apps/v1/$APP_ID/deactivate > /dev/null
APP_STATUS=$(curl -s -X GET $BASE_URL/apps/v1/$APP_ID | jq -r '.active')

if [ "$APP_STATUS" == "false" ]; then
  echo -e "${GREEN}✓ App deactivated successfully${NC}"
else
  echo -e "${RED}✗ App deactivation failed${NC}"
fi
echo ""

# Test 8: Activate App
echo -e "${YELLOW}[TEST 8]${NC} Reactivating app..."
curl -s -X POST $BASE_URL/apps/v1/$APP_ID/activate > /dev/null
APP_STATUS=$(curl -s -X GET $BASE_URL/apps/v1/$APP_ID | jq -r '.active')

if [ "$APP_STATUS" == "true" ]; then
  echo -e "${GREEN}✓ App reactivated successfully${NC}"
else
  echo -e "${RED}✗ App reactivation failed${NC}"
fi
echo ""

# Cleanup (optional)
echo -e "${YELLOW}[CLEANUP]${NC} Do you want to delete the test app? (y/n)"
read -r CLEANUP_CHOICE

if [ "$CLEANUP_CHOICE" == "y" ]; then
  curl -s -X DELETE $BASE_URL/apps/v1/$APP_ID
  echo -e "${GREEN}✓ Test app deleted${NC}"
else
  echo "Test app kept: $APP_ID"
  echo "API Key: $API_KEY"
fi

echo ""
echo "========================================"
echo "  ALL TESTS COMPLETED"
echo "========================================"
```

**Chạy script:**
```bash
chmod +x test_passwordless.sh
./test_passwordless.sh
```

### 6.2 Script Test OTP Flow Hoàn chỉnh

Tạo file `test_otp_flow.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
API_KEY="your-api-key-here"
PHONE="+84912345678"

echo "=== OTP FLOW TEST ==="
echo ""

# Step 1: Send OTP
echo "1. Sending OTP to $PHONE..."
SESSION_RESPONSE=$(curl -s -X POST $BASE_URL/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"sender\": \"sms\", \"destination\": \"$PHONE\"}")

SESSION_ID=$(echo $SESSION_RESPONSE | jq -r '.operationId')
echo "   Session ID: $SESSION_ID"
echo ""

# Step 2: Wait for user to enter OTP
echo "2. Enter the OTP code you received:"
read -r OTP_CODE

# Step 3: Verify OTP
echo "3. Verifying OTP..."
VERIFY_RESPONSE=$(curl -s -X POST $BASE_URL/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"destination\": \"$PHONE\", \"otp\": \"$OTP_CODE\"}")

VERIFIED=$(echo $VERIFY_RESPONSE | jq -r '.verified')

if [ "$VERIFIED" == "true" ]; then
  echo "   ✓ OTP verified successfully!"
else
  echo "   ✗ OTP verification failed"
  echo "   Response: $VERIFY_RESPONSE"
fi
```

---

## 7. Troubleshooting

### 7.1 Lỗi thường gặp

#### Lỗi: "Missing API key"

```bash
# Response
{
  "error": "Unauthorized",
  "message": "Missing API key for path: /otp/v1/send from IP: 127.0.0.1"
}
```

**Giải pháp:**
- Thêm header `X-API-Key`
- Kiểm tra API key có đúng không
- Kiểm tra app đã được activate chưa

#### Lỗi: Rate limit exceeded

```bash
# Response
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded"
}
```

**Giải pháp:**
- Đợi 1 phút rồi thử lại
- Tăng rate limit khi đăng ký app
- Kiểm tra audit logs: `/apps/v1/audit/logs/event/RATE_LIMIT_EXCEEDED`

#### Lỗi: Invalid OTP

```bash
# Response
{
  "verified": false
}
```

**Giải pháp:**
- OTP có thời hạn 3 phút
- Mỗi OTP chỉ dùng được 1 lần
- Kiểm tra destination/sessionId có đúng không

### 7.2 Debug Tips

**1. Kiểm tra verbose output:**
```bash
curl -v -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sender": "sms", "destination": "+84912345678"}'
```

**2. Lưu response vào file:**
```bash
curl -X GET http://localhost:8080/apps/v1/list > apps.json
cat apps.json | jq
```

**3. Test với http code:**
```bash
curl -w "\nHTTP Code: %{http_code}\n" \
  -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sender": "sms", "destination": "+84912345678"}'
```

**4. Kiểm tra timing:**
```bash
curl -w "\nTime: %{time_total}s\n" \
  -X GET http://localhost:8080/apps/v1/list
```

### 7.3 Variables Helper Script

Tạo file `env_setup.sh`:

```bash
#!/bin/bash

# Load environment variables
export BASE_URL="http://localhost:8080"
export API_KEY="your-api-key-here"
export APP_ID="your-app-id-here"

# Helper functions
send_otp() {
  curl -X POST $BASE_URL/otp/v1/send \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"sender\": \"$1\", \"destination\": \"$2\"}"
}

verify_otp() {
  curl -X POST $BASE_URL/otp/v1/verify \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"destination\": \"$1\", \"otp\": \"$2\"}"
}

list_apps() {
  curl -X GET $BASE_URL/apps/v1/list | jq
}

echo "Environment loaded!"
echo "BASE_URL: $BASE_URL"
echo "API_KEY: $API_KEY"
echo ""
echo "Available functions:"
echo "  send_otp <sender> <destination>"
echo "  verify_otp <destination> <otp>"
echo "  list_apps"
```

**Sử dụng:**
```bash
source env_setup.sh
send_otp "sms" "+84912345678"
verify_otp "+84912345678" "123456"
list_apps
```

---

## 8. Best Practices

### 8.1 Bảo mật API Key

**❌ KHÔNG BAO GIỜ:**
- Commit API key vào Git
- Share API key qua email/chat
- Hardcode API key trong source code

**✅ NÊN:**
- Dùng environment variables
- Dùng secret management (Vault, AWS Secrets Manager)
- Rotate API key định kỳ

### 8.2 Testing trong Production

```bash
# Sử dụng biến môi trường
export PROD_URL="https://auth.yourcompany.com"
export PROD_API_KEY="pk_prod_xxxxx"

# Test với production
curl -X POST $PROD_URL/otp/v1/send \
  -H "X-API-Key: $PROD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sender": "sms", "destination": "+84912345678"}'
```

### 8.3 Monitoring

**Thiết lập monitoring script:**
```bash
#!/bin/bash
# monitor.sh

while true; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
  
  if [ "$STATUS" == "200" ]; then
    echo "$(date): System UP ✓"
  else
    echo "$(date): System DOWN ✗ (HTTP $STATUS)"
    # Send alert
  fi
  
  sleep 60
done
```

---

## 9. Tài liệu tham khảo

- **API Documentation (English):** `/docs/API_DOCUMENTATION.md`
- **Architecture:** `/docs/IAM_ARCHITECTURE.md`
- **Implementation Status:** `/docs/IMPLEMENTATION_STATUS_VI.md`
- **WebAuthn Test Interface:** `http://localhost:8080/webauthn/test`

---

## 10. Liên hệ & Support

Nếu gặp vấn đề:
1. Kiểm tra audit logs
2. Xem server logs
3. Tham khảo troubleshooting section
4. Tạo issue trên GitHub

---

**Phiên bản:** 1.0
**Cập nhật:** 2026-01-28
**Tác giả:** IAM Development Team

