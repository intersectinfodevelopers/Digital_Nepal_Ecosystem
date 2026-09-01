package np.gov.digital.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.dto.ApprovalRequest;
import np.gov.digital.auth.dto.CitizenEditRequestDto;
import np.gov.digital.auth.dto.RejectionRequest;
import np.gov.digital.auth.entity.CitizenEditRequest;
import np.gov.digital.auth.service.ApprovalService;
import np.gov.digital.auth.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Approvals", description = "Ward/local-body admin approval workflow for citizen edit requests")
@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(summary = "Submit a citizen edit request for approval")
    @PostMapping
    public CitizenEditRequest submit(
            Authentication authentication,
            @RequestBody CitizenEditRequestDto request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.submit(
                user.getUserId(),
                request);
    }

    @Operation(summary = "Approve a pending citizen edit request")
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
}
