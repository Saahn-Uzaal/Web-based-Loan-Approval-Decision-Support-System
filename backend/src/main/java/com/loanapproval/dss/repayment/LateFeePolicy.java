package com.loanapproval.dss.repayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class LateFeePolicy {

    private static final List<MilestoneFee> LATE_FEE_MILESTONES = List.of(
            new MilestoneFee(1, new BigDecimal("0.0100")),
            new MilestoneFee(7, new BigDecimal("0.0100")),
            new MilestoneFee(30, new BigDecimal("0.0200")),
            new MilestoneFee(60, new BigDecimal("0.0200")),
            new MilestoneFee(90, new BigDecimal("0.0400")));

    private LateFeePolicy() {
    }

    static BigDecimal lateFeeDelta(int previousMilestone, int currentMilestone, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return LATE_FEE_MILESTONES.stream()
                .filter(fee -> fee.daysPastDue() > previousMilestone)
                .filter(fee -> fee.daysPastDue() <= currentMilestone)
                .map(fee -> baseAmount.multiply(fee.rate()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private record MilestoneFee(int daysPastDue, BigDecimal rate) {
    }
}
