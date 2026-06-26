package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerCreditCheckRecord(
    Long id,
    Long customerId,
    String identityNumber,
    boolean matchedRecord,
    CreditBureauStatus bureauStatus,
    Integer creditScore,
    Integer activeLoanCount,
    Integer daysPastDue,
    BigDecimal totalMonthlyObligation,
    BigDecimal totalOutstandingBalance,
    BigDecimal externalMonthlyObligation,
    BigDecimal externalOutstandingBalance,
    Integer reportingInstitutionCount,
    boolean manualReviewRequired,
    boolean hardReject,
    String riskNote,
    String source,
    Instant checkedAt
) {
    public CustomerCreditCheckSummary toSummary() {
        return new CustomerCreditCheckSummary(
            identityNumber,
            matchedRecord,
            bureauStatus,
            creditScore,
            activeLoanCount,
            daysPastDue,
            totalMonthlyObligation,
            totalOutstandingBalance,
            externalMonthlyObligation,
            externalOutstandingBalance,
            reportingInstitutionCount,
            manualReviewRequired,
            hardReject,
            riskNote,
            source,
            checkedAt
        );
    }
}
