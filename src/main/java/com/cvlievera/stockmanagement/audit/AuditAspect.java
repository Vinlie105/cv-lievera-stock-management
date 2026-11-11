package com.cvlievera.stockmanagement.audit;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String username = getCurrentUsername();
            Long entityId = resolveEntityId(auditable, joinPoint.getArgs(), result);
            auditLogService.log(username, auditable.action(), auditable.entityType(), entityId, null);
        } catch (Exception ignored) {
            // Audit failure must never break the main operation
        }

        return result;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "system";
    }

    private Long resolveEntityId(Auditable auditable, Object[] args, Object result) {
        if (auditable.entityIdParam() >= 0 && args != null && auditable.entityIdParam() < args.length) {
            Object arg = args[auditable.entityIdParam()];
            if (arg instanceof Long id) {
                return id;
            }
        }
        // Try to get ID from the return value via getId()
        if (result != null) {
            try {
                Method getId = result.getClass().getMethod("getId");
                Object id = getId.invoke(result);
                if (id instanceof Long longId) {
                    return longId;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
