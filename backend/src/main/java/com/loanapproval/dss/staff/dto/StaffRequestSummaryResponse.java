package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffRequestSummaryResponse(
    Long id,
    Long customerId,
    String customerEmail,
    String customerName,
    BigDecimal amount,
    Integer termMonths,
    LoanPurpose purpose,
    LoanStatus status,
    RiskRank riskRank,
    DssRecommendation dssRecommendation,
    Instant createdAt
) {
}
