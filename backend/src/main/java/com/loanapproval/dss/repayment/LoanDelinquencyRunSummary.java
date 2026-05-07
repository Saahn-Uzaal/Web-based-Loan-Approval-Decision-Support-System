package com.loanapproval.dss.repayment;

public record LoanDelinquencyRunSummary(
        int scannedLoans,
        int openedOrUpdated,
        int cured,
        int ratingAdjustments) {
}
