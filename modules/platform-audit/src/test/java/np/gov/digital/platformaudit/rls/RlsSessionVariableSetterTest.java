package np.gov.digital.platformaudit.rls;

import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * NOTE: previously authenticated via JwtAuthenticationToken — the type
 * Spring's OAuth2 resource-server JWT decoder produces, which this app
 * never actually uses. Rewritten to authenticate the way the app's real
 * JwtAuthenticationFilter does: a plain UsernamePasswordAuthenticationToken
 * wrapping an AuthenticatedActor principal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RlsSessionVariableSetter")
class RlsSessionVariableSetterTest {

    @Mock private Connection connection;
    @Mock private Statement  statement;

    private RlsSessionVariableSetter setter;

    private static final String WARD_UUID         = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String MUNICIPALITY_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PROVINCE_UUID     = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @BeforeEach
    void setUp() throws Exception {
        setter = new RlsSessionVariableSetter();
        lenient().when(connection.createStatement()).thenReturn(statement);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Ward admin — sets ward_id only")
    void wardAdmin_setsWardIdOnly() throws Exception {
        mockActor("WARD_ADMIN", WARD_UUID, null, null);
        setter.setExplicit(connection, WARD_UUID, null, null);
        verify(statement).execute(contains("app.current_ward_id = '" + WARD_UUID));
        verify(statement, never()).execute(contains("app.current_municipality_id"));
        verify(statement, never()).execute(contains("app.current_province_id"));
    }

    @Test
    @DisplayName("Local body admin — sets municipality_id only")
    void localBodyAdmin_setsMunicipalityOnly() throws Exception {
        mockActor("LOCAL_BODY_ADMIN", null, MUNICIPALITY_UUID, null);
        setter.setExplicit(connection, null, MUNICIPALITY_UUID, null);
        verify(statement, never()).execute(contains("app.current_ward_id"));
        verify(statement).execute(contains("app.current_municipality_id = '" + MUNICIPALITY_UUID));
        verify(statement, never()).execute(contains("app.current_province_id"));
    }

    @Test
    @DisplayName("Province admin — sets province_id only")
    void provinceAdmin_setsProvinceOnly() throws Exception {
        mockActor("PROVINCE_ADMIN", null, null, PROVINCE_UUID);
        setter.setExplicit(connection, null, null, PROVINCE_UUID);
        verify(statement, never()).execute(contains("app.current_ward_id"));
        verify(statement, never()).execute(contains("app.current_municipality_id"));
        verify(statement).execute(contains("app.current_province_id = '" + PROVINCE_UUID));
    }

    @Test
    @DisplayName("Central admin — no SET LOCAL calls")
    void centralAdmin_noSetLocalCalls() throws Exception {
        mockActor("CENTRAL_ADMIN", null, null, null);
        setter.setExplicit(connection, null, null, null);
        verify(statement, never()).execute(anyString());
    }

    @Test
    @DisplayName("Invalid UUID — throws IllegalArgumentException")
    void invalidUuid_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> setter.setExplicit(connection, "'; DROP TABLE citizen; --", null, null));
    }

    @Test
    @DisplayName("No auth in SecurityContext — skips gracefully")
    void noAuth_skipsGracefully() throws Exception {
        // No connection needed — just verify no exception
        RlsSessionVariableSetter noAuthSetter = new RlsSessionVariableSetter();
        Connection mockConn = mock(Connection.class);
        Statement mockStmt  = mock(Statement.class);
        lenient().when(mockConn.createStatement()).thenReturn(mockStmt);
        assertDoesNotThrow(() -> noAuthSetter.setFromSecurityContext(mockConn));
    }

    @Test
    @DisplayName("setFromSecurityContext — reads scope from AuthenticatedActor principal")
    void setFromSecurityContext_readsFromActor() throws Exception {
        mockActor("WARD_ADMIN", WARD_UUID, null, null);
        setter.setFromSecurityContext(connection);
        verify(statement).execute(contains("app.current_ward_id = '" + WARD_UUID));
    }

    // ----------------------------------------------------------------

    private void mockActor(String role, String wardId,
                         String municipalityId, String provinceId) {
        AuthenticatedActor actor = new AuthenticatedActor() {
            @Override public UUID getUserId() { return UUID.randomUUID(); }
            @Override public String getRole() { return role; }
            @Override public UUID getWardId() { return wardId != null ? UUID.fromString(wardId) : null; }
            @Override public UUID getMunicipalityId() { return municipalityId != null ? UUID.fromString(municipalityId) : null; }
            @Override public UUID getProvinceId() { return provinceId != null ? UUID.fromString(provinceId) : null; }
        };
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
    }
}
