package com.loanapproval.dss.creditcheck.dto;

import com.loanapproval.dss.creditcheck.CreditBureauStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreditBureauRecordResponse(
    String identityNumber,
    String borrowerName,
    CreditBureauStatus bureauStatus,
    Integer creditScore,
    Integer activeLoanCount,
    Integer daysPastDue,
    boolean manualReviewRequired,
    boolean hardReject,
    String riskNote,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance,
    BigDecimal externalMonthlyObligation,
    BigDecimal externalOutstandingBalance,
    Integer reportingInstitutionCount,
    boolean consentGranted,
    Instant lastReportedAt,
    List<CreditBureauLoanAccountResponse> loanAccounts,
    Instant updatedAt
) {
}
