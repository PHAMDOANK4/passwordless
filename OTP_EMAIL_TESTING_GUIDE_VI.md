# HƯỚNG DẪN TEST XÁC THỰC OTP QUA EMAIL BẰNG CURL

## Mục lục
1. [Giới thiệu](#giới-thiệu)
2. [Chuẩn bị](#chuẩn-bị)
3. [Flow hoàn chỉnh](#flow-hoàn-chỉnh-test-otp-qua-email)
4. [Ví dụ chi tiết](#ví-dụ-chi-tiết)
5. [Hai phương thức xác minh](#hai-phương-thức-xác-minh-otp)
6. [Troubleshooting](#troubleshooting)
7. [Script tự động](#script-tự-động-hoàn-chỉnh)
8. [Best Practices](#best-practices)

---

## Giới thiệu

### OTP qua Email là gì?

**OTP (One-Time Password)** là mã xác thực một lần được gửi qua email để xác nhận danh tính người dùng. 

**Đặc điểm:**
- Mã gồm 6 chữ số
- Có thời hạn sử dụng (mặc định 3 phút)
- Chỉ dùng được một lần
- Tự động xóa sau khi xác minh thành công

### Tại sao dùng Email?

| Tiêu chí | SMS | Email |
|----------|-----|-------|
| Chi phí | Cao | Miễn phí/Rẻ |
| Độ trễ | 1-30 giây | 1-5 giây |
| Độ tin cậy | Phụ thuộc mạng | Cao |
| Bảo mật | Trung bình | Cao (TLS) |
| Lưu trữ | Không | Có |

### Flow hoạt động

```
1. Client → Gửi request với email
2. Server → Tạo OTP (6 digits)
3. Server → Gửi email chứa OTP
4. User → Nhận email, lấy OTP
5. Client → Gửi OTP để verify
6. Server → Kiểm tra và xác nhận
```

---

## Chuẩn bị

### 1. Kiểm tra Server đang chạy

```bash
curl -X GET http://localhost:8080/actuator/health
```

**Kết quả mong đợi:**
```json
{
  "status": "UP"
}
```

### 2. Đăng ký App để lấy API Key

**Lệnh:**
```bash
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OTP Email Test App",
    "description": "Testing OTP via email",
    "rateLimitPerMinute": 60,
    "rateLimitPerHour": 1000
  }' | jq
```

**Kết quả:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "OTP Email Test App",
  "apiKey": "pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5",
  "active": true,
  "createdAt": "2026-01-29T10:00:00Z"
}
```

### 3. Lưu API Key vào biến môi trường

```bash
export API_KEY="pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5"
```

**Kiểm tra:**
```bash
echo $API_KEY
```

---

## Flow hoàn chỉnh Test OTP qua Email

### Bước 1: Gửi OTP qua Email

**Lệnh cơ bản:**
```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }'
```

**Lệnh với jq để format đẹp:**
```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq
```

**Response thành công:**
```json
{
  "sessionId": "993e61be-23cf-412d-8273-f02e316e8689",
  "destination": "user@example.com",
  "expiresAt": "2026-01-29T10:03:00Z"
}
```

**Lưu sessionId (optional, dùng cho phương thức 2):**
```bash
SESSION_ID=$(curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq -r '.sessionId')

echo "Session ID: $SESSION_ID"
```

### Bước 2: Kiểm tra Email

**Email sẽ có nội dung tương tự:**

```
From: noreply@yourapp.com
To: user@example.com
Subject: Your OTP Code

Your one-time password is: 123456

This code will expire in 3 minutes.

Do not share this code with anyone.
```

**Lưu OTP code:**
```bash
export OTP_CODE="123456"
```

### Bước 3: Xác minh OTP

**Phương thức 1: Theo Destination (Khuyến nghị - giống Google/Microsoft)**

```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "user@example.com",
    "otp": "123456"
  }' | jq
```

**Phương thức 2: Theo SessionId (Legacy)**

```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "'$SESSION_ID'",
    "otp": "123456"
  }' | jq
```

### Bước 4: Kiểm tra kết quả

**Response thành công:**
```json
{
  "verified": true
}
```

**Response thất bại:**
```json
{
  "verified": false,
  "error": "Invalid OTP code"
}
```

---

## Ví dụ chi tiết

### Ví dụ 1: Flow hoàn chỉnh với Email cụ thể

```bash
# 1. Set API key
export API_KEY="pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5"

# 2. Gửi OTP đến email
echo "=== Gửi OTP đến user@example.com ==="
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq

# 3. Đợi nhận email (check email inbox)
echo "Vui lòng kiểm tra email và nhập OTP code:"
read OTP_CODE

# 4. Xác minh OTP
echo "=== Xác minh OTP: $OTP_CODE ==="
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "user@example.com",
    "otp": "'$OTP_CODE'"
  }' | jq
```

### Ví dụ 2: Test với nhiều email khác nhau

```bash
# Danh sách email để test
EMAILS=(
  "alice@example.com"
  "bob@example.com"
  "charlie@example.com"
)

for email in "${EMAILS[@]}"; do
  echo "=== Testing with $email ==="
  curl -X POST http://localhost:8080/otp/v1/send \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d '{
      "sender": "email",
      "destination": "'$email'"
    }' | jq
  echo ""
  sleep 2
done
```

### Ví dụ 3: Lưu response vào file

```bash
# Gửi OTP và lưu response
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' > otp_response.json

# Đọc sessionId từ file
SESSION_ID=$(cat otp_response.json | jq -r '.sessionId')
echo "Session ID: $SESSION_ID"

# Hiển thị response đẹp
cat otp_response.json | jq
```

---

## Hai phương thức xác minh OTP

### Phương thức 1: Theo Destination (Khuyến nghị)

**Ưu điểm:**
- ✅ Đơn giản hơn (không cần lưu sessionId)
- ✅ Giống Google/Microsoft (user-friendly)
- ✅ Tự động tìm OTP mới nhất cho email
- ✅ Phù hợp cho web/mobile apps

**Nhược điểm:**
- ⚠️ Nếu gửi nhiều OTP cho cùng email, chỉ verify được cái mới nhất

**Cách dùng:**
```bash
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "user@example.com",
    "otp": "123456"
  }' | jq
```

### Phương thức 2: Theo SessionId (Legacy)

**Ưu điểm:**
- ✅ Chính xác (verify đúng OTP được tạo từ sessionId)
- ✅ Hỗ trợ multiple concurrent OTPs cho cùng user
- ✅ Tương thích ngược (backward compatible)

**Nhược điểm:**
- ⚠️ Phức tạp hơn (cần lưu sessionId)
- ⚠️ Client phải quản lý sessionId

**Cách dùng:**
```bash
# Lưu sessionId khi gửi OTP
SESSION_ID=$(curl -s -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq -r '.sessionId')

# Verify với sessionId
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "'$SESSION_ID'",
    "otp": "123456"
  }' | jq
```

### So sánh

| Tiêu chí | Destination | SessionId |
|----------|-------------|-----------|
| Độ phức tạp | Đơn giản | Phức tạp |
| Use case | Web/Mobile login | Multi-session apps |
| User experience | Tốt hơn | Bình thường |
| Độ chính xác | Cao (OTP mới nhất) | Rất cao (chính xác 100%) |
| Khuyến nghị | ✅ Ưu tiên | ⚠️ Khi cần chính xác tuyệt đối |

---

## Troubleshooting

### Lỗi 1: Email không đến

**Triệu chứng:**
```bash
# OTP sent thành công nhưng email không đến
```

**Nguyên nhân & Giải pháp:**

1. **Kiểm tra spam folder**
   - Email OTP có thể bị đánh dấu spam
   - Thêm sender vào whitelist

2. **Kiểm tra email server configuration**
   ```bash
   # Check application logs
   tail -f logs/application.log | grep -i email
   ```

3. **Kiểm tra email address hợp lệ**
   ```bash
   # Email phải có format đúng
   # Valid: user@example.com
   # Invalid: user@example, user.com
   ```

4. **Test với email khác**
   ```bash
   # Thử với Gmail, Outlook
   curl -X POST http://localhost:8080/otp/v1/send \
     -H "X-API-Key: $API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"sender": "email", "destination": "your-email@gmail.com"}' | jq
   ```

### Lỗi 2: OTP không hợp lệ

**Triệu chứng:**
```json
{
  "verified": false,
  "error": "Invalid OTP code"
}
```

**Nguyên nhân & Giải pháp:**

1. **OTP đã hết hạn (> 3 phút)**
   ```bash
   # Gửi OTP mới
   curl -X POST http://localhost:8080/otp/v1/send ...
   ```

2. **Nhập sai OTP code**
   ```bash
   # Check kỹ 6 chữ số trong email
   # Không có khoảng trắng, không có ký tự đặc biệt
   ```

3. **OTP đã được sử dụng**
   ```bash
   # Mỗi OTP chỉ dùng được 1 lần
   # Request OTP mới nếu cần verify lại
   ```

4. **Dùng sai phương thức verify**
   ```bash
   # Nếu dùng destination, đảm bảo email chính xác
   # Nếu dùng sessionId, đảm bảo sessionId đúng
   ```

### Lỗi 3: Rate Limiting

**Triệu chứng:**
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later."
}
```

**Giải pháp:**

1. **Đợi một chút rồi thử lại**
   ```bash
   # Mặc định: 60 requests/minute
   sleep 60
   ```

2. **Tăng rate limit cho app**
   ```bash
   # Khi đăng ký app, set rate limit cao hơn
   "rateLimitPerMinute": 100,
   "rateLimitPerHour": 5000
   ```

3. **Kiểm tra rate limit hiện tại**
   ```bash
   curl "http://localhost:8080/apps/v1/$APP_ID" | jq
   ```

### Lỗi 4: API Key không hợp lệ

**Triệu chứng:**
```json
{
  "error": "Unauthorized",
  "message": "Invalid API key"
}
```

**Giải pháp:**

1. **Kiểm tra API key trong header**
   ```bash
   # Đảm bảo có "X-API-Key" trong header
   curl -H "X-API-Key: $API_KEY" ...
   ```

2. **Kiểm tra API key còn active**
   ```bash
   curl http://localhost:8080/apps/v1/list | jq
   ```

3. **Regenerate API key nếu cần**
   ```bash
   curl -X POST http://localhost:8080/apps/v1/$APP_ID/regenerate-key
   ```

### Lỗi 5: Connection refused

**Triệu chứng:**
```bash
curl: (7) Failed to connect to localhost port 8080: Connection refused
```

**Giải pháp:**

1. **Kiểm tra server đang chạy**
   ```bash
   # Start server nếu chưa chạy
   mvn spring-boot:run
   ```

2. **Kiểm tra port đúng**
   ```bash
   # Default: 8080
   # Nếu đổi port, update URL
   ```

3. **Kiểm tra firewall**
   ```bash
   # Đảm bảo port 8080 không bị block
   ```

---

## Script tự động hoàn chỉnh

### Script 1: Test OTP Email - Interactive

```bash
#!/bin/bash
# File: test_otp_email.sh
# Mục đích: Test OTP qua email với interactive input

set -e

echo "╔════════════════════════════════════════╗"
echo "║   TEST OTP EMAIL - INTERACTIVE MODE    ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Kiểm tra API key
if [ -z "$API_KEY" ]; then
  echo "⚠️  API_KEY chưa được set"
  echo "Vui lòng nhập API key:"
  read API_KEY
  export API_KEY
fi

echo "✓ API Key: ${API_KEY:0:10}..."
echo ""

# Nhập email
echo "Nhập email để nhận OTP:"
read EMAIL

if [ -z "$EMAIL" ]; then
  echo "❌ Email không được để trống"
  exit 1
fi

echo ""
echo "=== BƯỚC 1: GỬI OTP ==="
echo "Đang gửi OTP đến: $EMAIL"

RESPONSE=$(curl -s -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "'$EMAIL'"
  }')

echo "$RESPONSE" | jq

# Kiểm tra response có sessionId không
SESSION_ID=$(echo "$RESPONSE" | jq -r '.sessionId')

if [ "$SESSION_ID" == "null" ]; then
  echo "❌ Gửi OTP thất bại!"
  echo "$RESPONSE"
  exit 1
fi

echo "✓ OTP đã được gửi!"
echo "✓ Session ID: $SESSION_ID"
echo ""

# Đợi user nhập OTP
echo "=== BƯỚC 2: KIỂM TRA EMAIL ==="
echo "Vui lòng kiểm tra email: $EMAIL"
echo "Nhập mã OTP (6 chữ số):"
read OTP_CODE

if [ -z "$OTP_CODE" ]; then
  echo "❌ OTP không được để trống"
  exit 1
fi

echo ""
echo "=== BƯỚC 3: XÁC MINH OTP ==="
echo "Đang xác minh OTP: $OTP_CODE"

VERIFY_RESPONSE=$(curl -s -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "'$EMAIL'",
    "otp": "'$OTP_CODE'"
  }')

echo "$VERIFY_RESPONSE" | jq

# Kiểm tra kết quả
VERIFIED=$(echo "$VERIFY_RESPONSE" | jq -r '.verified')

echo ""
if [ "$VERIFIED" == "true" ]; then
  echo "✅ XÁC MINH THÀNH CÔNG!"
else
  echo "❌ XÁC MINH THẤT BẠI!"
fi
```

### Script 2: Test OTP Email - Automated (Mock)

```bash
#!/bin/bash
# File: test_otp_email_auto.sh
# Mục đích: Test tự động (với mock OTP)

set -e

API_KEY="${API_KEY:-pk_test_key}"
EMAIL="${1:-user@example.com}"

echo "╔════════════════════════════════════════╗"
echo "║   AUTOMATED OTP EMAIL TEST             ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "API Key: ${API_KEY:0:10}..."
echo "Email: $EMAIL"
echo ""

# Test 1: Gửi OTP
echo "[TEST 1] Gửi OTP..."
RESPONSE=$(curl -s -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "'$EMAIL'"
  }')

SESSION_ID=$(echo "$RESPONSE" | jq -r '.sessionId')

if [ "$SESSION_ID" != "null" ]; then
  echo "✓ OTP sent successfully"
  echo "  Session ID: $SESSION_ID"
else
  echo "✗ Failed to send OTP"
  echo "$RESPONSE" | jq
  exit 1
fi

# Test 2: Verify với OTP sai
echo ""
echo "[TEST 2] Verify với OTP sai (000000)..."
VERIFY_FAIL=$(curl -s -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "'$EMAIL'",
    "otp": "000000"
  }')

VERIFIED=$(echo "$VERIFY_FAIL" | jq -r '.verified')

if [ "$VERIFIED" == "false" ]; then
  echo "✓ Correctly rejected invalid OTP"
else
  echo "✗ Should have rejected invalid OTP"
fi

# Summary
echo ""
echo "╔════════════════════════════════════════╗"
echo "║   TEST SUMMARY                         ║"
echo "╠════════════════════════════════════════╣"
echo "║  ✓ Send OTP to email                  ║"
echo "║  ✓ Invalid OTP rejected               ║"
echo "║                                        ║"
echo "║  Note: Check email for actual OTP     ║"
echo "║  Then manually verify for full test   ║"
echo "╚════════════════════════════════════════╝"
```

### Script 3: Batch Test - Multiple Emails

```bash
#!/bin/bash
# File: batch_test_otp_email.sh
# Mục đích: Test với nhiều email

API_KEY="${API_KEY:-pk_test_key}"

EMAILS=(
  "alice@example.com"
  "bob@example.com"
  "charlie@example.com"
  "david@example.com"
)

echo "Batch Testing OTP Email"
echo "========================"
echo ""

SUCCESS_COUNT=0
FAIL_COUNT=0

for email in "${EMAILS[@]}"; do
  echo "Testing: $email"
  
  RESPONSE=$(curl -s -X POST http://localhost:8080/otp/v1/send \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -d '{
      "sender": "email",
      "destination": "'$email'"
    }')
  
  SESSION_ID=$(echo "$RESPONSE" | jq -r '.sessionId')
  
  if [ "$SESSION_ID" != "null" ]; then
    echo "  ✓ Success"
    ((SUCCESS_COUNT++))
  else
    echo "  ✗ Failed"
    ((FAIL_COUNT++))
  fi
  
  sleep 2
done

echo ""
echo "Results:"
echo "  Success: $SUCCESS_COUNT"
echo "  Failed: $FAIL_COUNT"
```

### Cách sử dụng Scripts

**1. Tạo file và cho phép thực thi:**
```bash
chmod +x test_otp_email.sh
chmod +x test_otp_email_auto.sh
chmod +x batch_test_otp_email.sh
```

**2. Chạy interactive test:**
```bash
export API_KEY="your_api_key_here"
./test_otp_email.sh
```

**3. Chạy automated test:**
```bash
./test_otp_email_auto.sh user@example.com
```

**4. Chạy batch test:**
```bash
./batch_test_otp_email.sh
```

---

## Best Practices

### 1. Bảo mật API Key

**✅ Nên:**
```bash
# Lưu trong environment variable
export API_KEY="pk_..."

# Hoặc trong file .env
echo "API_KEY=pk_..." > .env
source .env
```

**❌ Không nên:**
```bash
# Hardcode trong script
curl -H "X-API-Key: pk_hardcoded_key" ...

# Commit vào Git
git add script_with_api_key.sh
```

### 2. Error Handling

**✅ Nên:**
```bash
# Check response status
RESPONSE=$(curl -s -w "\n%{http_code}" ...)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" != "200" ]; then
  echo "Error: HTTP $HTTP_CODE"
  exit 1
fi
```

**❌ Không nên:**
```bash
# Không check response
curl ... | jq
# Nếu fail, jq sẽ error
```

### 3. Rate Limiting

**✅ Nên:**
```bash
# Thêm delay giữa các requests
for email in "${EMAILS[@]}"; do
  curl ...
  sleep 2  # Đợi 2 giây
done
```

**❌ Không nên:**
```bash
# Gửi liên tục không delay
for email in "${EMAILS[@]}"; do
  curl ...
done
```

### 4. Logging

**✅ Nên:**
```bash
# Log ra file
curl ... | tee otp_test.log | jq

# Log với timestamp
echo "[$(date)] Sending OTP..." >> otp_test.log
```

**❌ Không nên:**
```bash
# Không lưu log
curl ... > /dev/null
```

### 5. Testing Environment

**✅ Nên:**
```bash
# Phân biệt môi trường
if [ "$ENV" == "production" ]; then
  BASE_URL="https://api.production.com"
else
  BASE_URL="http://localhost:8080"
fi
```

**❌ Không nên:**
```bash
# Test trực tiếp trên production
curl https://api.production.com/otp/v1/send ...
```

---

## Tổng kết

### Checklist hoàn chỉnh

- [ ] Server đang chạy (`/actuator/health`)
- [ ] Đã đăng ký app và có API key
- [ ] API key được lưu trong environment variable
- [ ] Gửi OTP qua email thành công
- [ ] Email đến inbox (check spam nếu không thấy)
- [ ] Xác minh OTP thành công
- [ ] Test với nhiều email khác nhau
- [ ] Test error cases (OTP sai, hết hạn, etc.)
- [ ] Đã test rate limiting
- [ ] Scripts chạy được và có error handling

### Quick Reference

```bash
# 1. Set API key
export API_KEY="pk_your_key"

# 2. Gửi OTP
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"sender": "email", "destination": "user@example.com"}' | jq

# 3. Verify OTP (destination-based)
curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"destination": "user@example.com", "otp": "123456"}' | jq
```

### Tài liệu tham khảo

- **Comprehensive Guide:** `docs/CURL_TESTING_GUIDE_VI.md`
- **Quick Guide:** `QUICK_CURL_GUIDE.md`
- **API Documentation:** `docs/API_DOCUMENTATION.md`
- **Swagger UI:** `http://localhost:8080/swagger-ui/`

---

**Chúc bạn test thành công! 🎉**
