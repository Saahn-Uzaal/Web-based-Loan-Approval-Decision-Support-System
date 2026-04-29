package com.loanapproval.dss.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanContractScheduleTerms(
        LocalDate startDate,
        LocalDate firstPaymentDate,
        String monthlyPaymentDay,
        LocalDate finalPaymentDate,
        BigDecimal annualInterestRate,
        BigDecimal monthlyPayment) {
}
