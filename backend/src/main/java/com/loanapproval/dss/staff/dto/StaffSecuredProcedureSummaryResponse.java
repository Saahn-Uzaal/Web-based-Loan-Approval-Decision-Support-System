package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.staff.SecuredProcedureStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffSecuredProcedureSummaryResponse(
        Long loanRequestId,
        String customerEmail,
        String customerName,
        Long assignedStaffUserId,
        String assignedStaffEmail,
        Instant assignedAt,
        BigDecimal amount,
        LoanStatus loanStatus,
        Instant appointmentScheduledAt,
        String appointmentLocation,
        SecuredProcedureStatus procedureStatus,
        Instant updatedAt) {
}
