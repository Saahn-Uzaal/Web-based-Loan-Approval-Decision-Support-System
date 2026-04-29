package com.loanapproval.dss.repayment;

import com.loanapproval.dss.repayment.dto.ReviewPaymentConfirmationRequest;
import com.loanapproval.dss.repayment.dto.StaffPaymentConfirmationDetailResponse;
import com.loanapproval.dss.repayment.dto.StaffPaymentConfirmationSummaryResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
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
@RequestMapping("/api/staff/payment-confirmations")
@PreAuthorize("hasRole('STAFF')")
public class StaffPaymentConfirmationController {

    private final PaymentConfirmationService paymentConfirmationService;

    public StaffPaymentConfirmationController(PaymentConfirmationService paymentConfirmationService) {
        this.paymentConfirmationService = paymentConfirmationService;
    }

    @GetMapping
    public List<StaffPaymentConfirmationSummaryResponse> list(
            @RequestParam(value = "status", required = false) PaymentConfirmationStatus status) {
        return paymentConfirmationService.listForStaff(status);
    }

    @GetMapping("/{confirmationId}")
    public StaffPaymentConfirmationDetailResponse getDetail(@PathVariable("confirmationId") Long confirmationId) {
        return paymentConfirmationService.getForStaff(confirmationId);
    }

    @PostMapping("/{confirmationId}/review")
    public StaffPaymentConfirmationDetailResponse review(
            Authentication authentication,
            @PathVariable("confirmationId") Long confirmationId,
            @Valid @RequestBody ReviewPaymentConfirmationRequest request) {
        AuthenticatedUser user = extractUser(authentication);
        return paymentConfirmationService.review(user.id(), confirmationId, request);
    }

    @GetMapping("/{confirmationId}/proof")
    public ResponseEntity<Resource> downloadProof(@PathVariable("confirmationId") Long confirmationId) {
        return toDownloadResponse(paymentConfirmationService.loadProofForStaff(confirmationId));
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }

    private ResponseEntity<Resource> toDownloadResponse(PaymentProofDownload download) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null && !download.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(download.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.fileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.resource());
    }
}
