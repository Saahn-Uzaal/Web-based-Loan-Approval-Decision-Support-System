package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.loan.dto.LoanDocumentResponse;
import com.loanapproval.dss.staff.StaffDecisionAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StaffRequestDetailResponse(
        Long id,
        LoanType loanType,
        LoanStatus status,
        BigDecimal amount,
        Integer termMonths,
        LoanPurpose purpose,
        CollateralType collateralType,
        String finalReason,
        BigDecimal eligibleLimit,
        BigDecimal approvedAmount,
        Integer approvedTermMonths,
        BigDecimal approvedAnnualRate,
        BigDecimal approvedMonthlyPayment,
        String decisionPolicyVersion,
        String intakeNote,
        Instant createdAt,
        Instant updatedAt,
        CustomerSummary customer,
        AssignmentSummary assignment,
        CustomerProfileSummary customerProfile,
        DssSummary dss,
        VerificationSummary verification,
        RiskAssessmentSummary risk,
        LoanContractSummary contract,
        AppointmentSummary appointment,
        List<LoanDocumentResponse> documents,
        List<DecisionAuditEntry> decisionAudits) {
    public record CustomerSummary(
            Long id,
            String email) {
    }

    public record AssignmentSummary(
            Long staffUserId,
            String staffEmail,
            Instant assignedAt) {
    }

    public record CustomerProfileSummary(
            String fullName,
            String phone,
            BigDecimal monthlyIncome,
            BigDecimal debtToIncomeRatio,
            String employmentStatus,
            java.time.LocalDate employmentStartDate,
            Integer creditHistoryScore,
            String payslipFileName,
            Long payslipFileSize,
            Instant payslipUploadedAt) {
    }

    public record DssSummary(
            Integer creditScore,
            RiskRank riskRank,
            CustomerSegment customerSegment,
            DssRecommendation recommendation,
            String explanation,
            Instant createdAt) {
    }

    public record VerificationSummary(
            String documentStatus,
            String identityStatus,
            String faceMatchStatus,
            String incomeStatus,
            String kycStatus,
            String amlStatus,
            boolean fraudFlag,
            String note,
            Instant verifiedAt) {
    }

    public record RiskAssessmentSummary(
            Integer creditRiskScore,
            Integer fraudRiskScore,
            Integer operationalRiskScore,
            String overallRiskLevel,
            String riskReasons,
            Instant createdAt) {
    }

    public record LoanContractSummary(
            Long id,
            String status,
            BigDecimal annualInterestRate,
            BigDecimal monthlyPayment,
            BigDecimal totalInterest,
            Instant createdAt) {
    }

    public record AppointmentSummary(
            Long id,
            Long staffUserId,
            String staffEmail,
            Instant scheduledAt,
            String location,
            String note,
            String status,
            Instant createdAt) {
    }

    public record DecisionAuditEntry(
            Long id,
            Long staffUserId,
            String staffEmail,
            StaffDecisionAction action,
            String reason,
            Instant createdAt) {
    }
}
