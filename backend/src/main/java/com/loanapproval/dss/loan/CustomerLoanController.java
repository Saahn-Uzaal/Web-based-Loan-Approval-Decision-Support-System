package com.loanapproval.dss.loan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.loan.dto.LoanSummaryResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.shared.PageResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/loans")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerLoanController {

    private final CustomerLoanService customerLoanService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CustomerLoanController(
            CustomerLoanService customerLoanService,
            ObjectMapper objectMapper,
            Validator validator) {
        this.customerLoanService = customerLoanService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoanDetailResponse createLoan(
            Authentication authentication,
            @Valid @RequestBody CreateLoanRequest request) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.createDraft(user.id(), request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoanDetailResponse createLoanMultipart(
            Authentication authentication,
            @RequestPart("loan") String loanJson,
            @RequestPart(value = "vehicleRegistration", required = false) MultipartFile vehicleRegistration,
            @RequestPart(value = "licensePlateImage", required = false) MultipartFile licensePlateImage,
            @RequestPart(value = "idCardFront", required = false) MultipartFile idCardFront,
            @RequestPart(value = "idCardBack", required = false) MultipartFile idCardBack,
            @RequestPart(value = "faceCapture", required = false) MultipartFile faceCapture) {
        AuthenticatedUser user = extractUser(authentication);
        CreateLoanRequest request = parseAndValidateLoan(loanJson);
        return customerLoanService.create(
                user.id(),
                request,
                new LoanApplicationFiles(
                        vehicleRegistration,
                        licensePlateImage,
                        idCardFront,
                        idCardBack,
                        faceCapture,
                        List.of()));
    }

    @GetMapping
    public List<LoanSummaryResponse> listMyLoans(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.listMine(user.id());
    }

    @GetMapping("/paged")
    public PageResponse<LoanSummaryResponse> listMyLoansPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.listMinePaged(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public LoanDetailResponse getLoanDetail(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.getMineById(user.id(), id);
    }

    @PostMapping("/{id}/accept")
    public LoanDetailResponse acceptApprovedLoan(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.acceptApprovedLoan(user.id(), id);
    }

    @PostMapping("/{id}/withdraw")
    public LoanDetailResponse withdrawLoan(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.withdrawLoan(user.id(), id);
    }

    @PostMapping(value = "/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoanDetailResponse resubmitLoanMultipart(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestPart(value = "supplementalDocuments", required = false) List<MultipartFile> supplementalDocuments) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.resubmitLoan(
                user.id(),
                id,
                new LoanApplicationFiles(
                        null,
                        null,
                        null,
                        null,
                        null,
                        supplementalDocuments != null ? supplementalDocuments : List.of()));
    }

    @PostMapping("/{id}/resubmit")
    public LoanDetailResponse resubmitLoan(
            Authentication authentication,
            @PathVariable("id") Long id) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.resubmitLoan(user.id(), id);
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> downloadLoanDocument(
            Authentication authentication,
            @PathVariable("id") Long id,
            @PathVariable("documentId") Long documentId) {
        AuthenticatedUser user = extractUser(authentication);
        return toDownloadResponse(customerLoanService.downloadDocument(user.id(), id, documentId));
    }

    private CreateLoanRequest parseAndValidateLoan(String loanJson) {
        try {
            CreateLoanRequest request = objectMapper.readValue(loanJson, CreateLoanRequest.class);
            Set<ConstraintViolation<CreateLoanRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
            }
            return request;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu hồ sơ vay không đúng định dạng JSON", ex);
        }
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
