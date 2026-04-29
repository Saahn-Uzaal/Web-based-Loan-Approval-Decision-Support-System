package com.loanapproval.dss.repayment.dto;

import java.util.List;

public record RepaymentHistoryResponse(
    Integer currentRating,
    List<RepaymentItemResponse> items,
    List<PaymentConfirmationItemResponse> confirmationRequests,
    Integer page,
    Integer size,
    Long totalElements,
    Integer totalPages,
    Boolean last
) {
    /** Convenience constructor for the non-paged (legacy) endpoint. */
    public RepaymentHistoryResponse(Integer currentRating, List<RepaymentItemResponse> items) {
        this(currentRating, items, List.of(), null, null, null, null, null);
    }

    public RepaymentHistoryResponse(Integer currentRating, List<RepaymentItemResponse> items, List<PaymentConfirmationItemResponse> confirmationRequests) {
        this(currentRating, items, confirmationRequests, null, null, null, null, null);
    }

    public RepaymentHistoryResponse withConfirmationRequests(List<PaymentConfirmationItemResponse> confirmationRequests) {
        return new RepaymentHistoryResponse(
                currentRating,
                items,
                confirmationRequests,
                page,
                size,
                totalElements,
                totalPages,
                last);
    }
}
