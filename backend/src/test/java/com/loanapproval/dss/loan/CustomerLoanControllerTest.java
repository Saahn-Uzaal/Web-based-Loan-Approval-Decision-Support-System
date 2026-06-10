package com.loanapproval.dss.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CustomerLoanControllerTest {

    @Test
    void shouldRejectJsonLoanSubmissionAtCreateEndpoint() {
        CustomerLoanController controller = new CustomerLoanController(
            mock(CustomerLoanService.class),
            new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator()
        );
        CreateLoanRequest request = new CreateLoanRequest(
            LoanType.UNSECURED,
            BigDecimal.valueOf(50_000_000),
            12,
            LoanPurpose.PERSONAL,
            null,
            null
        );

        assertThatThrownBy(() -> controller.createLoan(null, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).contains("multipart/form-data");
                assertThat(exception.getReason()).contains("/api/customer/loans/drafts");
            });
    }
}
