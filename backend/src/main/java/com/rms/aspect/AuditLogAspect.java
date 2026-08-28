package com.rms.aspect;

import com.rms.domain.AuditLog;
import com.rms.repository.AuditLogRepository;
import com.rms.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * Module 2.8 - cross-cutting audit trail. Fires AFTER a @AuditableAction-annotated method
 * returns successfully (never on exception - a failed operation did not actually happen, so
 * nothing should be recorded as having occurred). Reading the annotation off the join point
 * means individual services never construct an AuditLog row themselves; they simply declare
 * intent with @AuditableAction("STOCK_CORRECTION") and this aspect does the rest.
 *
 * REQUIRES_NEW propagation is deliberate: the audit write commits independently of the
 * business transaction it is describing. If the audit insert itself failed for some reason,
 * we do not want that failure to roll back the (already-succeeded) business operation - we
 * log the audit failure and move on, since audit logging is best-effort observability, not a
 * transactional guarantee equivalent to a stock deduction.
 *
 * NFR-05 requires this write off the synchronous request path, same as stock-alert publishing.
 * recordAudit() itself stays synchronous - it reads userId/IP off the ThreadLocal-backed
 * SecurityContext and RequestContextHolder, which are NOT available once execution hops onto
 * an @Async thread pool thread. Only writeAuditRow(), which receives those already-resolved
 * plain values as arguments, is annotated @Async.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(pointcut = "@annotation(auditableAction)", argNames = "joinPoint,auditableAction")
    public void recordAudit(JoinPoint joinPoint, AuditableAction auditableAction) {
        try {
            Long userId = currentUserId();
            String ip = currentIpAddress();
            String details = joinPoint.getSignature().getName() + "(" + Arrays.toString(joinPoint.getArgs()) + ")";

            writeAuditRow(userId, auditableAction.value(), ip, details);
        } catch (Exception ex) {
            // Best-effort: never let an audit-logging failure surface to the caller.
            log.warn("Failed to write audit log entry for action {}: {}", auditableAction.value(), ex.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void writeAuditRow(Long userId, String actionType, String ip, String details) {
        AuditLog entry = AuditLog.builder()
                .userId(userId != null ? userId : -1L)
                .actionType(actionType)
                .ipAddress(ip)
                .details(details)
                .build();
        auditLogRepository.save(entry);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private String currentIpAddress() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            return forwardedFor != null ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
        }
        return "unknown";
    }
}
