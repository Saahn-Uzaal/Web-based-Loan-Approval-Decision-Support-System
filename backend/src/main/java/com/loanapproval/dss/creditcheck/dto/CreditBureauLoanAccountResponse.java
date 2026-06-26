package com.loanapproval.dss.creditcheck.dto;

import com.loanapproval.dss.creditcheck.CreditLoanAccountStatus;
import com.loanapproval.dss.creditcheck.CreditLoanSourceType;
import java.math.BigDecimal;
import java.time.Instant;

public record CreditBureauLoanAccountResponse(
    Long id,
    String reportingInstitution,
    String accountReference,
    CreditLoanSourceType sourceType,
    String loanCategory,
    CreditLoanAccountStatus accountStatus,
    BigDecimal originalAmount,
    BigDecimal outstandingBalance,
    BigDecimal monthlyPayment,
    Integer daysPastDue,
    String note,
    Instant reportedAt,
    Instant updatedAt
) {
}
