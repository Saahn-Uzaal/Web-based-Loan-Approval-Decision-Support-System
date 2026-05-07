package com.loanapproval.dss.repayment;

import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRepaymentController {

    private final RepaymentService repaymentService;
    private final PaymentConfirmationService paymentConfirmationService;

    public CustomerRepaymentController(
            RepaymentService repaymentService,
            PaymentConfirmationService paymentConfirmationService) {
        this.repaymentService = repaymentService;
        this.paymentConfirmationService = paymentConfirmationService;
    }

    @GetMapping
    public RepaymentHistoryResponse listMyRepayments(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return repaymentService.listMine(user.id())
                .withConfirmationRequests(paymentConfirmationService.listMine(user.id()));
    }

    @GetMapping("/paged")
    public RepaymentHistoryResponse listMyRepaymentsPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser user = extractUser(authentication);
        return repaymentService.listMinePaged(user.id(), page, size)
                .withConfirmationRequests(paymentConfirmationService.listMine(user.id()));
    }

    @PostMapping("/confirmations")
    @ResponseStatus(HttpStatus.CREATED)
    public com.loanapproval.dss.repayment.dto.PaymentConfirmationItemResponse createPaymentConfirmation(
            Authentication authentication,
            @RequestParam("loanRequestId") Long loanRequestId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            @RequestParam("proof") MultipartFile proof) {
        AuthenticatedUser user = extractUser(authentication);
        return paymentConfirmationService.create(user.id(), loanRequestId, note, proof, idempotencyKey);
    }

    @PostMapping("/confirmations/{confirmationId}/cancel")
    public com.loanapproval.dss.repayment.dto.PaymentConfirmationItemResponse cancelPaymentConfirmation(
            Authentication authentication,
            @PathVariable("confirmationId") Long confirmationId) {
        AuthenticatedUser user = extractUser(authentication);
        return paymentConfirmationService.cancelByCustomer(user.id(), confirmationId);
    }

    @PostMapping("/confirmations/{confirmationId}/replace")
    @ResponseStatus(HttpStatus.CREATED)
    public com.loanapproval.dss.repayment.dto.PaymentConfirmationItemResponse replacePaymentConfirmation(
            Authentication authentication,
            @PathVariable("confirmationId") Long confirmationId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("proof") MultipartFile proof) {
        AuthenticatedUser user = extractUser(authentication);
        return paymentConfirmationService.replaceByCustomer(user.id(), confirmationId, note, proof);
    }

    @GetMapping("/confirmations/{confirmationId}/proof")
    public ResponseEntity<Resource> downloadPaymentProof(
            Authentication authentication,
            @PathVariable("confirmationId") Long confirmationId) {
        AuthenticatedUser user = extractUser(authentication);
        return toDownloadResponse(paymentConfirmationService.loadProofForCustomer(user.id(), confirmationId));
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
