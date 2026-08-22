package np.gov.digital.auth.controller;

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

@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

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

    @PostMapping("/{id}/approve")
    public CitizenEditRequest approve(
            @PathVariable java.util.UUID id,
            Authentication authentication,
            @RequestBody ApprovalRequest request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.approve(id, user.getUserId());
    }

    @PostMapping("/{id}/reject")
    public CitizenEditRequest reject(
            @PathVariable java.util.UUID id,
            Authentication authentication,
            @RequestBody RejectionRequest request) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        return approvalService.reject(
                id,
                user.getUserId(),
                request.getReason());
    }

    @GetMapping("/pending")
    public List<CitizenEditRequest> pending() {
        return approvalService.pendingRequests();
    }
}