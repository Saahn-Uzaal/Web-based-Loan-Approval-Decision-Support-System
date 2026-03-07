package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.staff.StaffDecisionAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StaffRequestDetailResponse(
    Long id,
    LoanStatus status,
    BigDecimal amount,
    Integer termMonths,
    LoanPurpose purpose,
    String finalReason,
    Instant createdAt,
    Instant updatedAt,
    CustomerSummary customer,
    CustomerProfileSummary customerProfile,
    DssSummary dss,
    VerificationSummary verification,
    RiskAssessmentSummary risk,
    LoanContractSummary contract,
    List<DecisionAuditEntry> decisionAudits
) {
    public record CustomerSummary(
        Long id,
        String email
    ) {
    }

    public record CustomerProfileSummary(
        String fullName,
        String phone,
        BigDecimal monthlyIncome,
        BigDecimal debtToIncomeRatio,
        String employmentStatus
    ) {
    }

    public record DssSummary(
        Integer creditScore,
        RiskRank riskRank,
        CustomerSegment customerSegment,
        DssRecommendation recommendation,
        String explanation,
        Instant createdAt
    ) {
    }

    public record VerificationSummary(
        String documentStatus,
        String identityStatus,
        String incomeStatus,
        String kycStatus,
        String amlStatus,
        boolean fraudFlag,
        String note,
        Instant verifiedAt
    ) {
    }

    public record RiskAssessmentSummary(
        Integer creditRiskScore,
        Integer fraudRiskScore,
        Integer operationalRiskScore,
        String overallRiskLevel,
        String riskReasons,
        Instant createdAt
    ) {
    }

    public record LoanContractSummary(
        Long id,
        String status,
        BigDecimal annualInterestRate,
        BigDecimal monthlyPayment,
        BigDecimal totalInterest,
        Instant createdAt
    ) {
    }

    public record DecisionAuditEntry(
        Long id,
        Long staffUserId,
        String staffEmail,
        StaffDecisionAction action,
        String reason,
        Instant createdAt
    ) {
    }
}
