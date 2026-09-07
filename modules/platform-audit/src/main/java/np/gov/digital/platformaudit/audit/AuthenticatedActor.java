package np.gov.digital.platformaudit.audit;

import java.util.UUID;

/**
 * Implemented by the authenticated principal (auth module's
 * CustomUserDetails) so services outside the auth module can pull the
 * real actor's identity and geographic scope out of the SecurityContext
 * without a compile-time dependency on the auth module — which would be
 * circular, since auth already depends on citizen-registry (which depends
 * on this module) for ApprovalService.
 *
 * Without this, code like CitizenService.getActorId() could only call
 * Authentication.getName(), which for a UserDetails principal returns the
 * username (email here) — not a UUID — and silently falls back to a
 * placeholder actor on every real request. Same story for AuditLogService
 * and GeographicScopeFilter, which checked `instanceof JwtAuthenticationToken`
 * — the type Spring's OAuth2 resource-server JWT decoder produces, not what
 * this app's own custom JwtAuthenticationFilter actually puts in the
 * SecurityContext (a plain UsernamePasswordAuthenticationToken). Both checks
 * were always false for every real request: the audit log has been
 * silently writing nothing, and RLS session variables have never been set.
 */
public interface AuthenticatedActor {
    UUID getUserId();

    /** e.g. "WARD_ADMIN" — without the Spring Security "ROLE_" prefix. */
    String getRole();

    UUID getWardId();

    UUID getMunicipalityId();

    UUID getProvinceId();
}
