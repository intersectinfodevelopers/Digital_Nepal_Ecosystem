package np.gov.digital.platformsync.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import np.gov.digital.platformsync.dto.ConflictResolutionRequestDTO;
import np.gov.digital.platformsync.dto.ConflictResponseDTO;
import np.gov.digital.platformsync.dto.SyncBatchRequestDTO;
import np.gov.digital.platformsync.dto.SyncBatchStatusResponseDTO;
import np.gov.digital.platformsync.dto.SyncResponseDTO;
import np.gov.digital.platformsync.dto.WardSyncStatusResponseDTO;
import np.gov.digital.platformsync.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sync")
@Validated
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/submit")
    public ResponseEntity<SyncResponseDTO> submitSyncBatch(
            @Valid @RequestBody SyncBatchRequestDTO requestDTO) {

        SyncResponseDTO response =
                syncService.processBatch(requestDTO);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/batch/{id}/status")
    public ResponseEntity<SyncBatchStatusResponseDTO> getBatchStatus(
            @PathVariable UUID id) {

        SyncBatchStatusResponseDTO response =
                syncService.getBatchStatus(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{wardId}")
    public ResponseEntity<WardSyncStatusResponseDTO> getWardSyncStatus(
            @PathVariable UUID wardId) {

        WardSyncStatusResponseDTO response =
                syncService.getWardSyncStatus(wardId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<ConflictResponseDTO> resolveConflict(
            @PathVariable UUID id,
            @Valid @RequestBody ConflictResolutionRequestDTO request) throws Exception {

        ConflictResponseDTO response =
                syncService.resolveConflict(id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<ConflictResponseDTO>> getConflicts(
            @RequestParam(required = false) UUID wardId,
            @RequestParam(required = false) String status) {

        List<ConflictResponseDTO> conflicts =
                syncService.getConflicts(wardId, status);

        return ResponseEntity.ok(conflicts);
    }
}