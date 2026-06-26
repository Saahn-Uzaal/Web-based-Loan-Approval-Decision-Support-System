package com.loanapproval.dss.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/profile")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CustomerProfileController(
        CustomerProfileService customerProfileService,
        ObjectMapper objectMapper,
        Validator validator
    ) {
        this.customerProfileService = customerProfileService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping
    public CustomerProfileResponse getCurrentProfile(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerProfileService.getByUserId(user.id());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CustomerProfileResponse upsertProfileJson(
        Authentication authentication,
        @Valid @RequestBody CustomerProfileRequest request
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return customerProfileService.upsert(user.id(), request, CustomerProfileFiles.empty());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CustomerProfileResponse upsertProfileMultipart(
        Authentication authentication,
        @RequestPart("profile") String profileJson,
        @RequestPart(value = "payslip", required = false) MultipartFile payslip,
        @RequestPart(value = "idCardFront", required = false) MultipartFile idCardFront,
        @RequestPart(value = "idCardBack", required = false) MultipartFile idCardBack
    ) {
        AuthenticatedUser user = extractUser(authentication);
        CustomerProfileRequest request = parseAndValidateProfile(profileJson);
        return customerProfileService.upsert(user.id(), request, new CustomerProfileFiles(payslip, idCardFront, idCardBack));
    }

    @GetMapping("/payslip")
    public ResponseEntity<Resource> downloadCurrentPayslip(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return toDownloadResponse(customerProfileService.downloadPayslip(user.id()));
    }

    @GetMapping("/id-card/front")
    public ResponseEntity<Resource> downloadCurrentIdentityCardFront(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return toDownloadResponse(customerProfileService.downloadIdentityCard(user.id(), CustomerIdentityCardSide.FRONT));
    }

    @GetMapping("/id-card/back")
    public ResponseEntity<Resource> downloadCurrentIdentityCardBack(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return toDownloadResponse(customerProfileService.downloadIdentityCard(user.id(), CustomerIdentityCardSide.BACK));
    }

    private CustomerProfileRequest parseAndValidateProfile(String profileJson) {
        try {
            CustomerProfileRequest request = objectMapper.readValue(profileJson, CustomerProfileRequest.class);
            Set<ConstraintViolation<CustomerProfileRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
            }
            return request;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu hồ sơ không đúng định dạng JSON", ex);
        }
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
