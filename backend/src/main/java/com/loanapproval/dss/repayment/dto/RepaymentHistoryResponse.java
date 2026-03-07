package com.loanapproval.dss.repayment.dto;

import java.util.List;

public record RepaymentHistoryResponse(
    Integer currentRating,
    List<RepaymentItemResponse> items,
    Integer page,
    Integer size,
    Long totalElements,
    Integer totalPages,
    Boolean last
) {
    /** Convenience constructor for the non-paged (legacy) endpoint. */
    public RepaymentHistoryResponse(Integer currentRating, List<RepaymentItemResponse> items) {
        this(currentRating, items, null, null, null, null, null);
    }
}
