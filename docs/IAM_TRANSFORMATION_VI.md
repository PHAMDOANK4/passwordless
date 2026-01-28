# Chuyển Đổi Sang Nền Tảng IAM - Tài Liệu Tiếng Việt

## Tổng Quan

Hệ thống đã được chuyển đổi để hoạt động như một nền tảng IAM (Identity and Access Management) đầy đủ, tương tự như Google Workspace hoặc Microsoft Azure AD. Điều này cho phép quản lý xác thực cho tất cả tài khoản trong một domain/tổ chức.

## Những Gì Đã Được Triển Khai

### 1. Quản Lý Domain (Tổ Chức)

**Domain** đại diện cho một tổ chức/công ty, ví dụ:
- `company.com` 
- `organization.vn`
- `mycompany.edu.vn`

Mỗi domain có:
- **Tên domain duy nhất**: Không trùng lặp trong toàn hệ thống
- **Tên hiển thị**: Tên công ty/tổ chức
- **Email chủ sở hữu**: Người quản trị domain
- **Chính sách MFA**: Bắt buộc MFA cho toàn bộ domain hay không
- **Cấu hình SSO**: Hỗ trợ đăng nhập một lần
- **Giới hạn user**: Số lượng user tối đa trong domain
- **Branding tùy chỉnh**: Logo, trang đăng nhập riêng

### 2. Quản Lý User (Người Dùng)

**User** đại diện cho từng tài khoản người dùng trong domain:
- **Email**: Định danh duy nhất (user@domain.com)
- **Thuộc về domain**: Mỗi user thuộc một domain cụ thể
- **Thông tin cá nhân**: Họ, tên, số điện thoại, ảnh đại diện
- **Trạng thái**: 
  - ACTIVE (Đang hoạt động)
  - SUSPENDED (Bị đình chỉ)
  - DELETED (Đã xóa)
  - PENDING_VERIFICATION (Chờ xác thực)
- **Vai trò**:
  - SUPER_ADMIN: Quản trị toàn hệ thống
  - DOMAIN_ADMIN: Quản trị domain
  - USER: Người dùng thông thường
  - GUEST: Người dùng khách
- **Bảo mật**:
  - Theo dõi số lần đăng nhập thất bại
  - Khóa tài khoản tự động khi có nghi ngờ
  - Lưu lịch sử đăng nhập (thời gian, IP)
  - Hỗ trợ MFA cá nhân

## Hoạt Động Như Google/Microsoft

### 1. Tổ Chức Theo Domain

**Giống Google Workspace:**
```
Công ty A đăng ký domain: companyA.com
- admin@companyA.com (Quản trị viên)
- john@companyA.com (Nhân viên)
- jane@companyA.com (Nhân viên)

Công ty B đăng ký domain: companyB.vn
- admin@companyB.vn (Quản trị viên)
- nguyen@companyB.vn (Nhân viên)
- tran@companyB.vn (Nhân viên)
```

Mỗi công ty quản lý user của riêng mình, hoàn toàn độc lập với nhau.

### 2. Quản Lý User Tập Trung

**Giống Azure AD:**
- Admin domain có thể tạo/xóa/sửa user
- Phân quyền theo vai trò
- Áp dụng chính sách bảo mật thống nhất
- Theo dõi hoạt động của user

### 3. Xác Thực Đa Yếu Tố (MFA)

**Giống cả Google và Microsoft:**
- Hỗ trợ nhiều phương thức:
  - TOTP (Google Authenticator)
  - SMS OTP
  - Email OTP
  - WebAuthn (Vân tay, FaceID, USB key)
- Chính sách MFA áp dụng cho cả domain
- User có thể chọn phương thức ưa thích

## Ví Dụ Sử Dụng

### 1. Tạo Domain (Đăng Ký Tổ Chức)

```bash
POST /iam/v1/domains
{
  "domainName": "mycompany.vn",
  "displayName": "Công Ty Của Tôi",
  "description": "Công ty công nghệ",
  "ownerEmail": "admin@mycompany.vn",
  "requireMfa": true,
  "maxUsers": 500
}
```

### 2. Tạo User Trong Domain

```bash
POST /iam/v1/domains/{domainId}/users
{
  "email": "nguyen.van.a@mycompany.vn",
  "firstName": "Nguyễn Văn",
  "lastName": "A",
  "phoneNumber": "+84901234567",
  "role": "USER",
  "mfaEnabled": true,
  "preferredMfaMethod": "TOTP"
}
```

### 3. Quy Trình Đăng Nhập

1. User nhập email: `nguyen.van.a@mycompany.vn`
2. Hệ thống xác định domain: `mycompany.vn`
3. Kiểm tra chính sách MFA của domain
4. Áp dụng các chính sách xác thực của domain
5. User xác thực bằng WebAuthn/OTP/TOTP
6. Tạo phiên đăng nhập với ngữ cảnh domain

### 4. Thao Tác Của Domain Admin

```bash
# Liệt kê tất cả user trong domain
GET /iam/v1/domains/{domainId}/users?page=0&size=20

# Đình chỉ user
PATCH /iam/v1/domains/{domainId}/users/{userId}
{
  "status": "SUSPENDED"
}

# Reset MFA của user
DELETE /iam/v1/domains/{domainId}/users/{userId}/mfa
```

## Các Giai Đoạn Tiếp Theo

### Giai Đoạn 2: API Quản Trị Domain (Sắp triển khai)
- CRUD endpoints cho domain
- Endpoints quản lý user
- Quản lý cài đặt domain
- Thao tác hàng loạt trên user

### Giai Đoạn 3: SSO & Federation (Sắp triển khai)
- Tích hợp SAML 2.0
- OAuth 2.0/OIDC provider
- Cấu hình IDP bên ngoài
- API quản lý session

### Giai Đoạn 4: Kiểm Soát Truy Cập (Sắp triển khai)
- RBAC (Role-Based Access Control)
- Hệ thống phân quyền
- Chính sách truy cập tài nguyên
- Tích hợp ứng dụng

### Giai Đoạn 5: Bảo Mật & Tuân Thủ (Sắp triển khai)
- Audit logging nâng cao
- Báo cáo tuân thủ (SOC 2, GDPR)
- Chính sách bảo mật
- IP whitelist/blacklist

## Lợi Ích

1. **Quản Lý Tập Trung**: Một nơi quản lý tất cả user
2. **Cách Ly Domain**: Hoàn toàn tách biệt giữa các tổ chức
3. **Chính Sách Linh Hoạt**: Áp dụng theo domain hoặc từng user
4. **Khả Năng Mở Rộng**: Hỗ trợ nhiều domain và hàng nghìn user
5. **Sẵn Sàng Doanh Nghiệp**: Đầy đủ tính năng cho khách hàng doanh nghiệp
6. **Tuân Thủ**: Audit trail và tính năng bảo mật cho quy định

## Kết Luận

Hệ thống đã được nâng cấp từ một dịch vụ xác thực đơn giản thành một nền tảng IAM toàn diện, phù hợp cho doanh nghiệp, tương tự như Google Workspace hoặc Microsoft Azure AD, trong khi vẫn duy trì khả năng xác thực không mật khẩu cốt lõi.

## Tài Liệu Chi Tiết

Xem thêm tài liệu tiếng Anh chi tiết tại: `docs/IAM_TRANSFORMATION.md`
