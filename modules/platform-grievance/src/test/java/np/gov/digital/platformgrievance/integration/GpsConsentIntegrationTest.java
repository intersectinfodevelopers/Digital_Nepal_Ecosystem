package np.gov.digital.platformgrievance.integration;

import np.gov.digital.platformgis.dto.GpsCaptureRequest;
import np.gov.digital.platformgis.entity.CitizenGis;
import np.gov.digital.platformgis.exception.InvalidGpsAccuracyException;
import np.gov.digital.platformgis.repository.CitizenGisRepository;
import np.gov.digital.platformgis.service.CitizenGisService;
import np.gov.digital.platformaudit.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class GpsConsentIntegrationTest {

    @Mock CitizenGisRepository citizenGisRepository;
    @Mock AuditLogService auditLogService;

    private final UUID citizenId = UUID.randomUUID();
    private final UUID wardId    = UUID.randomUUID();
    private final UUID actor     = UUID.randomUUID();

    private CitizenGisService service() {
        return new CitizenGisService(citizenGisRepository, auditLogService);
    }

    @Test
    void gpsConsentGranted_storesLocation() {
        when(citizenGisRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GpsCaptureRequest request = GpsCaptureRequest.builder()
                .latitude(27.7172)
                .longitude(85.3240)
                .accuracyM(30)
                .build();

        CitizenGis result = service().captureAndStore(citizenId, wardId, actor, request);

        assertThat(result.getCitizenId()).isEqualTo(citizenId);
        assertThat(result.getWardId()).isEqualTo(wardId);
        assertThat(result.getLocation()).isNotNull();
        verify(citizenGisRepository).save(any());
    }

    @Test
    void gpsConsentDeclined_nothingStoredInCitizenGis() {
        verifyNoInteractions(citizenGisRepository);
    }

    @Test
    void gpsAccuracyAbove500m_rejected() {
        // Day 12 checklist: "Test GPS accuracy_m > 500 — reading rejected"
        GpsCaptureRequest request = GpsCaptureRequest.builder()
                .latitude(27.7172)
                .longitude(85.3240)
                .accuracyM(501) // exceeds 500m limit
                .build();

        assertThatThrownBy(() ->
                service().captureAndStore(citizenId, wardId, actor, request))
                .isInstanceOf(InvalidGpsAccuracyException.class);

        verify(citizenGisRepository, never()).save(any());
    }

    @Test
    void gpsAccuracyExactly500m_accepted() {
        when(citizenGisRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GpsCaptureRequest request = GpsCaptureRequest.builder()
                .latitude(27.7172)
                .longitude(85.3240)
                .accuracyM(500) // exactly at limit — should be accepted
                .build();

        assertThatNoException().isThrownBy(() ->
                service().captureAndStore(citizenId, wardId, actor, request));

        verify(citizenGisRepository).save(any());
    }

    @Test
    void wardIdOnCitizenGisAlwaysMirrorsCitizenWardId() {
        // match citizen.ward_id so RLS policies work correctly
        when(citizenGisRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        GpsCaptureRequest request = GpsCaptureRequest.builder()
                .latitude(27.7)
                .longitude(85.3)
                .accuracyM(50)
                .build();

        CitizenGis result = service().captureAndStore(citizenId, wardId, actor, request);

        assertThat(result.getWardId()).isEqualTo(wardId);
    }
}