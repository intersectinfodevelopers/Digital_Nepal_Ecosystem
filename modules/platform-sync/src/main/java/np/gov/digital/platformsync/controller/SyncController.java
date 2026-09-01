package np.gov.digital.platformsync.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Offline Sync", description = "Offline batch submission, sync status, and conflict resolution for ward-level devices")
@RestController
@RequestMapping("/v1/sync")
@Validated
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @Operation(
            summary = "Submit an offline sync batch",
            description = "Processes a batch of offline-recorded citizen changes submitted from a ward device.")
    @PostMapping("/submit")
    public ResponseEntity<SyncResponseDTO> submitSyncBatch(
            @Valid @RequestBody SyncBatchRequestDTO requestDTO) {

        SyncResponseDTO response =
                syncService.processBatch(requestDTO);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @Operation(summary = "Get the processing status of a sync batch")
    @GetMapping("/batch/{id}/status")
    public ResponseEntity<SyncBatchStatusResponseDTO> getBatchStatus(
            @Parameter(description = "Sync batch ID") @PathVariable UUID id) {

        SyncBatchStatusResponseDTO response =
                syncService.getBatchStatus(id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get the overall sync status for a ward")
    @GetMapping("/status/{wardId}")
    public ResponseEntity<WardSyncStatusResponseDTO> getWardSyncStatus(
            @Parameter(description = "Ward ID") @PathVariable UUID wardId) {

        WardSyncStatusResponseDTO response =
                syncService.getWardSyncStatus(wardId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Resolve a sync conflict",
            description = "Applies the chosen resolution to a detected sync conflict.")
    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<ConflictResponseDTO> resolveConflict(
            @Parameter(description = "Conflict ID") @PathVariable UUID id,
            @Valid @RequestBody ConflictResolutionRequestDTO request) throws Exception {

        ConflictResponseDTO response =
                syncService.resolveConflict(id, request);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "List sync conflicts",
            description = "Optionally filter by ward and/or conflict status.")
    @GetMapping("/conflicts")
    public ResponseEntity<List<ConflictResponseDTO>> getConflicts(
            @Parameter(description = "Filter by ward ID, optional") @RequestParam(required = false) UUID wardId,
            @Parameter(description = "Filter by conflict status, optional") @RequestParam(required = false) String status) {

        List<ConflictResponseDTO> conflicts =
                syncService.getConflicts(wardId, status);

        return ResponseEntity.ok(conflicts);
    }
}
