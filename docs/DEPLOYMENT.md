# Deployment Guide

This guide provides instructions for deploying the Passwordless Authentication Service in various environments.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Docker Deployment](#docker-deployment)
- [Production Deployment](#production-deployment)
- [Environment Variables](#environment-variables)
- [Security Considerations](#security-considerations)
- [Monitoring and Maintenance](#monitoring-and-maintenance)

## Prerequisites

- Docker and Docker Compose (for containerized deployment)
- Java 17+ (for standalone deployment)
- MySQL 8.0+ (or H2 for development)
- (Optional) Twilio account for SMS OTP
- (Optional) Email server for Email OTP

## Docker Deployment

### Quick Start with Docker Compose

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-org/passwordless.git
   cd passwordless
   ```

2. **Configure environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   nano .env
   ```

3. **Build and run:**
   ```bash
   docker-compose up --build -d
   ```

4. **Verify the service is running:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Using Pre-built Docker Image

```bash
docker run -d \
  --name passwordless \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://your-mysql-host:3306/passwordless \
  -e SPRING_DATASOURCE_USERNAME=passwordless \
  -e SPRING_DATASOURCE_PASSWORD=your-password \
  maximthomas/passwordless:latest
```

## Production Deployment

### 1. Database Setup

**Create MySQL database and user:**
```sql
CREATE DATABASE passwordless CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'passwordless'@'%' IDENTIFIED BY 'strong_password_here';
GRANT ALL PRIVILEGES ON passwordless.* TO 'passwordless'@'%';
FLUSH PRIVILEGES;
```

### 2. Application Configuration

**Create `application-prod.yml`:**
```yaml
server:
  port: 8080
  servlet:
    session:
      cookie:
        same-site: strict
        secure: true

spring:
  datasource:
    url: jdbc:mysql://your-db-host:3306/passwordless?useSSL=true&requireSSL=true
    username: passwordless
    password: ${DB_PASSWORD}
    
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
      
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true

logging:
  level:
    org.openidentityplatform.passwordless: INFO
    org.springframework.security: WARN
```

### 3. Build and Deploy

**Build the application:**
```bash
./mvnw clean package -DskipTests
```

**Run as a service (systemd example):**

Create `/etc/systemd/system/passwordless.service`:
```ini
[Unit]
Description=Passwordless Authentication Service
After=network.target mysql.service

[Service]
Type=simple
User=passwordless
ExecStart=/usr/bin/java -jar /opt/passwordless/passwordless.jar \
  --spring.profiles.active=prod \
  --spring.config.location=/opt/passwordless/application-prod.yml
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable passwordless
sudo systemctl start passwordless
sudo systemctl status passwordless
```

### 4. Reverse Proxy (Nginx)

**Example Nginx configuration:**
```nginx
server {
    listen 80;
    server_name auth.your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name auth.your-domain.com;

    ssl_certificate /etc/letsencrypt/live/auth.your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/auth.your-domain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | Database connection URL | `jdbc:mysql://localhost:3306/passwordless` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `passwordless` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `secure_password` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MAIL_HOST` | SMTP server host | `localhost` |
| `MAIL_PORT` | SMTP server port | `587` |
| `MAIL_USERNAME` | SMTP username | - |
| `MAIL_PASSWORD` | SMTP password | - |
| `TWILIO_ACCOUNT_SID` | Twilio Account SID (for SMS) | - |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token | - |
| `TWILIO_MESSAGING_SERVICE_SID` | Twilio Messaging Service SID | - |
| `TOTP_ISSUER` | TOTP issuer name | `acme.com` |
| `TOTP_ISSUER_LABEL` | TOTP display name | `Acme LLC` |
| `WEBAUTHN_RPID` | WebAuthn Relying Party ID | `localhost` |
| `WEBAUTHN_ORIGIN` | WebAuthn origin URL | `http://localhost:8080` |

## Security Considerations

### 1. API Key Management
- Store API keys securely in environment variables or secrets management systems
- Never commit API keys to version control
- Rotate API keys regularly
- Use different API keys for different environments

### 2. Database Security
- Use strong passwords for database users
- Enable SSL/TLS for database connections in production
- Regularly backup the database
- Implement database access controls

### 3. Network Security
- Use HTTPS in production (enable SSL/TLS)
- Configure firewalls to restrict access
- Use a reverse proxy (e.g., Nginx) in front of the application
- Enable CORS only for trusted domains

### 4. Rate Limiting
- Configure appropriate rate limits per application
- Monitor rate limit violations
- Implement IP-based rate limiting if needed

### 5. Audit Logging
- Regularly review audit logs for suspicious activity
- Set up alerts for security events
- Archive logs for compliance purposes

## Monitoring and Maintenance

### Health Check Endpoint
```bash
curl http://localhost:8080/actuator/health
```

### View Audit Logs
```bash
# View recent authentication attempts
curl -X GET "http://localhost:8080/apps/v1/audit/logs?page=0&size=20"
```

### Monitoring Metrics
- Monitor application logs for errors
- Track API response times
- Monitor database connection pool
- Set up alerts for:
  - High error rates
  - Rate limit violations
  - Unusual authentication patterns
  - Database connection issues

### Backup Strategy
1. **Database Backups:**
   ```bash
   mysqldump -u root -p passwordless > passwordless_backup_$(date +%Y%m%d).sql
   ```

2. **Application Configuration:**
   - Backup configuration files
   - Version control for configuration changes

3. **Automated Backups:**
   - Set up automated daily backups
   - Store backups in a secure location
   - Test backup restoration regularly

### Updates and Maintenance
1. **Update Dependencies:**
   ```bash
   ./mvnw versions:display-dependency-updates
   ```

2. **Security Patches:**
   - Subscribe to security advisories
   - Apply security patches promptly
   - Test in staging before production

3. **Database Maintenance:**
   - Optimize database tables regularly
   - Monitor database size and growth
   - Clean up old audit logs if needed

## Troubleshooting

### Common Issues

**Service won't start:**
- Check database connectivity
- Verify environment variables
- Review application logs

**Authentication failures:**
- Verify API key is correct
- Check rate limits
- Review audit logs

**Email/SMS not sending:**
- Verify SMTP/Twilio credentials
- Check network connectivity
- Review sender configuration

### Logs Location
- Docker: `docker logs passwordless-service`
- Systemd: `journalctl -u passwordless -f`
- File: `/var/log/passwordless/application.log`

## Support

For issues and questions:
- GitHub Issues: https://github.com/your-org/passwordless/issues
- Documentation: https://github.com/your-org/passwordless/wiki
- Email: support@your-domain.com
