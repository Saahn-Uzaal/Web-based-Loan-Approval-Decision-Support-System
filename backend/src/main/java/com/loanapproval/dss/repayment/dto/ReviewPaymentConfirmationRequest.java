package com.loanapproval.dss.repayment.dto;

import com.loanapproval.dss.repayment.PaymentConfirmationReviewAction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviewPaymentConfirmationRequest(
        @NotNull PaymentConfirmationReviewAction action,
        @DecimalMin(value = "1.00", inclusive = true) BigDecimal confirmedAmount,
        Instant confirmedPaidAt,
        @Size(max = 120) String bankTransactionCode,
        @Size(max = 500) String staffNote,
        @Size(max = 500) String rejectionReason) {
}
