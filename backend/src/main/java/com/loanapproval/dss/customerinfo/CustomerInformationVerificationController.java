package com.loanapproval.dss.customerinfo;

import com.loanapproval.dss.customerinfo.dto.CustomerInformationVerificationResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/information-verification")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerInformationVerificationController {

    private final CustomerInformationVerificationService customerInformationVerificationService;

    public CustomerInformationVerificationController(
        CustomerInformationVerificationService customerInformationVerificationService
    ) {
        this.customerInformationVerificationService = customerInformationVerificationService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CustomerInformationVerificationResponse getCurrentStatus(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerInformationVerificationService.getCurrentStatus(user.id());
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }
}
