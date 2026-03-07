package com.loanapproval.dss.repayment;

import com.loanapproval.dss.repayment.dto.CreateRepaymentRequest;
import com.loanapproval.dss.repayment.dto.RepaymentCreateResponse;
import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRepaymentController {

    private final RepaymentService repaymentService;

    public CustomerRepaymentController(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @GetMapping
    public RepaymentHistoryResponse listMyRepayments(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return repaymentService.listMine(user.id());
    }

    @GetMapping("/paged")
    public RepaymentHistoryResponse listMyRepaymentsPaged(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return repaymentService.listMinePaged(user.id(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepaymentCreateResponse createRepayment(
        Authentication authentication,
        @Valid @RequestBody CreateRepaymentRequest request
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return repaymentService.create(user.id(), request);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }
}
