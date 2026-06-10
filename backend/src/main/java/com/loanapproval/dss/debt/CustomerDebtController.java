package com.loanapproval.dss.debt;

import com.loanapproval.dss.debt.dto.CreateCustomerDebtRequest;
import com.loanapproval.dss.debt.dto.CustomerDebtMetricsResponse;
import com.loanapproval.dss.debt.dto.CustomerDebtResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/debts")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerDebtController {

    private final CustomerDebtService customerDebtService;

    public CustomerDebtController(CustomerDebtService customerDebtService) {
        this.customerDebtService = customerDebtService;
    }

    @GetMapping
    public List<CustomerDebtResponse> listMyDebts(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerDebtService.listMine(user.id());
    }

    @GetMapping("/metrics")
    public CustomerDebtMetricsResponse getMyDebtMetrics(Authentication authentication) {
        AuthenticatedUser user = extractUser(authentication);
        return customerDebtService.getMetrics(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDebtResponse createDebt(
        Authentication authentication,
        @Valid @RequestBody CreateCustomerDebtRequest request
    ) {
        AuthenticatedUser user = extractUser(authentication);
        return customerDebtService.create(user.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDebt(Authentication authentication, @PathVariable("id") Long id) {
        AuthenticatedUser user = extractUser(authentication);
        customerDebtService.deleteOwned(user.id(), id);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }
}
