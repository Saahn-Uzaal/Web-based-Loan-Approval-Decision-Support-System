package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditBureauLoanAccount(
    Long id,
    String identityNumber,
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
