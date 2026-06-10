package com.loanapproval.dss.loan.dto;

import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanAppointmentSummary;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LoanDetailResponse(
        Long id,
        Long customerId,
        LoanType loanType,
        BigDecimal amount,
        Integer termMonths,
        LoanPurpose purpose,
        CollateralType collateralType,
        BigDecimal collateralValue,
        LoanStatus status,
        String finalReason,
        BigDecimal eligibleLimit,
        BigDecimal approvedAmount,
        Integer approvedTermMonths,
        BigDecimal approvedAnnualRate,
        BigDecimal approvedMonthlyPayment,
        String decisionPolicyVersion,
        String intakeNote,
        String additionalInfoRequestNote,
        Instant additionalInfoLastRequestedAt,
        Instant additionalInfoRequestDeadline,
        Integer additionalInfoRequestCount,
        Instant reviewDeadlineAt,
        Instant contractAcceptanceDeadlineAt,
        LoanAppointmentSummary appointment,
        List<LoanDocumentResponse> documents,
        Instant createdAt,
        Instant updatedAt) {
}
