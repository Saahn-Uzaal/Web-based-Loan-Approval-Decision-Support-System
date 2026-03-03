package com.loanapproval.dss.repayment.dto;

import java.util.List;

public record RepaymentHistoryResponse(
    Integer currentRating,
    List<RepaymentItemResponse> items
) {
}
