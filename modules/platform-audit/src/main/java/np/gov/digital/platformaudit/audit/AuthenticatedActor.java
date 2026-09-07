package np.gov.digital.platformaudit.audit;

import java.util.UUID;

/**
 * Implemented by the authenticated principal (auth module's
 * CustomUserDetails) so services outside the auth module can pull the
 * real actor's user ID out of the SecurityContext without needing a
 * compile-time dependency on the auth module — which would be circular,
 * since auth already depends on citizen-registry (which depends on this
 * module) for ApprovalService.
 *
 * Without this, code like CitizenService.getActorId() could only call
 * Authentication.getName(), which for a UserDetails principal returns the
 * username (email here) — not a UUID — and silently falls back to a
 * placeholder actor on every real request.
 */
public interface AuthenticatedActor {
    UUID getUserId();
}
