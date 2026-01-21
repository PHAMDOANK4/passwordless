# Centralized Authentication System API Documentation

## Overview

This service provides a centralized authentication platform that allows external applications to leverage passwordless authentication without implementing it themselves. Applications register with the service, receive an API key, and can use OTP, TOTP, and WebAuthn authentication methods.

## Architecture

```
┌─────────────────┐
│  External App   │ (e.g., E-commerce Store)
│  (Your Backend) │
└────────┬────────┘
         │ API Key
         │ Authentication
         ▼
┌─────────────────┐
│  Passwordless   │ ◄── Rate Limiting
│  Auth Service   │ ◄── Audit Logging
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
  OTP/SMS   TOTP    WebAuthn
```

## Getting Started

### Step 1: Register Your Application

Register your application to obtain an API key:

```bash
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Clothing Store",
    "description": "E-commerce platform",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Clothing Store",
  "description": "E-commerce platform",
  "apiKey": "pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5",
  "active": true,
  "createdAt": "2026-01-21T17:00:00Z",
  "rateLimitPerMinute": 100,
  "rateLimitPerHour": 5000
}
```

**⚠️ Important:** Save the `apiKey` securely - it's only shown once!

### Step 2: Use Authentication APIs

All authentication requests must include the `X-API-Key` header:

```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5" \
  -H "Content-Type: application/json" \
  -d '{"sender": "sms", "destination": "+1234567890"}'
```

## API Endpoints

### App Management

#### Register New App
- **POST** `/apps/v1/register`
- **Description:** Register a new application
- **Authentication:** None required
- **Body:**
  ```json
  {
    "name": "string",
    "description": "string",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }
  ```

#### List All Apps
- **GET** `/apps/v1/list`
- **Description:** List all registered applications
- **Authentication:** None required

#### Get App Details
- **GET** `/apps/v1/{appId}`
- **Description:** Get details of a specific app
- **Authentication:** None required

#### Deactivate App
- **POST** `/apps/v1/{appId}/deactivate`
- **Description:** Temporarily disable an app
- **Authentication:** None required

#### Activate App
- **POST** `/apps/v1/{appId}/activate`
- **Description:** Re-enable a deactivated app
- **Authentication:** None required

#### Delete App
- **DELETE** `/apps/v1/{appId}`
- **Description:** Permanently delete an app
- **Authentication:** None required

#### Regenerate API Key
- **POST** `/apps/v1/{appId}/regenerate-key`
- **Description:** Generate a new API key for an app
- **Authentication:** None required
- **Response:** New API key string

### OTP Authentication

#### Send OTP
- **POST** `/otp/v1/send`
- **Authentication:** Required (X-API-Key header)
- **Body:**
  ```json
  {
    "sender": "sms",
    "destination": "+1234567890"
  }
  ```
- **Response:**
  ```json
  {
    "operationId": "993e61be-23cf-412d-8273-f02e316e8689"
  }
  ```

#### Verify OTP
- **POST** `/otp/v1/verify`
- **Authentication:** Required (X-API-Key header)
- **Body:**
  ```json
  {
    "sessionId": "993e61be-23cf-412d-8273-f02e316e8689",
    "otp": "12345"
  }
  ```
- **Response:**
  ```json
  {
    "verified": true
  }
  ```

### TOTP Authentication

#### Register TOTP
- **POST** `/totp/v1/register`
- **Authentication:** Required (X-API-Key header)
- **Body:**
  ```json
  {
    "username": "johndoe@example.com"
  }
  ```
- **Response:**
  ```json
  {
    "uri": "otpauth://totp/...",
    "qr": "data:image/png;base64,..."
  }
  ```

#### Verify TOTP
- **POST** `/totp/v1/verify`
- **Authentication:** Required (X-API-Key header)
- **Body:**
  ```json
  {
    "username": "johndoe@example.com",
    "totp": 879580
  }
  ```
- **Response:**
  ```json
  {
    "valid": true
  }
  ```

### WebAuthn (FIDO2) Authentication

#### Start Registration
- **POST** `/webauthn/v1/registration/start`
- **Authentication:** Required (X-API-Key header)

#### Finish Registration
- **POST** `/webauthn/v1/registration/finish`
- **Authentication:** Required (X-API-Key header)

#### Start Login
- **POST** `/webauthn/v1/login/start`
- **Authentication:** Required (X-API-Key header)

#### Finish Login
- **POST** `/webauthn/v1/login/finish`
- **Authentication:** Required (X-API-Key header)

### Audit Logging

#### Get All Audit Logs
- **GET** `/apps/v1/audit/logs?page=0&size=20`
- **Description:** Retrieve paginated audit logs
- **Authentication:** None required

#### Get Logs by App
- **GET** `/apps/v1/audit/logs/app/{appId}?page=0&size=20`
- **Description:** Get audit logs for specific app
- **Authentication:** None required

#### Get Logs by Event Type
- **GET** `/apps/v1/audit/logs/event/{eventType}?page=0&size=20`
- **Description:** Get logs by event type (AUTHENTICATION, API_REQUEST, RATE_LIMIT_EXCEEDED)
- **Authentication:** None required

#### Get Logs in Time Range
- **GET** `/apps/v1/audit/logs/range?start={ISO_DATE}&end={ISO_DATE}`
- **Description:** Get logs within a time range
- **Authentication:** None required

#### Get Request Statistics
- **GET** `/apps/v1/audit/stats/{appId}?hours=24`
- **Description:** Get request count for an app
- **Authentication:** None required

## Integration Examples

### Node.js Integration

```javascript
const PASSWORDLESS_API_KEY = process.env.PASSWORDLESS_API_KEY;
const PASSWORDLESS_URL = 'http://localhost:8080';

async function sendOTP(phoneNumber) {
  const response = await fetch(`${PASSWORDLESS_URL}/otp/v1/send`, {
    method: 'POST',
    headers: {
      'X-API-Key': PASSWORDLESS_API_KEY,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sender: 'sms',
      destination: phoneNumber
    })
  });
  
  return await response.json();
}

async function verifyOTP(operationId, otp) {
  const response = await fetch(`${PASSWORDLESS_URL}/otp/v1/verify`, {
    method: 'POST',
    headers: {
      'X-API-Key': PASSWORDLESS_API_KEY,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sessionId: operationId,
      otp: otp
    })
  });
  
  return await response.json();
}

// Usage
const { operationId } = await sendOTP('+1234567890');
const { verified } = await verifyOTP(operationId, '12345');
```

### Python Integration

```python
import os
import requests

PASSWORDLESS_API_KEY = os.environ['PASSWORDLESS_API_KEY']
PASSWORDLESS_URL = 'http://localhost:8080'

def send_otp(phone_number):
    response = requests.post(
        f'{PASSWORDLESS_URL}/otp/v1/send',
        headers={
            'X-API-Key': PASSWORDLESS_API_KEY,
            'Content-Type': 'application/json'
        },
        json={
            'sender': 'sms',
            'destination': phone_number
        }
    )
    return response.json()

def verify_otp(operation_id, otp):
    response = requests.post(
        f'{PASSWORDLESS_URL}/otp/v1/verify',
        headers={
            'X-API-Key': PASSWORDLESS_API_KEY,
            'Content-Type': 'application/json'
        },
        json={
            'sessionId': operation_id,
            'otp': otp
        }
    )
    return response.json()

# Usage
result = send_otp('+1234567890')
operation_id = result['operationId']
verification = verify_otp(operation_id, '12345')
```

### Java Integration

```java
import java.net.http.*;
import java.net.URI;
import com.google.gson.Gson;

public class PasswordlessClient {
    private static final String API_KEY = System.getenv("PASSWORDLESS_API_KEY");
    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    
    public SendOtpResponse sendOtp(String phoneNumber) throws Exception {
        var requestBody = new SendOtpRequest("sms", phoneNumber);
        var request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/otp/v1/send"))
            .header("X-API-Key", API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
            .build();
            
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), SendOtpResponse.class);
    }
    
    public VerifyOtpResponse verifyOtp(String operationId, String otp) throws Exception {
        var requestBody = new VerifyOtpRequest(operationId, otp);
        var request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/otp/v1/verify"))
            .header("X-API-Key", API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
            .build();
            
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), VerifyOtpResponse.class);
    }
}
```

## Error Handling

### Error Responses

All errors return JSON with an error message:

```json
{
  "error": "Error description"
}
```

### HTTP Status Codes

- `200 OK` - Success
- `201 Created` - Resource created successfully
- `400 Bad Request` - Invalid request parameters
- `401 Unauthorized` - Missing or invalid API key
- `404 Not Found` - Resource not found
- `409 Conflict` - Resource already exists
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - Server error

### Rate Limiting

When rate limits are exceeded, you'll receive:

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json

{
  "error": "Rate limit exceeded. Please try again later."
}
```

## Security Best Practices

### API Key Management

1. **Never expose API keys in client-side code**
2. **Store API keys in environment variables or secrets management**
3. **Use different API keys for different environments**
4. **Rotate API keys regularly**
5. **Never commit API keys to version control**

### Request Security

1. **Always use HTTPS in production**
2. **Implement request signing for additional security**
3. **Log all authentication attempts**
4. **Monitor for suspicious activity**
5. **Implement IP whitelisting if needed**

### Rate Limiting Strategy

1. **Set appropriate rate limits based on your traffic**
2. **Implement exponential backoff for retries**
3. **Monitor rate limit usage**
4. **Request limit increases when needed**

## Monitoring

### Health Check

Check service health:

```bash
curl http://localhost:8080/actuator/health
```

### View Audit Logs

Monitor authentication attempts:

```bash
curl http://localhost:8080/apps/v1/audit/logs?page=0&size=20
```

### Statistics

Get usage statistics:

```bash
curl http://localhost:8080/apps/v1/audit/stats/{appId}?hours=24
```

## Support

For issues, questions, or feature requests:
- GitHub Issues: [Create an issue](https://github.com/your-org/passwordless/issues)
- Documentation: [Full documentation](https://github.com/your-org/passwordless/wiki)
- Email: support@your-domain.com
