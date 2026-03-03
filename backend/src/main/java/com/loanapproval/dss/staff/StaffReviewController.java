package com.loanapproval.dss.staff;

import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.staff.dto.StaffDecisionRequest;
import com.loanapproval.dss.staff.dto.StaffDecisionResponse;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffRequestSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
        @RequestParam(value = "status", required = false) LoanStatus status
    ) {
        return staffReviewService.listReviewQueue(status);
    }

    @GetMapping("/{id}")
    public StaffRequestDetailResponse getRequestDetail(@PathVariable("id") Long id) {
        return staffReviewService.getRequestDetail(id);
    }

    @PostMapping("/{id}/decision")
    public StaffDecisionResponse submitDecision(
        Authentication authentication,
        @PathVariable("id") Long id,
        @Valid @RequestBody StaffDecisionRequest request
    ) {
        AuthenticatedUser staff = extractUser(authentication);
        return staffReviewService.submitDecision(staff.id(), id, request);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }
}
