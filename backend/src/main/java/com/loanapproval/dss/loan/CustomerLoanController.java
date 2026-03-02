package com.loanapproval.dss.loan;

import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.loan.dto.LoanSummaryResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/loans")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerLoanController {

    private final CustomerLoanService customerLoanService;

    public CustomerLoanController(CustomerLoanService customerLoanService) {
        this.customerLoanService = customerLoanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanDetailResponse createLoan(
        Authentication authentication,
        @Valid @RequestBody CreateLoanRequest request
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.create(user.id(), request);
    }

    @GetMapping
    public List<LoanSummaryResponse> listMyLoans(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.listMine(user.id());
    }

    @GetMapping("/{id}")
    public LoanDetailResponse getLoanDetail(
        Authentication authentication,
        @PathVariable("id") Long id
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return customerLoanService.getMineById(user.id(), id);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }
}
