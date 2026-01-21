# Passwordless Authentication Service

[![Build](https://github.com/maximthomas/passwordless/actions/workflows/build.yml/badge.svg)](https://github.com/maximthomas/passwordless/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/maximthomas/passwordless)](https://github.com/maximthomas/passwordless/blob/master/LICENSE)
![CodeQL](https://github.com/maximthomas/passwordless/workflows/CodeQL/badge.svg)

A centralized authentication service that provides passwordless authentication methods for your applications.

## 📚 Documentation

- **[API Documentation](docs/API_DOCUMENTATION.md)** - Complete API reference with examples
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Production deployment instructions
- **[Quick Start](#quick-start)** - Get started in 5 minutes

# Table of contents

- [How it works](#how-it-works)
- [Centralized Authentication System](#centralized-authentication-system)
  * [Overview](#overview)
  * [App Registration](#app-registration)
  * [API Authentication](#api-authentication)
  * [Rate Limiting](#rate-limiting)
  * [Audit Logging](#audit-logging)
- [Quick start](#quick-start)
- [One Time Password Authentication](#one-time-password-authentication)
  * [Introduction](#introduction)
  * [Sample Use Cases](#sample-use-cases)
    + [Registration Process](#registration-process)
    + [Authentication Process](#authentication-process)
    + [Essential Operation Confirmation (Authorization)](#essential-operation-confirmation--authorization-)
  * [Customize Settings](#customize-settings)
- [Using Time-Based One Time Password Authentication (TOTP)](#using-time-based-one-time-password-authentication--totp-)
  * [Prerequisites](#prerequisites)
  * [Registration](#registration)
  * [Authentication](#authentication)
- [Using Web Authentication (WebAuthn)](#using-web-authentication--webauthn-)
  * [Prerequisites](#prerequisites-1)
  * [Using Javascript SDK](#using-javascript-sdk)
    + [Registration](#registration-1)
    + [Login](#login)
- [Persistence](#persistence)


# How it works
This is a **centralized authentication service** that allows external applications to leverage passwordless authentication methods without implementing them from scratch. 

Applications register with the service, receive an API key, and can then use any of the supported authentication methods:
- [One Time Password (OTP)](https://en.wikipedia.org/wiki/One-time_password) - Receive codes via SMS or Email
- [Time-based One Time Password (TOTP)](https://en.wikipedia.org/wiki/Time-based_one-time_password) - Use with authenticator apps like Google Authenticator
- [Web Authentication (WebAuthn/FIDO2)](https://en.wikipedia.org/wiki/WebAuthn) - Biometric and security key authentication

You can also use it as a second authentication factor (2FA) alongside login and password or to authorize essential
operations (for example, change password, or confirm payment) for the already authenticated user.

# Centralized Authentication System

## Overview

The Passwordless service now operates as a centralized authentication platform that allows external systems (e.g., e-commerce stores, banking apps, SaaS platforms) to integrate passwordless authentication without building it themselves.

### Key Features:
- **API Key Authentication**: Secure access control using API keys with BCrypt hashing
- **Rate Limiting**: Per-app rate limits (configurable per minute and per hour)
- **Audit Logging**: Track all authentication attempts, API requests, and security events
- **Multi-tenancy**: Support multiple apps with isolated configurations
- **RESTful API**: Easy integration with any platform

## App Registration

External applications must register with the service to obtain an API key.

### Register a New App

```bash
curl -X POST http://localhost:8080/apps/v1/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Clothing Store",
    "description": "E-commerce platform for fashion",
    "rateLimitPerMinute": 100,
    "rateLimitPerHour": 5000
  }'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Clothing Store",
  "description": "E-commerce platform for fashion",
  "apiKey": "pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5",
  "active": true,
  "createdAt": "2026-01-21T17:00:00Z",
  "rateLimitPerMinute": 100,
  "rateLimitPerHour": 5000
}
```

**Important:** Store the `apiKey` securely - it's only shown once during registration!

### List All Registered Apps

```bash
curl -X GET http://localhost:8080/apps/v1/list
```

### Get App Details

```bash
curl -X GET http://localhost:8080/apps/v1/{appId}
```

### Deactivate an App

```bash
curl -X POST http://localhost:8080/apps/v1/{appId}/deactivate
```

### Regenerate API Key

```bash
curl -X POST http://localhost:8080/apps/v1/{appId}/regenerate-key
```

## API Authentication

All authentication API requests must include the API key in the `X-API-Key` header:

```bash
curl -X POST http://localhost:8080/otp/v1/send \
  -H "X-API-Key: pk_aGVsbG93b3JsZHRoaXNpc2F0ZXN0a2V5" \
  -H "Content-Type: application/json" \
  -d '{
    "sender": "sms",
    "destination": "+1234567890"
  }'
```

### Authentication Flow for External Apps

1. **Register your app** to get an API key
2. **Store the API key** securely in your backend
3. **Make API calls** with the `X-API-Key` header
4. **Handle responses** and integrate with your authentication flow

### Example: E-commerce Store Integration

```javascript
// Backend code (Node.js example)
const API_KEY = process.env.PASSWORDLESS_API_KEY;
const PASSWORDLESS_URL = 'http://localhost:8080';

async function sendOTP(phoneNumber) {
  const response = await fetch(`${PASSWORDLESS_URL}/otp/v1/send`, {
    method: 'POST',
    headers: {
      'X-API-Key': API_KEY,
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
      'X-API-Key': API_KEY,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sessionId: operationId,
      otp: otp
    })
  });
  
  return await response.json();
}
```

## Rate Limiting

Each app has configurable rate limits to prevent abuse:

- **Per-Minute Limit**: Maximum requests per minute (default: 60)
- **Per-Hour Limit**: Maximum requests per hour (default: 1000)

When rate limits are exceeded, the API returns HTTP 429 (Too Many Requests):

```json
{
  "error": "Rate limit exceeded. Please try again later."
}
```

Rate limits are enforced independently per app and reset automatically.

## Audit Logging

All authentication attempts and API requests are logged for security monitoring and debugging.

### View Audit Logs

```bash
# Get all audit logs (paginated)
curl -X GET "http://localhost:8080/apps/v1/audit/logs?page=0&size=20"

# Get logs for a specific app
curl -X GET "http://localhost:8080/apps/v1/audit/logs/app/{appId}?page=0&size=20"

# Get logs by event type
curl -X GET "http://localhost:8080/apps/v1/audit/logs/event/AUTHENTICATION?page=0&size=20"

# Get logs in a time range
curl -X GET "http://localhost:8080/apps/v1/audit/logs/range?start=2026-01-21T00:00:00Z&end=2026-01-21T23:59:59Z"

# Get request count statistics
curl -X GET "http://localhost:8080/apps/v1/audit/stats/{appId}?hours=24"
```

### Event Types:
- `AUTHENTICATION`: API key authentication attempts
- `API_REQUEST`: Successful API calls
- `RATE_LIMIT_EXCEEDED`: Rate limit violations


# Quick start

There are several ways to run the Passwordless service:

Run from source code
```
$> ./mvnw spring-boot:run
```

Run as a Docker image
```
$> docker run --publish=8080:8080  maximthomas/passwordless
```

Build and run docker image using docker-compose
```
$> ./mvnw install
$> docker-compose up --build 
```

# One Time Password Authentication

## Introduction

A user enters credentials on your site, you get a phone or email from the user's credentials and call Passwordless service API.
Passwordless service generates and sends a one-time password (OTP) to the user's phone via SMS or to a user's E-mail.
You can use any custom provider.
The user enters the received OTP and then you verify it at Passwordless service.
If verification was successful, the user can be authenticated.

## Sample Use Cases

### Registration Process
While registering the user enters his phone number or email among other data.
Site calls Passwordless service to comfirm users email or phone number, to be sure that phone or email belongs to the user.
After user enters valid OTP, user account with confirmed phone or email can be created.

This process shown on the diagram below:
![Registration diab](docs/images/Registration.png)

### Authentication Process
While authentication the user enters his login, site gets users phone number or email from his profile and calls
Passwordless service. Passwordless service sends OTP to the users phone or email. Users enters OTP, if OTP is valid,
the user can be authenticated.

### Essential Operation Confirmation (Authorization)
If there'a need to change password, restore password or confirm purchase or payment, site calls Passwordless service
to be sure that exactly the user performs this critical operation.

## Customize Settings

Adjust settings in [application.yml](./src/main/resources/application.yml) in the `otp/settings` section
```yaml
#dummy OTP sender (does noting just logs)
- id: "sms"
  name: "Dummy SMS OTP Setting"
  messageTitle: "Acme LLC"
  messageTemplate: "Confirmation code: ${otp}"
  otpLength: 5
  useLetters: false
  useDigits: true
  ttlMinutes: 3
  sender: "dummyOTPSender"

#Twilio SMS Sender
- id: "twilioSms"
  name: "Twilio SMS OTP Setting"
  messageTitle: "Acme LLC"
  messageTemplate: "Confirmation code: ${otp}"
  otpLength: 5
  useLetters: false
  useDigits: true
  ttlMinutes: 3
  sender: "twilioOTPSender"

#Email OTP Link Sender
- id: "email"
  name: "TEST Email"
  messageTitle: "Thank yor for registration"
  messageTemplate: "Temporary link: http://acme.com?link=${otp}"
  otpLength: 36
  useLetters: true
  useDigits: true
  ttlMinutes: 180 #three hours
  sender: "emailOTPSender"
```

Send OTP to client with SMS setting:
```
curl -X POST -d '{"destination": "+1999999999"}'  -H "Content-Type: application/json" 'http://localhost:8080/otp/v1/sms/send' 
```
where `/sms/` - otp settings ID from `.yaml` settings file
Sample response:
```
{"operationId":"993e61be-23cf-412d-8273-f02e316e8689"}
```

Validate OTP with `operationId`:
```
curl -X POST -d '{"operationId": "993e61be-23cf-412d-8273-f02e316e8689", "otp": "123456"}'  -H "Content-Type: application/json" 'http://localhost:8080/otp/v1/verify'
```
Sample response:
```
{"verified":false}
```

# Using Time-Based One Time Password Authentication (TOTP)

TOTP authentication can be used as a 2 factor during the authentication process.

## Prerequisites
To use TOTP authentication the user should have an app such as Google Authenticator or Microsoft Authenticator installed on his device.

## Registration
To register a user or update user registration, send a POST request with the username
```
curl -X POST -d '{"username": "johndoe"}'  -H "Content-Type: application/json" 'http://localhost:8080/totp/v1/register'
```

The service will respond back TOTP URI and QR code image in Base64 format to scan in authenticator mobile application

```json
{"uri":"otpauth://totp/Acme+LLC:johndoe@acme.com?secret=5HFDYTGWPC3T72CCWDDXM7SY33ITKLC3&issuer=Acme+LLC","qr":"data:image/png;base64, iVBORw0KGgoAAAANSUhEUgAAASwAAAEsAQAAAABRBrPYAAACQElEQVR4Xu2WQW7sIBBE2yuOwU3B3NRHyJIVpKqwJzETKdl8fblFy/Iw8FhQXd3G+l/iw+aZH2NhUyxsioVNsbApFjbFv8CaMRKfuJttmKpm4eBs8IVxfADLeIe+awvG+VzyhB04fgoHpCjVcoUmmgHvErNYatwpSOxKvVcsc9iBlU6TJ58YXkeSFA0VzS34+27y52PGoAL3h7O+sDNqbKhoJJ2rLO1XuMEaM26ZXQskfH4avrConWH8i7ekGKuHZiJL2xvGEibT2bXs9WFyhunsXV/bRFlG7/rygBtMISBwyz7U6HhTIlcYck1BqInR2Ad+aPKrffnCcFckWWpsL9JuJneAKd08fgo0AINSHPmtnB+PmXGSxn6NyWyVtveEdbYs3RgD67eolk36ZK27weDq3nmJUsblhJH9GkW5wppKeFMVYxWe72AklCeMauhJ4bxHfR94wprGpdPtMvlpg2R33VxgOD4yDmWMvRp/kX2OR7jBBtmZ+tGZxVvc74I4wBrNzOMXfZJ2dWZKJH1cYYYeFYsE2bQqAJrcBXk+1k8RMB9LZWcmgMEVjjB4+9KEgOVxm1LT9oQ1VW7iE5usnsYd480hT8dGNKMOG6v7JLF9LLnBms6eKyZZzimwlvFO3OgLo5M1LxF24zeX40B9fGFSQEDnZyg2qSFZHGL5Wk3SoXFmSr0bjAqYbGBsWWfefWFcGlkuKGcagHWdL9IPZgwmHReMzv6c+da89rrBfo2FTbGwKRY2xcKmWNgU/wf7BJvt/ZLujX4VAAAAAElFTkSuQmCC"}
```

Scan the QR code in Authenticator app it will start generatig TOTP

## Authentication

To verify TOTP send a user's code from an authenticator mobile app in a POST request
```
curl -X POST -d '{"username": "johndoe", "totp": 879580 }'  -H "Content-Type: application/json" 'http://localhost:8080/totp/v1/verify'
```

The server will respond with verification result
```json
{"valid":true}
```

# Using Web Authentication (WebAuthn)

Passwordless service can be used to provide WebAuthn Registration and Login functions both on server using API and on client using JavaScript SDK.

## Prerequisites
Setup required origin in `webauthn-sample-settings.yaml` in `origin` setting.

And run Passwordless Service from docker compose

## Using Javascript SDK

Just add to your web application SDK script and initialize SDK:
```html
<script src="http://passwordless-service:8080/js/passwordless-sdk.js"></script>
<script>
    Passwordless.init({host: 'http://passwordless-service:8080'});
</script>
``` 
Full example is [here](./examples/jssdk)

### Registration

Just call
```javascript
Passwordless.webauthn.startRegistration(login);
```
where `login` - your username, and dialog asking you to insert USB Token will appear.
After successful registration SDK will return credenital Id value.


### Login
If your account already registered via startRegistration function and you want to authenticate, call
```javascript
Passwordless.webauthn.startLogin(login);
```

# Persistence

Passwordless service PostgresSQL and H2 databases.