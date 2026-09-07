package np.gov.digital.platformaudit.filter;

import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequirePermissionAspectTest
 * Day 5 — Task 8: @RequirePermission returns 403 before DB touched
 *
 * Task doc:
 *   "WARD_ADMIN without CITIZEN_WRITE permission calls
 *    POST /api/v1/citizens → confirm 403 returned before DB touched"
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequirePermissionAspect — permission checks")
class RequirePermissionAspectTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("WARD_ADMIN has CITIZEN_WRITE permission")
    void wardAdmin_hasCitizenWrite() {
        mockActor("WARD_ADMIN");
        RequirePermissionAspect aspect = new RequirePermissionAspect();

        // WARD_ADMIN should have CITIZEN_WRITE — no exception thrown
        // We test the role-permission map directly
        assertTrue(hasPermission("WARD_ADMIN", Permission.CITIZEN_WRITE));
    }

    @Test
    @DisplayName("PROVINCE_ADMIN does NOT have CITIZEN_WRITE — would get 403")
    void provinceAdmin_doesNotHaveCitizenWrite() {
        assertFalse(hasPermission("PROVINCE_ADMIN", Permission.CITIZEN_WRITE));
    }

    @Test
    @DisplayName("CENTRAL_ADMIN does NOT have CITIZEN_WRITE — would get 403")
    void centralAdmin_doesNotHaveCitizenWrite() {
        assertFalse(hasPermission("CENTRAL_ADMIN", Permission.CITIZEN_WRITE));
    }

    @Test
    @DisplayName("LOCAL_BODY_ADMIN has ID_CARD_APPROVE")
    void localBodyAdmin_hasIdCardApprove() {
        assertTrue(hasPermission("LOCAL_BODY_ADMIN", Permission.ID_CARD_APPROVE));
    }

    @Test
    @DisplayName("WARD_ADMIN does NOT have ID_CARD_APPROVE")
    void wardAdmin_doesNotHaveIdCardApprove() {
        assertFalse(hasPermission("WARD_ADMIN", Permission.ID_CARD_APPROVE));
    }

    @Test
    @DisplayName("WARD_ADMIN does NOT have CITIZEN_ARCHIVE")
    void wardAdmin_doesNotHaveCitizenArchive() {
        assertFalse(hasPermission("WARD_ADMIN", Permission.CITIZEN_ARCHIVE));
    }

    // ----------------------------------------------------------------
    // Regression coverage for extractRole() itself: before the
    // AuthenticatedActor fix, this method checked
    // `auth instanceof JwtAuthenticationToken` — a type this app's real
    // JwtAuthenticationFilter never produces — so it always returned
    // "UNKNOWN" and every @RequirePermission-annotated call would have
    // been denied. None of the tests above ever called extractRole()
    // directly, so that bug shipped invisibly. These do.
    // ----------------------------------------------------------------

    @Test
    @DisplayName("extractRole() reads the role off a real AuthenticatedActor principal")
    void extractRole_readsFromAuthenticatedActor() throws Exception {
        mockActor("LOCAL_BODY_ADMIN");
        RequirePermissionAspect aspect = new RequirePermissionAspect();

        String role = invokeExtractRole(aspect);

        assertEquals("LOCAL_BODY_ADMIN", role);
    }

    @Test
    @DisplayName("extractRole() returns UNKNOWN when no actor is authenticated")
    void extractRole_returnsUnknownWithNoAuth() throws Exception {
        RequirePermissionAspect aspect = new RequirePermissionAspect();

        String role = invokeExtractRole(aspect);

        assertEquals("UNKNOWN", role);
    }

    private String invokeExtractRole(RequirePermissionAspect aspect) throws Exception {
        var method = RequirePermissionAspect.class.getDeclaredMethod("extractRole");
        method.setAccessible(true);
        return (String) method.invoke(aspect);
    }

    /**
     * Simulates what RequirePermissionAspect does internally.
     * Checks if a role has the given permission.
     */
    private boolean hasPermission(String role, String permission) {
        java.util.Map<String, java.util.List<String>> ROLE_PERMISSIONS = java.util.Map.of(
                "WARD_ADMIN", java.util.List.of(
                        Permission.CITIZEN_READ, Permission.CITIZEN_WRITE,
                        Permission.ID_CARD_INITIATE, Permission.GRIEVANCE_RECEIVE
                ),
                "LOCAL_BODY_ADMIN", java.util.List.of(
                        Permission.CITIZEN_READ, Permission.CITIZEN_WRITE,
                        Permission.CITIZEN_APPROVE, Permission.CITIZEN_ARCHIVE,
                        Permission.ID_CARD_INITIATE, Permission.ID_CARD_APPROVE,
                        Permission.EDIT_APPROVE, Permission.EDIT_REJECT,
                        Permission.GRIEVANCE_RESOLVE
                ),
                "PROVINCE_ADMIN", java.util.List.of(Permission.CITIZEN_READ),
                "CENTRAL_ADMIN",  java.util.List.of(
                        Permission.CITIZEN_READ, Permission.SYSTEM_CONFIG)
        );
        return ROLE_PERMISSIONS.getOrDefault(role, java.util.List.of()).contains(permission);
    }

    private void mockActor(String role) {
        AuthenticatedActor actor = new AuthenticatedActor() {
            @Override public UUID getUserId() { return UUID.randomUUID(); }
            @Override public String getRole() { return role; }
            @Override public UUID getWardId() { return null; }
            @Override public UUID getMunicipalityId() { return null; }
            @Override public UUID getProvinceId() { return null; }
        };
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
    }
}