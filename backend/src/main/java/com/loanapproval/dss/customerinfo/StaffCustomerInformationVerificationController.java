package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.customerinfo.dto.CustomerInformationVerificationResponse;
import com.loanapproval.dss.customerinfo.dto.ReviewCustomerInformationRequest;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationDetailResponse;
import com.loanapproval.dss.customerinfo.dto.StaffCustomerInformationSummaryResponse;
import com.loanapproval.dss.profile.CustomerIdentityCardDownload;
import com.loanapproval.dss.profile.CustomerIdentityCardSide;
import com.loanapproval.dss.profile.CustomerPayslipDownload;
import com.loanapproval.dss.profile.CustomerProfileService;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.verification.VerificationStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/information-verifications")
@PreAuthorize("hasRole('STAFF')")
public class StaffCustomerInformationVerificationController {

    private final CustomerInformationVerificationService customerInformationVerificationService;
    private final CustomerProfileService customerProfileService;

    public StaffCustomerInformationVerificationController(
        CustomerInformationVerificationService customerInformationVerificationService,
        CustomerProfileService customerProfileService
    ) {
        this.customerInformationVerificationService = customerInformationVerificationService;
        this.customerProfileService = customerProfileService;
    }

    @GetMapping
    public List<StaffCustomerInformationSummaryResponse> listCustomers(
        @RequestParam(value = "status", required = false) VerificationStatus status
    ) {
        return customerInformationVerificationService.listCustomers(status);
    }

    @GetMapping("/{customerId}")
    public StaffCustomerInformationDetailResponse getCustomerDetail(@PathVariable("customerId") Long customerId) {
        return customerInformationVerificationService.getCustomerDetail(customerId);
    }

    @GetMapping("/{customerId}/payslip")
    public ResponseEntity<Resource> downloadPayslip(@PathVariable("customerId") Long customerId) {
        return toDownloadResponse(customerProfileService.downloadPayslip(customerId));
    }

    @GetMapping("/{customerId}/id-card/front")
    public ResponseEntity<Resource> downloadIdentityCardFront(@PathVariable("customerId") Long customerId) {
        return toDownloadResponse(customerProfileService.downloadIdentityCard(customerId, CustomerIdentityCardSide.FRONT));
    }

    @GetMapping("/{customerId}/id-card/back")
    public ResponseEntity<Resource> downloadIdentityCardBack(@PathVariable("customerId") Long customerId) {
        return toDownloadResponse(customerProfileService.downloadIdentityCard(customerId, CustomerIdentityCardSide.BACK));
    }

    @PostMapping("/{customerId}/decision")
    @ResponseStatus(HttpStatus.OK)
    public CustomerInformationVerificationResponse review(
        Authentication authentication,
        @PathVariable("customerId") Long customerId,
        @Valid @RequestBody ReviewCustomerInformationRequest request
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return customerInformationVerificationService.review(customerId, user.id(), request);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }

    private ResponseEntity<Resource> toDownloadResponse(CustomerPayslipDownload download) {
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
                    .toString()
            )
            .body(download.resource());
    }

    private ResponseEntity<Resource> toDownloadResponse(CustomerIdentityCardDownload download) {
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
                    .toString()
            )
            .body(download.resource());
    }
}
