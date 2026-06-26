package com.loanapproval.dss.creditcheck.dto;

import com.loanapproval.dss.creditcheck.CreditLoanAccountStatus;
import com.loanapproval.dss.creditcheck.CreditLoanSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpsertCreditBureauLoanAccountRequest(
    @NotBlank @Size(max = 150) String reportingInstitution,
    @Size(max = 100) String accountReference,
    @NotNull CreditLoanSourceType sourceType,
    @Size(max = 80) String loanCategory,
    @NotNull CreditLoanAccountStatus accountStatus,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal originalAmount,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal outstandingBalance,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal monthlyPayment,
    @NotNull @Min(0) Integer daysPastDue,
    @Size(max = 300) String note
) {
}
