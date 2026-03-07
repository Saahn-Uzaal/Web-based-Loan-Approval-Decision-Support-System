package com.loanapproval.dss.verification;

import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.verification.dto.CustomerVerificationResponse;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/verifications")
@PreAuthorize("hasRole('STAFF')")
public class CustomerVerificationController {

    private final CustomerVerificationService customerVerificationService;

    public CustomerVerificationController(CustomerVerificationService customerVerificationService) {
        this.customerVerificationService = customerVerificationService;
    }

    @GetMapping("/{customerId}")
    public CustomerVerificationResponse getByCustomerId(@PathVariable("customerId") Long customerId) {
        return customerVerificationService.getByCustomerId(customerId);
    }

    @PutMapping("/{customerId}")
    public CustomerVerificationResponse upsert(
        Authentication authentication,
        @PathVariable("customerId") Long customerId,
        @Valid @RequestBody UpdateCustomerVerificationRequest request
    ) {
        AuthenticatedUser staff = extractUser(authentication);
        return customerVerificationService.upsert(customerId, staff.id(), request);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }
}
