# Xây dựng và Triển khai Thực nghiệm Hệ thống

## 1. Mục tiêu

Tài liệu này mô tả quy trình xây dựng, triển khai thực nghiệm và đánh giá vận hành hệ thống Passwordless IdP để đưa vào báo cáo kỹ thuật/luận văn. Mục tiêu gồm:

1. Chuẩn hóa quy trình build và deploy có thể lặp lại.
2. Xác nhận hệ thống hoạt động đúng với các luồng xác thực chính.
3. Thu thập minh chứng thực nghiệm cho các tiêu chí: tính đúng chức năng, tính sẵn sàng dịch vụ, và khả năng vận hành.

## 2. Kiến trúc thực nghiệm

Mô hình triển khai thực nghiệm sử dụng Docker Compose với 5 thành phần chính:

1. passwordless-service: ứng dụng Spring Boot xử lý nghiệp vụ IdP.
2. mysql: lưu trữ dữ liệu bền vững (users, tokens, sessions, oauth clients, audit logs).
3. redis: lưu trạng thái ngắn hạn (challenge/session active cache).
4. nginx: reverse proxy + TLS termination.
5. mailhog: mô phỏng SMTP trong môi trường thử nghiệm.

Luồng truy cập:

Client -> Nginx (443) -> passwordless-service (8080) -> MySQL/Redis

## 3. Môi trường xây dựng và chạy thử

### 3.1. Yêu cầu phần mềm

1. JDK 17.
2. Maven Wrapper (đã có sẵn trong dự án).
3. Docker Engine và Docker Compose.

### 3.2. Cấu hình chính

1. Runtime backend:
- Spring Boot 3.2.5
- Java 17

2. Cơ sở dữ liệu và state:
- MySQL 8.0
- Redis 7

3. Cấu hình xác thực:
- OAuth2/OIDC token issuer và lifetime trong application.yml
- WebAuthn RP ID và Origin theo môi trường deploy

### 3.3. Lưu ý môi trường nội bộ

Vì môi trường thực nghiệm bật Nginx TLS và host passwordless.actvn, cần ánh xạ host cục bộ về máy chạy Docker khi kiểm thử trình duyệt.

## 4. Quy trình xây dựng (Build)

### 4.1. Build từ source

```bash
./mvnw clean package -DskipTests
```

Kết quả mong đợi:
- Sinh artifact jar tại thư mục target.

### 4.2. Chạy kiểm thử tự động

1. Chạy toàn bộ test:

```bash
./mvnw test -DskipTests=false
```

2. Chạy test với cấu hình bỏ qua integration tests:

```bash
./mvnw test -DskipITs
```

Ghi nhận thực nghiệm trong phiên gần nhất:
- Lệnh test đầy đủ có phát sinh lỗi môi trường.
- Lệnh test với tùy chọn skipITs hoàn thành thành công.

## 5. Quy trình triển khai thực nghiệm

### 5.1. Khởi động toàn bộ stack

```bash
docker compose up -d --build
```

Trường hợp cần áp dụng lại cấu hình proxy:

```bash
docker compose up -d --force-recreate nginx
```

### 5.2. Kiểm tra trạng thái dịch vụ

```bash
docker compose ps
```

Điều kiện đạt:
- mysql healthy
- passwordless-service running
- nginx running
- redis running

### 5.3. Kiểm tra truy cập ứng dụng

1. Truy cập giao diện người dùng IdP qua HTTPS.
2. Kiểm tra swagger-ui.
3. Kiểm tra endpoint health (nếu bật actuator).

## 6. Kịch bản thực nghiệm chức năng

### 6.1. Kịch bản A: Đăng ký và đăng nhập qua IdP

1. Đăng ký tài khoản mới.
2. Đăng nhập và xác minh luồng MFA theo phương thức được chọn.
3. Kiểm tra danh sách session sau đăng nhập.

Tiêu chí đạt:
- Đăng nhập thành công.
- Tạo session mới trong hệ thống.

### 6.2. Kịch bản B: WebAuthn (USB key/Passkey)

1. Gọi begin đăng ký WebAuthn.
2. Hoàn tất finish đăng ký với thiết bị bảo mật.
3. Thực hiện login begin/finish.

Tiêu chí đạt:
- Credential được lưu thành công.
- Người dùng đăng nhập thành công bằng WebAuthn.

### 6.3. Kịch bản C: OAuth2 Authorization Code + PKCE

1. Client điều hướng người dùng đến authorize endpoint.
2. Nhận authorization code hợp lệ.
3. Đổi code lấy token qua token endpoint.
4. Gọi userinfo/introspect để xác nhận trạng thái token.

Tiêu chí đạt:
- Code chỉ dùng một lần.
- Token trả về đúng định dạng và hết hạn theo cấu hình.

### 6.4. Kịch bản D: Thu hồi session/token

1. Thu hồi 1 session cụ thể.
2. Kiểm tra token liên quan bị vô hiệu.
3. Thu hồi toàn bộ phiên của người dùng.

Tiêu chí đạt:
- Session chuyển revoked.
- Token không còn hợp lệ sau thu hồi.

## 7. Chỉ tiêu đánh giá thực nghiệm

### 7.1. Nhóm chỉ tiêu chức năng

1. Tỷ lệ kịch bản chạy thành công.
2. Tính đúng logic begin/finish cho WebAuthn.
3. Tính đúng flow OAuth2 PKCE.

### 7.2. Nhóm chỉ tiêu vận hành

1. Thời gian khởi động stack.
2. Tỷ lệ dịch vụ ổn định sau deploy.
3. Khả năng khôi phục khi restart container.

### 7.3. Nhóm chỉ tiêu bảo mật cơ bản

1. Xác thực challenge TTL và chống replay.
2. Kiểm tra lockout khi nhập sai nhiều lần.
3. Kiểm tra revoke token/session có hiệu lực tức thời.

## 8. Bảng ghi nhận kết quả thực nghiệm (mẫu)

| Hạng mục | Bước kiểm tra | Kết quả | Ghi chú |
|---|---|---|---|
| Build source | mvnw clean package | Pass | Sinh jar tại target |
| Unit/Service test | mvnw test -DskipITs | Pass | Phù hợp kiểm thử nhanh CI/local |
| Full test | mvnw test -DskipTests=false | Cần xử lý thêm | Có thể phụ thuộc môi trường ngoài |
| Deploy stack | docker compose up -d --build | Pass | Các container lên thành công |
| Nginx recreate | docker compose up -d --force-recreate nginx | Pass | Áp dụng lại cấu hình proxy |
| WebAuthn login | begin/finish flow | Pass/Quan sát | Kiểm tra trực tiếp bằng trình duyệt + thiết bị |
| OAuth2 PKCE | authorize -> token | Pass | Code one-time và token hợp lệ |

## 9. Kết luận thực nghiệm

Kết quả triển khai thực nghiệm cho thấy hệ thống có khả năng:

1. Xây dựng và đóng gói ổn định bằng Maven.
2. Triển khai nhất quán bằng Docker Compose với kiến trúc nhiều thành phần.
3. Đáp ứng các luồng xác thực cốt lõi: OTP/TOTP/WebAuthn và OAuth2/OIDC.
4. Hỗ trợ quản lý session/token cho yêu cầu vận hành và an toàn đăng nhập.

Để tăng độ tin cậy cho môi trường production, cần bổ sung thêm các vòng đánh giá:

1. kiểm thử tải,
2. kiểm thử bảo mật chuyên sâu,
3. đo kiểm độ sẵn sàng dài hạn,
4. chuẩn hóa pipeline full-test theo môi trường staging độc lập.
