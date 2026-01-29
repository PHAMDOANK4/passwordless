# HƯỚNG DẪN NHANH: Test OTP qua Email bằng Curl

## 🎯 Tài liệu chi tiết đã có sẵn!

Tài liệu hướng dẫn đầy đủ (919 dòng) đã tồn tại tại:
```
📄 OTP_EMAIL_TESTING_GUIDE_VI.md
```

## ⚡ Quick Start (3 bước)

### Bước 1: Chuẩn bị API Key

```bash
# Đăng ký app để lấy API key
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OTP Test App",
    "description": "Testing OTP via email"
  }' | jq

# Lưu API key
export API_KEY="pk_aGVsbG93b3JsZA..."
```

### Bước 2: Gửi OTP qua Email

```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "email",
    "destination": "user@example.com"
  }' | jq
```

**Kết quả:**
```json
{
  "sessionId": "993e61be-23cf-412d-8273-f02e316e8689",
  "destination": "user@example.com",
  "expiresAt": "2026-01-29T10:03:00Z"
}
```

### Bước 3: Xác minh OTP

```bash
# Kiểm tra email nhận OTP (6 chữ số)
# Sau đó verify:

curl -X POST http://localhost:8080/otp/v1/verify \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "user@example.com",
    "otp": "123456"
  }' | jq
```

**Kết quả thành công:**
```json
{
  "verified": true
}
```

---

## 📚 Tài liệu chi tiết

Đọc hướng dẫn đầy đủ:
```bash
cat OTP_EMAIL_TESTING_GUIDE_VI.md
```

Hoặc mở bằng editor:
```bash
less OTP_EMAIL_TESTING_GUIDE_VI.md
# hoặc
nano OTP_EMAIL_TESTING_GUIDE_VI.md
```

## 🔍 Nội dung tài liệu đầy đủ bao gồm:

✅ **Flow hoàn chỉnh** - Từng bước chi tiết  
✅ **Ví dụ thực tế** - 3 scenarios khác nhau  
✅ **Hai phương thức verify** - Destination vs SessionId  
✅ **Troubleshooting** - 5 lỗi thường gặp + giải pháp  
✅ **Automation scripts** - 3 bash scripts sẵn sàng dùng  
✅ **Best practices** - Security, error handling, rate limiting  
✅ **So sánh** - Email vs SMS  
✅ **Quick reference** - Copy-paste commands  

---

## 🛠️ Bash Scripts có sẵn

Trong tài liệu có 3 scripts hoàn chỉnh:

1. **test_otp_email.sh** - Interactive mode (nhập email & OTP)
2. **test_otp_email_auto.sh** - Automated testing
3. **batch_test_otp_email.sh** - Test nhiều emails cùng lúc

Copy scripts từ tài liệu và sử dụng ngay!

---

## 🔗 Tài liệu liên quan

- **OTP_EMAIL_TESTING_GUIDE_VI.md** - Guide đầy đủ này
- **CURL_TESTING_GUIDE_VI.md** - Test tất cả chức năng
- **QUICK_CURL_GUIDE.md** - Quick reference
- **Swagger UI** - http://localhost:8080/swagger-ui/

---

## 💡 Tips

**Email không đến?**
- Check spam folder
- Thử email khác (Gmail, Outlook)
- Check application logs

**OTP không hợp lệ?**
- Check OTP đúng 6 chữ số
- OTP có hạn 3 phút
- Mỗi OTP chỉ dùng 1 lần

**Chi tiết troubleshooting:** Xem section 6 trong `OTP_EMAIL_TESTING_GUIDE_VI.md`

---

**Đọc hướng dẫn đầy đủ để hiểu rõ hơn!** 📚
