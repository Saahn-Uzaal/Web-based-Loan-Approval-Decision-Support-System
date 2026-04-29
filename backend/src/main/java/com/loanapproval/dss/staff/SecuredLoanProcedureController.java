package com.loanapproval.dss.staff;

import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.shared.DemoTimeResolver;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureRequest;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureResponse;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureSummaryResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/secured-procedures")
@PreAuthorize("hasRole('STAFF')")
public class SecuredLoanProcedureController {

    private final SecuredLoanProcedureService securedLoanProcedureService;
    private final DemoTimeResolver demoTimeResolver;

    public SecuredLoanProcedureController(
            SecuredLoanProcedureService securedLoanProcedureService,
            DemoTimeResolver demoTimeResolver) {
        this.securedLoanProcedureService = securedLoanProcedureService;
        this.demoTimeResolver = demoTimeResolver;
    }

    @GetMapping
    public List<StaffSecuredProcedureSummaryResponse> listSecuredProcedures() {
        return securedLoanProcedureService.listSecuredProcedures();
    }

    @GetMapping("/{loanRequestId}")
    public StaffSecuredProcedureResponse getSecuredProcedure(@PathVariable Long loanRequestId) {
        return securedLoanProcedureService.getSecuredProcedure(loanRequestId);
    }

    @PutMapping("/{loanRequestId}")
    public StaffSecuredProcedureResponse saveSecuredProcedure(
            Authentication authentication,
            @PathVariable Long loanRequestId,
            @RequestHeader(value = "X-Demo-Now", required = false) String demoNowHeader,
            @Valid @RequestBody StaffSecuredProcedureRequest request) {
        AuthenticatedUser staff = extractUser(authentication);
        Instant effectiveNow = demoTimeResolver.resolveEffectiveNow(demoNowHeader);
        return securedLoanProcedureService.saveSecuredProcedure(staff.id(), loanRequestId, request, effectiveNow);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }
}
