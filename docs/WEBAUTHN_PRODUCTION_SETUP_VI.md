# Hướng Dẫn Cấu Hình WebAuthn Cho Production Domain

## 📋 Tổng quan

Tài liệu này hướng dẫn chi tiết cách cấu hình và test WebAuthn trên production domain (ví dụ: `authentication.k4.vn`).

---

## ⚠️ Lỗi Thường Gặp

### 1. Lỗi 403 Forbidden

**Triệu chứng:**
```
❌ Error: HTTP error! status: 403
```

**Nguyên nhân:**
- Security configuration không cho phép truy cập `/webauthn/test`
- CORS configuration không cho phép origin của production domain

**Giải pháp:**
✅ **ĐÃ ĐƯỢC SỬA** - Commit mới nhất đã fix:
- Security config cho phép `/webauthn/test` và `/webauthn/v1/**`
- CORS cho phép `https://authentication.k4.vn`

### 2. Origin Mismatch Error

**Triệu chứng:**
```
❌ Error: The relying party ID is not a registrable domain suffix of, nor equal to the current domain
```

**Nguyên nhân:**
- `rpId` trong `application.yml` chưa được cập nhật cho production domain
- Vẫn đang dùng `rpId: localhost`

**Giải pháp:**
```yaml
# ❌ SAI - Dùng localhost
webauthn:
  settings:
    rpId: localhost
    origin: "http://localhost:8080"

# ✅ ĐÚNG - Dùng production domain
webauthn:
  settings:
    rpId: authentication.k4.vn
    origin: "https://authentication.k4.vn"
```

### 3. SSL/HTTPS Required Error

**Triệu chứng:**
```
❌ Error: The operation is insecure
```

**Nguyên nhân:**
- WebAuthn chỉ hoạt động trên HTTPS (hoặc localhost)
- Server chưa cấu hình SSL

**Giải pháp:**
- Bắt buộc phải dùng HTTPS cho production
- Cấu hình SSL certificate cho domain
- Xem hướng dẫn ở phần "Cấu hình HTTPS"

---

## 🔧 Cấu Hình Chi Tiết

### Bước 1: Cập nhật application.yml

**File:** `src/main/resources/application.yml`

```yaml
webauthn:
  settings:
    timeout: 60000
    rpId: authentication.k4.vn              # ← Thay bằng domain của bạn
    origin: "https://authentication.k4.vn"  # ← Thay bằng HTTPS URL của bạn
```

**Lưu ý:**
- `rpId` chỉ là domain name (không có `https://`)
- `origin` phải có protocol (`https://`)
- Hai giá trị này phải match với domain thực tế

### Bước 2: Environment Variables (Khuyến nghị)

**Thay vì hardcode trong application.yml, dùng environment variables:**

```bash
# .env file hoặc system environment
export WEBAUTHN_SETTINGS_RPID=authentication.k4.vn
export WEBAUTHN_SETTINGS_ORIGIN=https://authentication.k4.vn
```

**Cập nhật application.yml:**
```yaml
webauthn:
  settings:
    rpId: ${WEBAUTHN_SETTINGS_RPID:localhost}
    origin: ${WEBAUTHN_SETTINGS_ORIGIN:http://localhost:8080}
```

**Hoặc trong docker-compose.yml:**
```yaml
services:
  app:
    environment:
      - WEBAUTHN_SETTINGS_RPID=authentication.k4.vn
      - WEBAUTHN_SETTINGS_ORIGIN=https://authentication.k4.vn
```

### Bước 3: Xác minh CORS Configuration

**File:** `src/main/java/.../webauthn/controllers/WebAuthnController.java`

```java
@CrossOrigin(origins = {
    "http://localhost:1234",           // Development
    "http://localhost:8080",            // Local testing
    "https://authentication.k4.vn"      // Production ← Phải có dòng này
}, allowCredentials = "true")
```

**Nếu cần thêm domain khác:**
```java
@CrossOrigin(origins = {
    "http://localhost:1234",
    "http://localhost:8080",
    "https://authentication.k4.vn",
    "https://auth.another-domain.com"   // Thêm domain khác
}, allowCredentials = "true")
```

---

## 🧪 Hướng Dẫn Test

### Test 1: Kiểm tra Server Health

```bash
# Kiểm tra server đang chạy
curl -I https://authentication.k4.vn/actuator/health

# Expected output:
# HTTP/2 200
# content-type: application/json
```

### Test 2: Kiểm tra WebAuthn Test Page

```bash
# Test page có load không
curl -I https://authentication.k4.vn/webauthn/test

# Expected output:
# HTTP/2 200
# content-type: text/html
```

### Test 3: Kiểm tra WebAuthn API

```bash
# Test registration endpoint
curl -X POST https://authentication.k4.vn/webauthn/v1/register \
  -H "Content-Type: application/json" \
  -d '{"username": "test@example.com"}' \
  -v

# Expected: 200 OK với challenge data (không phải 403)
```

### Test 4: Browser Testing

**Bước 1: Mở test page**
```bash
# MacOS
open https://authentication.k4.vn/webauthn/test

# Linux
xdg-open https://authentication.k4.vn/webauthn/test

# Windows
start https://authentication.k4.vn/webauthn/test
```

**Bước 2: Test Registration**
1. Nhập username (email)
2. Click "Register"
3. Browser sẽ hiện popup WebAuthn
4. Chọn security key hoặc dùng biometric
5. Xem activity log hiện "✅ Registration successful"

**Bước 3: Test Login**
1. Nhập username (email)
2. Click "Login"
3. Browser sẽ hiện popup WebAuthn
4. Xác thực với security key/biometric
5. Xem activity log hiện "✅ Login successful"

---

## 🔒 Cấu Hình HTTPS

### Option 1: Self-Signed Certificate (Development/Testing)

```bash
# Tạo self-signed certificate
keytool -genkeypair -alias passwordless -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365 \
  -dname "CN=authentication.k4.vn" \
  -ext "SAN=dns:authentication.k4.vn"

# Cập nhật application.yml
```

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: passwordless
```

### Option 2: Let's Encrypt (Production)

```bash
# Install Certbot
sudo apt-get update
sudo apt-get install certbot

# Get certificate
sudo certbot certonly --standalone -d authentication.k4.vn

# Convert to PKCS12
sudo openssl pkcs12 -export \
  -in /etc/letsencrypt/live/authentication.k4.vn/fullchain.pem \
  -inkey /etc/letsencrypt/live/authentication.k4.vn/privkey.pem \
  -out /opt/keystore.p12 \
  -name passwordless
```

### Option 3: Nginx Reverse Proxy (Khuyến nghị)

**Cấu hình Nginx:**
```nginx
server {
    listen 443 ssl http2;
    server_name authentication.k4.vn;
    
    ssl_certificate /etc/letsencrypt/live/authentication.k4.vn/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/authentication.k4.vn/privkey.pem;
    
    # WebSocket support for WebAuthn
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 📊 Troubleshooting Checklist

### Checklist trước khi test:

- [ ] Server đang chạy và healthy (`/actuator/health` returns 200)
- [ ] HTTPS đã được cấu hình (certificate valid)
- [ ] `application.yml` có đúng `rpId` và `origin`
- [ ] CORS configuration có domain của bạn
- [ ] Security configuration cho phép `/webauthn/test` và `/webauthn/v1/**`
- [ ] Browser hỗ trợ WebAuthn (Chrome 67+, Firefox 60+, Safari 13+, Edge 18+)
- [ ] Có authenticator (Touch ID, Windows Hello, USB Security Key)

### Debug Steps:

**1. Check Browser Console:**
```javascript
// Mở DevTools (F12)
// Tab Console
// Xem có error message gì không
```

**2. Check Network Tab:**
```
// DevTools → Network tab
// Filter: XHR/Fetch
// Xem requests đến /webauthn/v1/register và /webauthn/v1/login
// Status code phải là 200, không phải 403 hoặc 500
```

**3. Check Server Logs:**
```bash
# Xem logs của Spring Boot
tail -f logs/spring.log

# Hoặc nếu dùng Docker
docker logs -f passwordless-app
```

**4. Verbose curl test:**
```bash
# Test với verbose output
curl -vvv https://authentication.k4.vn/webauthn/test

# Check:
# - SSL handshake thành công
# - HTTP status code
# - Response headers
# - Response body
```

---

## 🎯 FAQ

### Q1: Tôi đã config đúng nhưng vẫn bị 403?

**A:** Kiểm tra lại:
1. Đã rebuild project chưa? `mvn clean install`
2. Đã restart server chưa?
3. Browser cache đã clear chưa?
4. Config file có đúng location không? (application.yml vs application-prod.yml)

### Q2: Credential đã đăng ký với localhost, giờ chuyển sang domain mới bị lỗi?

**A:** Đây là behavior bình thường của WebAuthn. Khi thay đổi `rpId`:
- Credentials cũ sẽ không hoạt động
- User phải đăng ký lại authenticator
- Đây là tính năng bảo mật, không phải bug

**Giải pháp:**
- Development: Dùng localhost
- Production: Dùng domain thật
- Không thay đổi rpId sau khi deploy production

### Q3: Test page hiện nhưng registration/login không hoạt động?

**A:** Kiểm tra:
1. **Browser Console có errors?**
   - Origin mismatch: Check rpId và origin trong config
   - CORS error: Check CORS configuration
   
2. **Network tab có requests?**
   - Requests đến API server chưa?
   - Status code là gì?
   
3. **Server logs có gì?**
   - Check WebAuthn service logs
   - Check for exceptions

### Q4: Làm sao để support nhiều domains?

**A:** Thêm tất cả domains vào CORS:

```java
@CrossOrigin(origins = {
    "https://authentication.k4.vn",
    "https://auth.anotherdomain.com",
    "https://login.thirddomain.org"
}, allowCredentials = "true")
```

**Lưu ý:** Mỗi domain phải có rpId riêng hoặc dùng subdomain chung.

### Q5: WebAuthn hoạt động trên localhost nhưng không hoạt động trên server?

**A:** Kiểm tra:
1. HTTPS có được cấu hình đúng không?
2. Certificate có valid không? (không expired, trusted CA)
3. Domain name có resolve đúng IP không?
4. Firewall có block port 443 không?

---

## 📚 Tài Liệu Tham Khảo

### Nội bộ:
- [Curl Testing Guide](CURL_TESTING_GUIDE_VI.md) - Test các chức năng bằng curl
- [Quick Curl Guide](../QUICK_CURL_GUIDE.md) - Quick reference
- [IAM Architecture](IAM_ARCHITECTURE.md) - System architecture
- [Implementation Status](IMPLEMENTATION_STATUS_VI.md) - Implementation tracking

### External Resources:
- [WebAuthn Spec](https://www.w3.org/TR/webauthn/) - W3C specification
- [webauthn4j Documentation](https://github.com/webauthn4j/webauthn4j) - Library docs
- [FIDO Alliance](https://fidoalliance.org/) - FIDO2/WebAuthn standards

---

## ✅ Kết Luận

Sau khi apply tất cả các fix trên:

✅ **Lỗi 403 đã được fix**
✅ **CORS configuration đã được cập nhật cho production**
✅ **Security configuration cho phép WebAuthn endpoints**
✅ **Hướng dẫn cấu hình domain đầy đủ**
✅ **Troubleshooting guide chi tiết**

**Bước tiếp theo:**
1. Cập nhật `application.yml` với domain của bạn
2. Restart server
3. Test lại trên browser
4. Enjoy passwordless authentication! 🎉

---

**Liên hệ:** Nếu vẫn gặp vấn đề, check:
- Server logs: `/logs/spring.log`
- Browser console: DevTools → Console
- Network requests: DevTools → Network → XHR
