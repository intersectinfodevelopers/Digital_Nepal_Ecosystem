package np.gov.digital.platformaudit.rls;

import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * RlsSessionVariableSetter
 *
 * Reads the current authenticated actor from Spring SecurityContext and
 * calls SET LOCAL on the database connection so PostgreSQL RLS policies
 * (V9 migration) know which ward / municipality / province this
 * request belongs to.
 *
 * Flow:
 *   request arrives  →  JwtAuthenticationFilter authenticates it  →  this
 *   class reads the AuthenticatedActor principal
 *   →  SET LOCAL app.current_ward_id = '<uuid>'
 *   →  PostgreSQL RLS filters citizen rows automatically
 *
 * SET LOCAL is transaction-scoped — resets when the transaction
 * ends, so HikariCP connection recycling cannot leak one user's
 * data scope to another user.
 */
@Slf4j
@Component
public class RlsSessionVariableSetter {
    // BUG FIX: this used to check `auth instanceof JwtAuthenticationToken`
    // — the type Spring's OAuth2 resource-server JWT decoder produces, not
    // what this app's own custom JwtAuthenticationFilter actually puts in
    // the SecurityContext. That check was always false, so this method was
    // a silent no-op for every real request. It turns out to be dead code
    // today (GeographicScopeFilter calls setExplicit(...) directly with
    // actor-derived values instead), but it's public API another caller
    // could reasonably wire up later and would fail closed exactly the
    // same way — fixed for consistency with the other AuthenticatedActor
    // call sites.
    public void setFromSecurityContext(Connection connection) throws SQLException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.debug("RLS: no authenticated user — skipping SET LOCAL");
            return;
        }

        if (!(auth.getPrincipal() instanceof AuthenticatedActor actor)) {
            log.debug("RLS: principal type {} is not an AuthenticatedActor — skipping",
                    auth.getPrincipal() != null ? auth.getPrincipal().getClass().getSimpleName() : "null");
            return;
        }

        String wardId         = uuidAsString(actor.getWardId());
        String municipalityId = uuidAsString(actor.getMunicipalityId());
        String provinceId     = uuidAsString(actor.getProvinceId());
        String role           = actor.getRole();

        log.debug("RLS: role={} ward={} municipality={} province={}",
                role, wardId, municipalityId, provinceId);

        try (Statement stmt = connection.createStatement()) {
            if (wardId != null) {
                stmt.execute("SET LOCAL app.current_ward_id = '"+ validated(wardId) + "'");
            }
            if (municipalityId != null) {
                stmt.execute("SET LOCAL app.current_municipality_id = '"+ validated(municipalityId) + "'");
            }
            if (provinceId != null) {
                stmt.execute("SET LOCAL app.current_province_id = '"+ validated(provinceId) + "'");
            }
            // CENTRAL_ADMIN: no scope — USING (true) policy applies, no SET LOCAL needed
        }
    }

    /**
     * Convenience overload for tests or non-HTTP flows where you
     * already have the IDs available directly.
     */
    public void setExplicit(Connection connection,
                            String wardId,
                            String municipalityId,
                            String provinceId) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            if (wardId != null)
                stmt.execute("SET LOCAL app.current_ward_id = '"+ validated(wardId) + "'");
            if (municipalityId != null)
                stmt.execute("SET LOCAL app.current_municipality_id = '"+ validated(municipalityId) + "'");
            if (provinceId != null)
                stmt.execute("SET LOCAL app.current_province_id = '"+ validated(provinceId) + "'");
        }
    }

    /**
     * Resets all session variables back to defaults.
     * Call this inside Spring TransactionSynchronization.afterCompletion()
     * when returning a connection to HikariCP after an abnormal
     * transaction end — prevents scope from bleeding to the next user
     * who receives the same physical connection from the pool.
     */
    public void reset(Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET app.current_ward_id");
            stmt.execute("RESET app.current_municipality_id");
            stmt.execute("RESET app.current_province_id");
        } catch (SQLException e) {
            // Variable was never set — safe to ignore
            log.trace("RLS reset: {}", e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private String uuidAsString(java.util.UUID id) {
        return id != null ? id.toString() : null;
    }

    /**
     * Validates that value is a UUID before embedding in SQL.
     * Prevents SQL injection through a tampered JWT claim.
     * UUID = 32 hex digits + 4 dashes = exactly 36 characters.
     */
    private String validated(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException(
                    "RLS: claim value is not a valid UUID: [" + value + "]");
        }
        return value;
    }
}