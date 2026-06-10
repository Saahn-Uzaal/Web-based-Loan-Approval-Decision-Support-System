package com.loanapproval.dss.loan;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class LoanApplicationPolicy {

    public static final Set<LoanStatus> BLOCKING_APPLICATION_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            LoanStatus.DRAFT,
            LoanStatus.PENDING,
            LoanStatus.NEEDS_MORE_INFO,
            LoanStatus.APPOINTMENT_SCHEDULED,
            LoanStatus.APPROVED,
            LoanStatus.CONTRACTED,
            LoanStatus.ACTIVE,
            LoanStatus.OVERDUE));

    public static final Set<LoanStatus> CUSTOMER_WITHDRAWABLE_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            LoanStatus.DRAFT,
            LoanStatus.PENDING,
            LoanStatus.NEEDS_MORE_INFO));

    public static final int MAX_ADDITIONAL_INFO_REQUESTS = 3;
    public static final int DEFAULT_ADDITIONAL_INFO_DEADLINE_DAYS = 3;

    private LoanApplicationPolicy() {
    }

    public static boolean blocksNewApplication(LoanStatus status) {
        return status != null && BLOCKING_APPLICATION_STATUSES.contains(status);
    }

    public static boolean canCustomerWithdraw(LoanStatus status) {
        return status != null && CUSTOMER_WITHDRAWABLE_STATUSES.contains(status);
    }
}
