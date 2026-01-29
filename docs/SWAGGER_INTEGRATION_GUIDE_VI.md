# Hướng Dẫn Sử Dụng Swagger/OpenAPI

Tài liệu chi tiết về cách sử dụng Swagger UI để test và document APIs trong hệ thống Passwordless Authentication.

---

## 📚 Mục Lục

1. [Giới Thiệu](#giới-thiệu)
2. [Truy Cập Swagger UI](#truy-cập-swagger-ui)
3. [Giao Diện Swagger UI](#giao-diện-swagger-ui)
4. [Test APIs Không Cần API Key](#test-apis-không-cần-api-key)
5. [Test APIs Có API Key](#test-apis-có-api-key)
6. [Các Tính Năng Swagger](#các-tính-năng-swagger)
7. [OpenAPI JSON Spec](#openapi-json-spec)
8. [Tích Hợp Vào Code](#tích-hợp-vào-code)
9. [Troubleshooting](#troubleshooting)

---

## Giới Thiệu

### Swagger/OpenAPI là gì?

**Swagger UI** là công cụ tương tác để:
- 📖 **Document APIs** - Tự động tạo tài liệu từ code
- 🧪 **Test APIs** - Test trực tiếp trên browser
- 🔍 **Explore APIs** - Khám phá tất cả endpoints
- 📋 **Generate client code** - Tạo code tự động

### Tại sao dùng Swagger?

**Trước khi có Swagger:**
```
Developer: "API này nhận parameters gì nhỉ?"
→ Phải đọc code hoặc hỏi người khác
→ Test bằng Postman/curl (phải setup)
→ Documentation outdated hoặc không có
```

**Sau khi có Swagger:**
```
Developer: Opens /swagger-ui.html
→ Thấy ngay tất cả APIs
→ Click "Try it out" → Test ngay
→ Documentation luôn cập nhật
→ Không cần Postman!
```

---

## Truy Cập Swagger UI

### URLs

**Local Development:**
```
http://localhost:8080/swagger-ui.html
```

**Production:**
```
https://authentication.k4.vn/swagger-ui.html
```

### OpenAPI JSON Spec

**OpenAPI 3.0 JSON:**
```
http://localhost:8080/v3/api-docs
https://authentication.k4.vn/v3/api-docs
```

Sử dụng để:
- Import vào Postman
- Generate client code
- API Gateway integration

---

## Giao Diện Swagger UI

### Các thành phần chính:

```
┌─────────────────────────────────────────────┐
│  Passwordless Authentication API      v1.0.0│
│  [Authorize]                         [Explore]│
├─────────────────────────────────────────────┤
│                                              │
│  Servers: ▼ http://localhost:8080           │
│                                              │
│  ▼ App Management                           │
│    POST /apps/v1/register                   │
│    GET  /apps/v1/list                       │
│    ...                                       │
│                                              │
│  ▼ OTP Authentication                       │
│    POST /otp/v1/send                        │
│    POST /otp/v1/verify                      │
│    ...                                       │
│                                              │
│  ▼ WebAuthn                                 │
│    GET  /webauthn/v1/register/challenge     │
│    POST /webauthn/v1/register/credential    │
│    ...                                       │
└─────────────────────────────────────────────┘
```

### Các nút quan trọng:

**[Authorize]** - Nhập API key một lần, dùng cho tất cả requests
**[Try it out]** - Kích hoạt chế độ test
**[Execute]** - Gửi request
**[Clear]** - Xóa dữ liệu form

---

## Test APIs Không Cần API Key

APIs không yêu cầu API key:
- ✅ App Registration (`/apps/v1/register`)
- ✅ WebAuthn endpoints (`/webauthn/v1/*`)
- ✅ Actuator endpoints (`/actuator/*`)

### Ví dụ: Test App Registration

**Bước 1:** Mở Swagger UI
```
http://localhost:8080/swagger-ui.html
```

**Bước 2:** Tìm "App Management" section
```
▼ App Management
  APIs for registering and managing applications
```

**Bước 3:** Click endpoint `POST /apps/v1/register`
```
POST /apps/v1/register
Register a new application
```

**Bước 4:** Click nút "Try it out"
```
[Try it out]  ← Click here
```

**Bước 5:** Điền request body
```json
{
  "name": "My Test App",
  "description": "Testing with Swagger UI",
  "rateLimitPerMinute": 60,
  "rateLimitPerHour": 1000
}
```

**Bước 6:** Click "Execute"
```
[Execute]  ← Click here
```

**Bước 7:** Xem response
```
Code: 201 Created

Response body:
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Test App",
  "apiKey": "pk_aGVsbG93b3JsZA...",  ← LƯU LẠI API KEY NÀY!
  "active": true,
  "createdAt": "2026-01-29T09:15:00Z"
}
```

**Bước 8:** Copy API key
```
pk_aGVsbG93b3JsZA...
```

---

## Test APIs Có API Key

APIs yêu cầu API key:
- 🔑 OTP endpoints (`/otp/v1/*`)
- 🔑 TOTP endpoints (`/totp/v1/*`)

### Bước 1: Authorize

**Click nút "Authorize" (góc trên bên phải)**
```
[Authorize]  ← Click here
```

**Dialog sẽ hiện:**
```
┌────────────────────────────────────┐
│ Available authorizations           │
├────────────────────────────────────┤
│ X-API-Key (apiKey)                │
│                                    │
│ API Key for authentication.        │
│ Required for OTP and TOTP         │
│ endpoints.                         │
│                                    │
│ Value: [____________________]     │
│        ↑ Paste API key here       │
│                                    │
│ [Authorize] [Close]               │
└────────────────────────────────────┘
```

**Paste API key vào:**
```
Value: pk_aGVsbG93b3JsZA...
```

**Click "Authorize":**
```
✓ Authorized
```

**Click "Close" để đóng dialog**

### Bước 2: Test OTP Send

**Tìm "OTP Authentication" section**
```
▼ OTP Authentication
  One-Time Password authentication via SMS or Email
```

**Click `POST /otp/v1/send`**
```
POST /otp/v1/send
Send OTP to phone or email
```

**Click "Try it out"**

**Điền request body:**
```json
{
  "sender": "sms",
  "destination": "+84912345678"
}
```

**Click "Execute"**

**Xem request được gửi:**
```
curl -X POST "http://localhost:8080/otp/v1/send" \
     -H "accept: application/json" \
     -H "X-API-Key: pk_aGVsbG93b3JsZA..." \  ← API key tự động thêm!
     -H "Content-Type: application/json" \
     -d '{"sender":"sms","destination":"+84912345678"}'
```

**Response:**
```
Code: 200 OK

{
  "sessionId": "993e61be-23cf-412d-8273-f02e316e8689",
  "expiresAt": "2026-01-29T09:18:00Z"
}
```

### Bước 3: Test OTP Verify

**Click `POST /otp/v1/verify`**

**Request body:**
```json
{
  "destination": "+84912345678",
  "otp": "123456"
}
```

**Execute và xem response!**

---

## Các Tính Năng Swagger

### 1. Search/Filter APIs

**Search box ở top:**
```
🔍 [Filter by tags, operation summary, description...]
```

**Ví dụ:**
- Type "register" → Tìm tất cả APIs liên quan đến registration
- Type "otp" → Tìm tất cả OTP endpoints
- Type "POST" → Lọc chỉ POST methods

### 2. Grouped by Tags

APIs được nhóm theo chức năng:
```
▼ App Management (7 operations)
▼ OTP Authentication (4 operations)
▼ TOTP Authentication (3 operations)
▼ WebAuthn (6 operations)
▼ Audit Logs (5 operations)
```

Click tag để expand/collapse tất cả endpoints trong nhóm.

### 3. Sorted by Method

Trong mỗi nhóm, APIs được sắp xếp theo HTTP method:
```
DELETE /apps/v1/{id}
GET    /apps/v1/list
GET    /apps/v1/{id}
POST   /apps/v1/register
POST   /apps/v1/{id}/activate
```

### 4. Request/Response Schemas

**Click "Schema" để xem data structure:**
```
▼ AppRegistrationRequest
  {
    name*         string
    description   string
    rateLimitPerMinute   integer($int32)
    rateLimitPerHour     integer($int32)
  }
```

**Click "Model" để xem example value:**
```json
{
  "name": "string",
  "description": "string",
  "rateLimitPerMinute": 0,
  "rateLimitPerHour": 0
}
```

### 5. Copy as cURL

Sau khi execute, copy curl command:
```
curl -X 'POST' \
  'http://localhost:8080/apps/v1/register' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "My App",
    "description": "Test"
  }'
```

Dùng trong terminal hoặc scripts!

### 6. Response Time

Sau mỗi request, thấy response time:
```
Response time: 245 ms
```

Useful để đánh giá performance.

### 7. Multiple Servers

Switch giữa các environments:
```
Servers: ▼ http://localhost:8080
         ▼ https://authentication.k4.vn
```

Test trên local hoặc production!

---

## OpenAPI JSON Spec

### Truy cập:

```
http://localhost:8080/v3/api-docs
```

### Dùng để làm gì?

**1. Import vào Postman:**
```
Postman → Import → Link → http://localhost:8080/v3/api-docs
```

**2. Generate Client Code:**
```bash
# Install openapi-generator
npm install -g @openapitools/openapi-generator-cli

# Generate TypeScript client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g typescript-axios \
  -o ./generated-client
```

**3. API Gateway Integration:**
- AWS API Gateway
- Kong
- Apigee

**4. Testing Tools:**
- Dredd
- Schemathesis
- RestAssured

### JSON Structure:

```json
{
  "openapi": "3.0.1",
  "info": {
    "title": "Passwordless Authentication API",
    "description": "# Passwordless Authentication Service...",
    "contact": {
      "name": "Passwordless Project",
      "url": "https://github.com/PHAMDOANK4/passwordless",
      "email": "support@example.com"
    },
    "license": {
      "name": "Apache License 2.0",
      "url": "https://www.apache.org/licenses/LICENSE-2.0"
    },
    "version": "1.0.0"
  },
  "servers": [
    {
      "url": "http://localhost:8080",
      "description": "Local Development Server"
    },
    {
      "url": "https://authentication.k4.vn",
      "description": "Production Server"
    }
  ],
  "paths": {
    "/apps/v1/register": {
      "post": {
        "tags": ["App Management"],
        "summary": "Register a new application",
        "operationId": "registerApp",
        "requestBody": {...},
        "responses": {...}
      }
    },
    ...
  },
  "components": {
    "schemas": {...},
    "securitySchemes": {
      "X-API-Key": {
        "type": "apiKey",
        "name": "X-API-Key",
        "in": "header"
      }
    }
  }
}
```

---

## Tích Hợp Vào Code

### Cách thêm documentation cho controller mới:

**Bước 1: Import annotations**
```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
```

**Bước 2: Annotate controller class**
```java
@RestController
@RequestMapping("/myapi/v1")
@Tag(name = "My API", description = "Description of my API features")
public class MyController {
    // ...
}
```

**Bước 3: Annotate endpoints**
```java
@PostMapping("/create")
@Operation(
    summary = "Create new resource",
    description = """
        Detailed description here.
        
        Can use **markdown** formatting!
        - Point 1
        - Point 2
        """
)
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request"),
    @ApiResponse(responseCode = "401", description = "Unauthorized")
})
public ResponseEntity<MyResponse> create(
        @Parameter(description = "Resource name", required = true)
        @RequestParam String name) {
    // implementation
}
```

**Bước 4: Rebuild và refresh Swagger UI**
```bash
mvn clean compile
# Restart app
# Refresh http://localhost:8080/swagger-ui.html
```

Endpoint mới sẽ tự động xuất hiện!

### Annotations reference:

| Annotation | Mục đích | Ví dụ |
|-----------|---------|-------|
| `@Tag` | Nhóm APIs | `@Tag(name = "Users")` |
| `@Operation` | Mô tả endpoint | `@Operation(summary = "Get user")` |
| `@ApiResponses` | Document response codes | `@ApiResponses(value = {...})` |
| `@Parameter` | Mô tả parameters | `@Parameter(description = "ID")` |
| `@RequestBody` | Mô tả request body | `@RequestBody(description = "...")` |
| `@Schema` | Link to model | `@Schema(implementation = User.class)` |

---

## Troubleshooting

### 1. Swagger UI không load

**Symptom:**
```
http://localhost:8080/swagger-ui.html
→ 404 Not Found
```

**Solutions:**

**Check 1: Dependency có đúng không?**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Check 2: Application properties**
```yaml
# application.yml
springdoc:
  swagger-ui:
    enabled: true
```

**Check 3: Security configuration**
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

**Check 4: Rebuild**
```bash
mvn clean install
mvn spring-boot:run
```

### 2. API Key không hoạt động

**Symptom:**
```
Request → 401 Unauthorized
Even after clicking Authorize
```

**Solutions:**

**Check 1: API key có đúng format?**
```
Correct: pk_aGVsbG93b3JsZA...
Wrong:   aGVsbG93b3JsZA... (missing pk_ prefix)
```

**Check 2: Đã click "Authorize"?**
```
1. Click [Authorize] button
2. Paste API key
3. Click [Authorize] trong dialog
4. Click [Close]
5. Try request again
```

**Check 3: App còn active?**
```bash
# Check app status
curl http://localhost:8080/apps/v1/list

# If inactive, activate:
curl -X POST http://localhost:8080/apps/v1/{id}/activate
```

**Check 4: Header name đúng?**
```
Must be: X-API-Key
Not: Api-Key or API-Key
```

### 3. Schema không hiển thị

**Symptom:**
```
Request body shows: "string" instead of proper schema
```

**Solutions:**

**Add @Schema annotation:**
```java
@Schema(implementation = MyRequestClass.class)
@RequestBody MyRequest request
```

**Make sure class is public:**
```java
public class MyRequest {  // Must be public!
    private String field1;
    // getters/setters
}
```

**Use @JsonProperty if needed:**
```java
public class MyRequest {
    @JsonProperty("fieldName")
    private String field;
}
```

### 4. Description không hiển thị

**Symptom:**
```
Endpoint shows but no description
```

**Solutions:**

**Add @Operation:**
```java
@Operation(
    summary = "Short summary",
    description = "Detailed description"
)
```

**Use text blocks for long descriptions:**
```java
@Operation(
    description = """
        Line 1
        Line 2
        Line 3
        """
)
```

### 5. "Try it out" không hoạt động

**Symptom:**
```
Click "Try it out" → Nothing happens
```

**Solutions:**

**Check browser console:**
```
F12 → Console tab
Look for JavaScript errors
```

**Clear browser cache:**
```
Ctrl+Shift+Delete → Clear cache
Refresh page
```

**Try different browser:**
```
Chrome, Firefox, Edge all supported
```

**Check CORS if on different domain:**
```java
@CrossOrigin(origins = "*")
```

---

## Best Practices

### 1. Documentation Standards

**Good description:**
```java
@Operation(
    summary = "Register a new application",
    description = """
        Register your application to get an API key for accessing OTP/TOTP endpoints.
        
        **Important:** Save the API key from the response - it cannot be retrieved later.
        
        **Rate Limits:** Configure per-minute and per-hour limits for your app.
        
        **Example:**
        ```json
        {
          "name": "My Mobile App",
          "rateLimitPerMinute": 100
        }
        ```
        """
)
```

**Bad description:**
```java
@Operation(summary = "Register app")  // Too short!
```

### 2. Response Codes

**Document all possible responses:**
```java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Success"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "404", description = "Not found"),
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
    @ApiResponse(responseCode = "500", description = "Server error")
})
```

### 3. Parameter Documentation

**Be specific:**
```java
@Parameter(
    description = "Application ID (UUID format)",
    required = true,
    example = "550e8400-e29b-41d4-a716-446655440000"
)
@PathVariable String id
```

### 4. Grouping

**Use consistent tag names:**
```java
// Good:
@Tag(name = "App Management", description = "...")
@Tag(name = "OTP Authentication", description = "...")
@Tag(name = "TOTP Authentication", description = "...")

// Bad:
@Tag(name = "Apps")  // Inconsistent naming
@Tag(name = "otpAPI")  // Wrong case
```

### 5. Examples

**Provide realistic examples:**
```java
@Schema(example = """
    {
      "name": "Production API Client",
      "description": "Client for production environment",
      "rateLimitPerMinute": 1000,
      "rateLimitPerHour": 50000
    }
    """)
```

---

## Summary

### ✅ Swagger UI Advantages:

1. **No Postman needed** - Test APIs trong browser
2. **Always up-to-date** - Generated từ code
3. **Interactive** - Try-it-out trực tiếp
4. **Developer-friendly** - Search, filter, copy curl
5. **Standard format** - OpenAPI 3.0 spec
6. **Multi-environment** - Switch servers dễ dàng
7. **API Key support** - Authorize một lần, dùng mọi nơi

### 🎯 Quick Reference:

| URL | Mục đích |
|-----|---------|
| `/swagger-ui.html` | Interactive UI |
| `/v3/api-docs` | OpenAPI JSON |
| `/swagger-ui/index.html` | Alternative UI path |

| Button | Action |
|--------|--------|
| **Authorize** | Set API key globally |
| **Try it out** | Enable testing mode |
| **Execute** | Send request |
| **Clear** | Reset form |

| Feature | Shortcut |
|---------|----------|
| Search | Ctrl+F trong page |
| Expand all | Click tag name |
| Copy curl | Click copy icon |
| Download spec | `/v3/api-docs` → Save |

---

**Happy API Testing! 🚀**

Có vấn đề? Check [Troubleshooting](#troubleshooting) section hoặc xem logs trong browser console (F12).
