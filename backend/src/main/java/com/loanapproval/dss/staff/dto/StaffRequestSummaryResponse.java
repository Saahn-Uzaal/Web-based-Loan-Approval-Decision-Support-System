package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffRequestSummaryResponse(
        Long id,
        Long customerId,
        LoanType loanType,
        String customerEmail,
        String customerName,
        Long assignedStaffUserId,
        String assignedStaffEmail,
        Instant assignedAt,
        BigDecimal amount,
        Integer termMonths,
        LoanPurpose purpose,
        LoanStatus status,
        BigDecimal approvedAmount,
        BigDecimal approvedMonthlyPayment,
        RiskRank riskRank,
        DssRecommendation dssRecommendation,
        Instant createdAt) {
}
