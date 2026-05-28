package com.loanapproval.dss.creditcheck;

import com.loanapproval.dss.creditcheck.dto.CreditBureauRecordResponse;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauRecordRequest;
import com.loanapproval.dss.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-bureau-records")
public class CreditBureauManagementController {

    private final CreditBureauManagementService creditBureauManagementService;

    public CreditBureauManagementController(CreditBureauManagementService creditBureauManagementService) {
        this.creditBureauManagementService = creditBureauManagementService;
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public PageResponse<CreditBureauRecordResponse> listPaged(
        @RequestParam(value = "status", required = false) CreditBureauStatus status,
        @RequestParam(value = "query", required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return creditBureauManagementService.listPaged(status, query, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditBureauRecordResponse create(@Valid @RequestBody UpsertCreditBureauRecordRequest request) {
        return creditBureauManagementService.create(request);
    }

    @PutMapping("/{identityNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public CreditBureauRecordResponse update(
        @PathVariable("identityNumber") String identityNumber,
        @Valid @RequestBody UpsertCreditBureauRecordRequest request
    ) {
        return creditBureauManagementService.update(identityNumber, request);
    }

    @DeleteMapping("/{identityNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("identityNumber") String identityNumber) {
        creditBureauManagementService.delete(identityNumber);
    }
}
