package np.gov.digital.platformgrievance.service;

import np.gov.digital.platformgrievance.entity.Grievance;
import np.gov.digital.platformgrievance.enums.GrievanceCategory;
import np.gov.digital.platformgrievance.enums.GrievanceStatus;
import np.gov.digital.platformgrievance.repository.GrievanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrievanceTrackingServiceTest {

    @Mock GrievanceRepository grievanceRepository;

    private GrievanceTrackingService service;

    @BeforeEach
    void setUp() {
        service = new GrievanceTrackingService(grievanceRepository);
    }

    private Grievance buildGrievance(String trackingCode) {
        return Grievance.builder()
                .id(UUID.randomUUID())
                .citizenId(UUID.randomUUID())     // PII — must NOT appear in response
                .municipalityId(UUID.randomUUID()) // PII — must NOT appear in response
                .trackingCode(trackingCode)
                .status(GrievanceStatus.IN_PROGRESS)
                .category(GrievanceCategory.DATA_INACCURACY)
                .description("Sensitive citizen description") // PII
                .filedAt(Instant.now().minusSeconds(3600))
                .slaDueAt(Instant.now().plusSeconds(172800))
                .slaBreached(false)
                .reopenCount((short) 0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void returnsCorrectTrackingCode() {
        Grievance g = buildGrievance("GRV-2026-000123");
        when(grievanceRepository.findByTrackingCode("GRV-2026-000123"))
                .thenReturn(Optional.of(g));

        var response = service.track("GRV-2026-000123");

        assertThat(response.getTrackingCode()).isEqualTo("GRV-2026-000123");
    }

    @Test
    void returnsStatusAndCategory() {
        Grievance g = buildGrievance("GRV-2026-000123");
        when(grievanceRepository.findByTrackingCode(any()))
                .thenReturn(Optional.of(g));

        var response = service.track("GRV-2026-000123");

        assertThat(response.getStatus()).isEqualTo(GrievanceStatus.IN_PROGRESS);
        assertThat(response.getCategory()).isEqualTo(GrievanceCategory.DATA_INACCURACY);
    }

    @Test
    void responseDoesNotContainCitizenId() {
        Grievance g = buildGrievance("GRV-2026-000123");
        when(grievanceRepository.findByTrackingCode(any()))
                .thenReturn(Optional.of(g));

        var response = service.track("GRV-2026-000123");

        // GrievanceTrackingResponse has no citizenId field — this confirms
        // at the type level that citizenId cannot be returned
        // If someone adds citizenId to the response, this test will fail to compile
        assertThat(response).isNotNull();
        // Verify the response class has no getter that would expose UUID citizenId
        assertThat(response.getClass().getDeclaredFields())
                .noneMatch(f -> f.getName().equalsIgnoreCase("citizenId"));
    }

    @Test
    void throwsWhenTrackingCodeNotFound() {
        when(grievanceRepository.findByTrackingCode("GRV-2026-999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.track("GRV-2026-999999"))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void slaBreachedFlagIsReturned() {
        Grievance g = buildGrievance("GRV-2026-000123");
        g.setSlaBreached(true);
        when(grievanceRepository.findByTrackingCode(any()))
                .thenReturn(Optional.of(g));

        var response = service.track("GRV-2026-000123");

        assertThat(response.isSlaBreached()).isTrue();
    }

    @Test
    void messageIsPopulatedForEachStatus() {
        for (GrievanceStatus status : GrievanceStatus.values()) {
            Grievance g = buildGrievance("GRV-2026-000123");
            g.setStatus(status);
            when(grievanceRepository.findByTrackingCode(any()))
                    .thenReturn(Optional.of(g));

            var response = service.track("GRV-2026-000123");

            assertThat(response.getMessage())
                    .as("Message should not be blank for status " + status)
                    .isNotBlank();
        }
    }
}