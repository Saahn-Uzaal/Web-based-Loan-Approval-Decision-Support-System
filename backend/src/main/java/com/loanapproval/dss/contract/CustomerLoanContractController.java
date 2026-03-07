package com.loanapproval.dss.contract;

import com.loanapproval.dss.contract.dto.LoanContractResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/contracts")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerLoanContractController {

    private final LoanContractService loanContractService;

    public CustomerLoanContractController(LoanContractService loanContractService) {
        this.loanContractService = loanContractService;
    }

    @GetMapping("/{loanRequestId}")
    public LoanContractResponse getMine(
        Authentication authentication,
        @PathVariable("loanRequestId") Long loanRequestId
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return loanContractService.getMine(user.id(), loanRequestId);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }
}
