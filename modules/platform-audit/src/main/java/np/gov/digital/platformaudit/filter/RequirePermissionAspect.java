package np.gov.digital.platformaudit.filter;

import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
@Slf4j
@Aspect
@Component
public class RequirePermissionAspect {

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(

            "WARD_ADMIN", List.of(
                    Permission.CITIZEN_READ,
                    Permission.CITIZEN_WRITE,
                    Permission.ID_CARD_INITIATE,
                    Permission.GRIEVANCE_RECEIVE
            ),

            "LOCAL_BODY_ADMIN", List.of(
                    Permission.CITIZEN_READ,
                    Permission.CITIZEN_WRITE,
                    Permission.CITIZEN_APPROVE,
                    Permission.CITIZEN_ARCHIVE,
                    Permission.ID_CARD_INITIATE,
                    Permission.ID_CARD_APPROVE,
                    Permission.EDIT_APPROVE,
                    Permission.EDIT_REJECT,
                    Permission.GRIEVANCE_RESOLVE
            ),

            "PROVINCE_ADMIN", List.of(
                    Permission.CITIZEN_READ
            ),

            "CENTRAL_ADMIN", List.of(
                    Permission.CITIZEN_READ,
                    Permission.SYSTEM_CONFIG
            )
    );

    @Around("@annotation(np.gov.digital.platformaudit.filter.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {

        // Read @RequirePermission annotation from method
        MethodSignature sig    = (MethodSignature) joinPoint.getSignature();
        Method          method = sig.getMethod();
        String required = method.getAnnotation(RequirePermission.class).value();

        // Read role from current JWT
        String role = extractRole();

        log.debug("RequirePermissionAspect: role={} requires={}", role, required);

        // Check if role has the required permission
        List<String> permissions = ROLE_PERMISSIONS.getOrDefault(role, List.of());

        if (!permissions.contains(required)) {
            log.warn("RequirePermissionAspect: DENIED — role={} does not have permission={}",
                    role, required);
            // 403 FORBIDDEN — DB never touched
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Permission denied: " + required + " required"
            );
        }

        // Permission granted — run the method
        return joinPoint.proceed();
    }

    // BUG FIX: this checked `auth instanceof JwtAuthenticationToken` — the
    // type Spring's OAuth2 resource-server JWT decoder produces, not what
    // this app's own custom JwtAuthenticationFilter actually puts in the
    // SecurityContext. Currently unused anywhere (no controller method
    // carries @RequirePermission yet), but as written this would have
    // returned "UNKNOWN" — and therefore denied — for every real request,
    // the moment anyone wired it up. Fixed to match the same
    // AuthenticatedActor pattern used elsewhere.
    private String extractRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor) {
                return actor.getRole();
            }
        } catch (Exception e) {
            log.trace("RequirePermissionAspect: could not extract role: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
}