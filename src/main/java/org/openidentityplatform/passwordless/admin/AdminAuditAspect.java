package org.openidentityplatform.passwordless.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.openidentityplatform.passwordless.apps.services.AuditLogService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that automatically logs all admin controller actions.
 * <p>
 * Captures:
 * <ul>
 *   <li>{@code user_id} — from request attributes (set by JwtAuthenticationFilter)</li>
 *   <li>{@code action} — derived from the controller method name</li>
 *   <li>{@code timestamp} — recorded by AuditLogService</li>
 *   <li>{@code IP address} — from the HTTP request</li>
 * </ul>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Intercepts all public methods in admin controllers (RestControllers under admin package).
     * Logs the admin action after successful execution.
     */
    @Around("execution(* org.openidentityplatform.passwordless.admin.Admin*Controller.*(..))")
    public Object auditAdminAction(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return result;
            }

            HttpServletRequest request = attrs.getRequest();
            String userId = (String) request.getAttribute("adminUserId");
            String userEmail = (String) request.getAttribute("adminUserEmail");
            String endpoint = request.getRequestURI();
            String httpMethod = request.getMethod();
            String ipAddress = getClientIpAddress(request);
            String action = joinPoint.getSignature().getName();

            // Only log write operations (POST, PUT, DELETE) to avoid noise from reads
            if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)
                    || "DELETE".equalsIgnoreCase(httpMethod) || "PATCH".equalsIgnoreCase(httpMethod)) {

                String details = buildDetails(action, userId, request);
                auditLogService.logAdminAction(userId, userEmail, action, endpoint, httpMethod, ipAddress, details);
            }
        } catch (Exception e) {
            // Never let audit logging break the request
            log.error("Admin audit logging failed for {}", joinPoint.getSignature().getName(), e);
        }

        return result;
    }

    private String buildDetails(String action, String userId, HttpServletRequest request) {
        try {
            var details = new java.util.LinkedHashMap<String, Object>();
            details.put("action", action);
            details.put("adminUserId", userId);
            details.put("adminRole", request.getAttribute("adminUserRole"));
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            return "{\"action\": \"" + action + "\"}";
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
