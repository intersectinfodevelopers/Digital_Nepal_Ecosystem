package np.gov.digital.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.dto.ApprovalRequest;
import np.gov.digital.auth.dto.CitizenEditRequestDto;
import np.gov.digital.auth.dto.RejectionRequest;
import np.gov.digital.auth.entity.CitizenEditRequest;
import np.gov.digital.auth.exception.SelfApprovalException;
import np.gov.digital.auth.service.ApprovalService;
import np.gov.digital.auth.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Approvals", description = "Ward/local-body admin approval workflow for citizen edit requests")
@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(
            summary = "Submit a citizen edit request for approval",
            description = "Only a fixed whitelist of low-risk fields (name, contact info, demographics) "
                    + "can be submitted — NID, citizenship number, DOB, sex, and ward are not editable "
                    + "this way.")
    @PostMapping
    public CitizenEditRequest submit(
            Authentication authentication,
            @Valid @RequestBody CitizenEditRequestDto request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.submit(
                user.getUserId(),
                request);
    }

    @Operation(
            summary = "Approve a pending citizen edit request",
            description = "An admin can never approve a request they submitted themselves.")
    @PostMapping("/{id}/approve")
    public CitizenEditRequest approve(
            @Parameter(description = "Edit request ID") @PathVariable java.util.UUID id,
            Authentication authentication,
            @RequestBody ApprovalRequest request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.approve(id, user.getUserId());
    }

    @Operation(summary = "Reject a pending citizen edit request")
    @PostMapping("/{id}/reject")
    public CitizenEditRequest reject(
            @Parameter(description = "Edit request ID") @PathVariable java.util.UUID id,
            Authentication authentication,
            @RequestBody RejectionRequest request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.reject(
                id,
                user.getUserId(),
                request.getReason());
    }

    @Operation(summary = "List all pending citizen edit requests")
    @GetMapping("/pending")
    public List<CitizenEditRequest> pending() {
        return approvalService.pendingRequests();
    }

    // 403 Forbidden — Governance Tiers §3: "Cannot approve a submission
    // they personally submitted." Backed by the no_self_approval CHECK
    // constraint (V17); this is just the clean error path in front of it.
    @ExceptionHandler(SelfApprovalException.class)
    public ResponseEntity<Map<String, String>> handleSelfApproval(SelfApprovalException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "SELF_APPROVAL_NOT_ALLOWED",
                "message", ex.getMessage(),
                "status", "403"
        ));
    }
}
