package np.gov.digital.platformaudit.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuditLogServiceTest
 * Day 5 — citizen_events writer tests
 *
 * NOTE: these tests used to authenticate via a JwtAuthenticationToken —
 * the type Spring's OAuth2 resource-server JWT decoder produces, which this
 * app never actually uses. That meant the tests were "passing" while
 * exercising a code path real requests never hit (AuditLogService checked
 * for that exact type and always found it absent in production, so it
 * silently wrote nothing — see AuditLogService's class Javadoc). Rewritten
 * to authenticate the way the app's real JwtAuthenticationFilter does: a
 * plain UsernamePasswordAuthenticationToken wrapping an AuthenticatedActor
 * principal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(jdbcTemplate);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("log — writes one row to audit_logs table")
    void log_writesOneRow() {
        UUID citizenId = UUID.randomUUID();
        mockActor(UUID.randomUUID(), "WARD_ADMIN", UUID.randomUUID(), null, null);

        auditLogService.log(AuditEventType.CITIZEN_REGISTERED, citizenId, "Citizen registered");

        verify(jdbcTemplate, times(1)).update(anyString(),
            any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("log — uses actor ID from authenticated principal")
    void log_usesActorFromJwt() {
        UUID userId = UUID.randomUUID();
        mockActor(userId, "WARD_ADMIN", UUID.randomUUID(), null, null);

        auditLogService.log(AuditEventType.CITIZEN_REGISTERED, "Test registration");

        verify(jdbcTemplate, times(1)).update(anyString(),
            any(), any(), any(), eq(userId.toString()), any(), any());
    }

    @Test
    @DisplayName("log — NEVER throws even if DB fails")
    void log_neverThrowsOnDbFailure() {
        mockActor(UUID.randomUUID(), "WARD_ADMIN", UUID.randomUUID(), null, null);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertDoesNotThrow(() ->
                auditLogService.log(AuditEventType.FAILED_LOGIN, "Login failed"));
    }

    @Test
    @DisplayName("log — skips anonymous events when no authenticated actor is present")
    void log_skipsAnonymousWithNoAuth() {
        assertDoesNotThrow(() ->
                auditLogService.log(AuditEventType.FAILED_LOGIN, "No auth"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("log — FAILED_LOGIN event type resolves to AUTH entity")
    void log_failedLogin_resolvesToAuthEntity() {
        mockActor(UUID.randomUUID(), "WARD_ADMIN", UUID.randomUUID(), null, null);
        auditLogService.log(AuditEventType.FAILED_LOGIN, "Wrong password");

        verify(jdbcTemplate).update(anyString(),
            isNull(String.class), eq("FAILED_LOGIN"), anyString(), anyString(),
            eq("WARD_ADMIN"), anyString());
    }

    @Test
    @DisplayName("log — CITIZEN_REGISTERED event resolves to CITIZEN entity")
    void log_citizenRegistered_resolvesToCitizenEntity() {
        UUID citizenId = UUID.randomUUID();
        mockActor(UUID.randomUUID(), "WARD_ADMIN", UUID.randomUUID(), null, null);
        auditLogService.log(AuditEventType.CITIZEN_REGISTERED, citizenId, "Registered");

        verify(jdbcTemplate).update(anyString(),
            eq(citizenId.toString()), eq("CITIZEN_REGISTERED"), anyString(),
            anyString(), eq("WARD_ADMIN"), anyString());
    }

    @Test
    @DisplayName("log — CENTRAL_ADMIN with no ward/municipality/province scope resolves to national sentinel")
    void log_centralAdmin_resolvesToNationalSentinel() {
        mockActor(UUID.randomUUID(), "CENTRAL_ADMIN", null, null, null);

        auditLogService.log(AuditEventType.CITIZEN_REGISTERED, UUID.randomUUID(), "Registered");

        verify(jdbcTemplate).update(anyString(),
            any(), any(), any(), any(), eq("CENTRAL_ADMIN"),
            eq("00000000-0000-0000-0000-000000000000"));
    }

    // ----------------------------------------------------------------

    private void mockActor(UUID userId, String role, UUID wardId, UUID municipalityId, UUID provinceId) {
        AuthenticatedActor actor = new AuthenticatedActor() {
            @Override public UUID getUserId() { return userId; }
            @Override public String getRole() { return role; }
            @Override public UUID getWardId() { return wardId; }
            @Override public UUID getMunicipalityId() { return municipalityId; }
            @Override public UUID getProvinceId() { return provinceId; }
        };
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
    }
}
