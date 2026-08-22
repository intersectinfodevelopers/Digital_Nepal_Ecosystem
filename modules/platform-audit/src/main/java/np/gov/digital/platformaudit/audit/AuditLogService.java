package np.gov.digital.platformaudit.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SECURITY / CORRECTNESS FIX: this service previously inserted into
 * `reporting.audit_logs` — a table that only ever existed in the abandoned
 * `001_init_schema.sql` draft (see SCHEMA_RECONCILIATION_NOTES.md), never in
 * the canonical V1 schema. Every single call to log(...) across the
 * codebase (CitizenService duplicate-NID attempts, ApprovalService
 * decisions, etc.) has been silently failing — caught by the try/catch
 * below, which by design never lets an audit failure break the main
 * request, but that also meant the failure was invisible unless someone
 * went looking at error-level logs. There has effectively been no working
 * audit trail.
 *
 * This version writes into `citizen_events` — the real, partitioned,
 * append-only audit table from the SDD (Section 4.13, Critical
 * Implementation Note #4), created in V15__citizen_events_audit_log.sql,
 * with UPDATE/DELETE revoked at the DB level.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public void log(AuditEventType eventType, UUID entityId, String details) {
        try {
            UUID actorId        = extractActorId();
            String actorRole    = extractActorRole();
            UUID jurisdictionId = extractJurisdictionId();

            // citizen_events.acted_by and .jurisdiction_id are NOT NULL by
            // design (every event must be attributable to someone with a
            // scope). If we ever get here with no authenticated actor —
            // e.g. a Quartz job — that job must authenticate as a reserved
            // system-service account, not call this method unauthenticated.
            if (actorId == null || jurisdictionId == null) {
                log.warn("AuditLogService: cannot log {} — no authenticated actor/jurisdiction "
                        + "in context. System-triggered events must run as a system-service "
                        + "account, not anonymously.", eventType);
                return;
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO citizen_events
                        (citizen_id, event_type, new_value_json, acted_by, acted_role, jurisdiction_id)
                    VALUES
                        (?::uuid, ?, ?::jsonb, ?::uuid, ?, ?::uuid)
                    """,
                    entityId != null ? entityId.toString() : null,
                    eventType.name(),
                    buildChangesJson(details),
                    actorId.toString(),
                    actorRole,
                    jurisdictionId.toString()
            );

            log.debug("Audit: {} on citizen={} by actor={} role={}",
                    eventType, entityId, actorId, actorRole);

        } catch (Exception e) {
            // NEVER let audit failure break the main request — but this
            // time, log loudly enough that a broken audit path (like the
            // reporting.audit_logs one before it) doesn't go unnoticed for
            // months again.
            log.error("AuditLogService: FAILED to write audit event {} — {}. "
                    + "The audit trail is currently incomplete; investigate immediately.",
                    eventType, e.getMessage(), e);
        }
    }

    public void log(AuditEventType eventType, String details) {
        log(eventType, null, details);
    }

    private UUID extractActorId() {
        Jwt jwt = extractJwt();
        if (jwt == null) return null;
        Object userId = jwt.getClaims().get("user_id");
        if (userId == null) return null;
        try {
            return UUID.fromString(userId.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractActorRole() {
        Jwt jwt = extractJwt();
        if (jwt == null) return "SYSTEM";
        Object role = jwt.getClaims().get("role");
        return role != null ? role.toString() : "SYSTEM";
    }

    // Resolves whichever of ward_id / municipality_id / province_id is
    // present on the actor's JWT — matches the "exactly one scope claim per
    // role" design enforced at the DB level in V19's chk_users_single_jurisdiction.
    // CENTRAL_ADMIN has none of the three (national scope) — events they
    // trigger use a fixed nationwide sentinel jurisdiction id.
    private static final UUID NATIONAL_JURISDICTION_SENTINEL =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private UUID extractJurisdictionId() {
        Jwt jwt = extractJwt();
        if (jwt == null) return null;
        for (String claim : new String[] {"ward_id", "municipality_id", "province_id"}) {
            Object value = jwt.getClaims().get(claim);
            if (value != null) {
                try {
                    return UUID.fromString(value.toString());
                } catch (IllegalArgumentException ignored) {
                    // fall through to next claim
                }
            }
        }
        // No scoped claim present — likely a CENTRAL_ADMIN (national scope).
        String role = extractActorRole();
        if ("CENTRAL_ADMIN".equals(role)) {
            return NATIONAL_JURISDICTION_SENTINEL;
        }
        return null;
    }

    private Jwt extractJwt() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                return jwtAuth.getToken();
            }
        } catch (Exception e) {
            log.trace("AuditLogService: could not extract JWT from security context: {}", e.getMessage());
        }
        return null;
    }

    private String buildChangesJson(String details) {
        if (details == null || details.isBlank()) {
            return "{}";
        }
        // `details` is a controlled internal string, not raw user input, so
        // basic escaping is sufficient here — do not put user-supplied free
        // text (e.g. a grievance description) through this without a real
        // JSON serializer.
        String escaped = details.replace("\"", "\\\"");
        return "{\"details\": \"" + escaped + "\"}";
    }
}
