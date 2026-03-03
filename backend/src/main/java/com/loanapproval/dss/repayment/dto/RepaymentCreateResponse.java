package com.loanapproval.dss.repayment.dto;

public record RepaymentCreateResponse(
    RepaymentItemResponse repayment,
    Integer currentRating
) {
}
