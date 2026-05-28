package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanDelinquencyRecord(
        Long id,
        Long loanRequestId,
        Long customerId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal amountDue,
        BigDecimal currentAmountDue,
        Integer daysPastDue,
        Integer highestMilestone,
        Integer totalRatingDelta,
        BigDecimal totalFeeAssessed,
        LoanDelinquencyStatus status,
        Instant openedAt,
        Instant lastAssessedAt,
        Instant curedAt,
        Instant createdAt,
        Instant updatedAt) {
}
