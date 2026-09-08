package np.gov.digital.platformaudit.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import np.gov.digital.platformaudit.rls.RlsSessionVariableSetter;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;

@Slf4j
@Component
@Order(2) // runs after Spring Security filter (Order 1)
@RequiredArgsConstructor
public class GeographicScopeFilter extends OncePerRequestFilter {

    private final DataSource           dataSource;
    private final RlsSessionVariableSetter rlsSetter;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // BUG FIX: this used to check `auth instanceof JwtAuthenticationToken`
        // — the type Spring's OAuth2 resource-server JWT decoder produces,
        // not what this app's own custom JwtAuthenticationFilter actually
        // puts in the SecurityContext (a plain UsernamePasswordAuthenticationToken
        // wrapping CustomUserDetails). That check was always false for
        // every real request, so RLS session variables have never actually
        // been set — every RLS-protected query has been running with no
        // scope applied at all, "safe fail" or not.
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedActor actor) {
            String wardId         = uuidToString(actor.getWardId());
            String municipalityId = uuidToString(actor.getMunicipalityId());
            String provinceId     = uuidToString(actor.getProvinceId());

            log.debug("GeographicScopeFilter: role={} ward={} municipality={} province={}",
                    actor.getRole(), wardId, municipalityId, provinceId);

            // Get connection from pool and set RLS session variables
            try (Connection conn = dataSource.getConnection()) {
                rlsSetter.setExplicit(conn, wardId, municipalityId, provinceId);
            } catch (Exception e) {
                log.error("GeographicScopeFilter: failed to set RLS session variables: {}",
                        e.getMessage(), e);
                // Do NOT block the request — log and continue
                // RLS will return zero rows if vars not set (safe fail)
            }
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip RLS setup for public endpoints — no JWT, no scope needed
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/idcards/verify")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    // ----------------------------------------------------------------

    private String uuidToString(java.util.UUID id) {
        return id != null ? id.toString() : null;
    }
}