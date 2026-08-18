package np.gov.digital.platformgrievance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.gov.digital.platformgrievance.dto.*;
import np.gov.digital.platformgrievance.service.GrievanceEscalationService;
import np.gov.digital.platformgrievance.service.GrievanceService;
import np.gov.digital.platformgrievance.service.GrievanceStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/grievances")
@RequiredArgsConstructor
public class GrievanceController {

    private final GrievanceService grievanceService;
    private final GrievanceStateService grievanceStateService;
    private final GrievanceEscalationService grievanceEscalationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('WARD_ADMIN','LOCAL_BODY_ADMIN')")
    public ResponseEntity<GrievanceResponse> file(
            @Valid @RequestBody GrievanceFileRequest request) {
        log.info("POST /api/v1/grievances citizen={} category={}",
                request.getCitizenId(), request.getCategory());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(grievanceService.fileGrievance(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WARD_ADMIN','LOCAL_BODY_ADMIN')")
    public ResponseEntity<GrievanceResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody GrievanceTransitionRequest request) {
        log.info("PATCH /api/v1/grievances/{}/status → {}", id, request.getTargetStatus());
        return ResponseEntity.ok(grievanceStateService.transition(id, request));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('WARD_ADMIN','LOCAL_BODY_ADMIN')")
    public ResponseEntity<GrievanceResponse> escalate(
            @PathVariable UUID id,
            @Valid @RequestBody GrievanceEscalationRequest request,
            @RequestParam(required = false) String wardAdminMobile) {
        log.info("POST /api/v1/grievances/{}/escalate municipality={}",
                id, request.getMunicipalityId());
        return ResponseEntity.ok(
                grievanceEscalationService.escalateToJudicial(id, request, wardAdminMobile));
    }
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('WARD_ADMIN','LOCAL_BODY_ADMIN')")
    public ResponseEntity<GrievanceResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody GrievanceRejectionRequest request,
            @RequestParam(required = false) String wardAdminMobile) {
        log.info("POST /api/v1/grievances/{}/reject", id);
        return ResponseEntity.ok(
                grievanceEscalationService.closeInvalid(id, request, wardAdminMobile));
    }
}