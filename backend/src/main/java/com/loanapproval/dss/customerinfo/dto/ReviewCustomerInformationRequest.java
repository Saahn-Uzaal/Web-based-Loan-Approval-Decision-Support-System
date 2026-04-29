package com.loanapproval.dss.customerinfo.dto;

import com.loanapproval.dss.customerinfo.CustomerInformationDecisionAction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReviewCustomerInformationRequest(
    @NotNull CustomerInformationDecisionAction action,
    @Size(max = 500) String reason,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal verifiedMonthlyIncome
) {
}
