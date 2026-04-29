package com.loanapproval.dss.staff;

import com.loanapproval.dss.loan.LoanDocumentDownload;
import com.loanapproval.dss.loan.LoanDocumentType;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/requests")
@PreAuthorize("hasRole('STAFF')")
public class StaffReviewController {

    private final StaffReviewService staffReviewService;

    public StaffReviewController(StaffReviewService staffReviewService) {
        this.staffReviewService = staffReviewService;
    }

    @GetMapping
    public List<StaffRequestSummaryResponse> listReviewQueue(
            @RequestParam(value = "status", required = false) LoanStatus status) {
        return staffReviewService.listReviewQueue(status);
    }

    @GetMapping("/paged")
    public PageResponse<StaffRequestSummaryResponse> listReviewQueuePaged(
            @RequestParam(value = "status", required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return staffReviewService.listReviewQueuePaged(status, page, size);
    }

    @GetMapping("/operations")
    public List<StaffRequestSummaryResponse> listOperationQueue(
            @RequestParam(value = "status", required = false) LoanStatus status) {
        return staffReviewService.listOperationQueue(status);
    }

    @GetMapping("/operations/paged")
    public PageResponse<StaffRequestSummaryResponse> listOperationQueuePaged(
            @RequestParam(value = "status", required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return staffReviewService.listOperationQueuePaged(status, page, size);
    }

    @GetMapping("/{id}")
    public StaffRequestDetailResponse getRequestDetail(@PathVariable("id") Long id) {
        return staffReviewService.getRequestDetail(id);
    }

    @PostMapping("/{id}/decision")
    public StaffDecisionResponse submitDecision(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody StaffDecisionRequest request) {
        AuthenticatedUser staff = extractUser(authentication);
        return staffReviewService.submitDecision(staff.id(), id, request);
    }

    @PostMapping("/{id}/complete-contract")
    public StaffRequestDetailResponse completeContract(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser staff = extractUser(authentication);
        return staffReviewService.completeContract(staff.id(), id);
    }

    @PostMapping("/{id}/disburse")
    public StaffRequestDetailResponse disburseLoan(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser staff = extractUser(authentication);
        return staffReviewService.disburseLoan(staff.id(), id);
    }

    @GetMapping("/{id}/documents/{documentType}")
    public ResponseEntity<Resource> downloadLoanDocument(
            @PathVariable("id") Long id,
            @PathVariable("documentType") LoanDocumentType documentType) {
        return toDownloadResponse(staffReviewService.downloadDocument(id, documentType));
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }

    private ResponseEntity<Resource> toDownloadResponse(LoanDocumentDownload download) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null && !download.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(download.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.fileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.resource());
    }
}
