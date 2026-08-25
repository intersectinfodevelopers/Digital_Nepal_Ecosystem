package np.gov.digital.platformsync.integration;



import np.gov.digital.citizen.service.CitizenService;
import np.gov.digital.platformsync.PlatformSyncTestApplication;
import np.gov.digital.platformsync.batch.SyncBatchItemProcessor;
import np.gov.digital.platformsync.dto.CitizenRecordDTO;
import np.gov.digital.platformsync.dto.SyncBatchRequestDTO;
import np.gov.digital.platformsync.dto.SyncResponseDTO;
import np.gov.digital.platformsync.entity.SyncBatch;
import np.gov.digital.platformsync.repository.SyncBatchRepository;
import np.gov.digital.platformsync.repository.SyncRecordRepository;
import np.gov.digital.platformsync.service.SyncService;

import np.gov.digital.platformsync.test.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PlatformSyncTestApplication.class)
@ActiveProfiles("test")
class SyncBatchIntegrationTest  extends TestcontainersConfiguration {

//    @MockBean
//    private JobLauncher jobLauncher;
//
//    @MockBean
//    private Job syncJob;
//
//    @MockBean
//    private SyncBatchItemProcessor syncBatchItemProcessor;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job syncJob;

    @MockBean
    private CitizenService citizenService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private SyncBatchRepository syncBatchRepository;

    @Autowired
    private SyncRecordRepository syncRecordRepository;

    @Test
    void shouldSubmitSyncBatch() throws Exception {

        UUID batchId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID submittedBy = UUID.randomUUID();

        CitizenRecordDTO record = new CitizenRecordDTO();

        record.setLocalRecordId(UUID.randomUUID());
        record.setVersionNumber(1);

        SyncBatchRequestDTO request = new SyncBatchRequestDTO();

        request.setBatchId(batchId);
        request.setWardId(wardId);
        request.setSubmittedBy(submittedBy);
        request.setDeviceId("TEST-DEVICE-001");
        request.setRecords(List.of(record));

        SyncResponseDTO response =
                syncService.processBatch(request);

        JobParameters parameters =
                new JobParametersBuilder()
                        .addString("batchId", batchId.toString())
                        .addLong("time", System.currentTimeMillis())
                        .toJobParameters();

        jobLauncher.run(syncJob, parameters);

        assertNotNull(response);
        assertEquals(batchId, response.getBatchId());
        assertEquals("SUCCESS", response.getStatus());

        SyncBatch savedBatch =
                syncBatchRepository.findById(batchId)
                        .orElseThrow();

        assertEquals(batchId, savedBatch.getBatchId());
        assertEquals(wardId, savedBatch.getWardId());
        assertEquals(submittedBy, savedBatch.getSubmittedBy());
        assertEquals("TEST-DEVICE-001", savedBatch.getDeviceId());
        assertEquals(1, savedBatch.getRecordCount());

        assertEquals(
                1,
                syncRecordRepository.findByBatchId(batchId).size()
        );
    }
}
