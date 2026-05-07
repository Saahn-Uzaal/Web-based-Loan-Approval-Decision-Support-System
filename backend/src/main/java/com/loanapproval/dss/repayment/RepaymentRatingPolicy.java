package com.loanapproval.dss.repayment;

import java.util.List;

final class RepaymentRatingPolicy {

    static final int ON_TIME_RATING_DELTA = 2;
    static final int EARLY_RATING_DELTA = 4;

    private static final List<MilestonePenalty> LATE_MILESTONE_PENALTIES = List.of(
            new MilestonePenalty(1, -6),
            new MilestonePenalty(7, -8),
            new MilestonePenalty(30, -12),
            new MilestonePenalty(60, -18),
            new MilestonePenalty(90, -30));

    private RepaymentRatingPolicy() {
    }

    static int rewardDelta(RepaymentStatus status) {
        return switch (status) {
            case EARLY -> EARLY_RATING_DELTA;
            case ON_TIME -> ON_TIME_RATING_DELTA;
            case LATE -> 0;
        };
    }

    static int firstLatePenalty() {
        return LATE_MILESTONE_PENALTIES.get(0).ratingDelta();
    }

    static int currentLateMilestone(int daysPastDue) {
        int milestone = 0;
        for (MilestonePenalty milestonePenalty : LATE_MILESTONE_PENALTIES) {
            if (daysPastDue >= milestonePenalty.daysPastDue()) {
                milestone = milestonePenalty.daysPastDue();
            }
        }
        return milestone;
    }

    static int latePenaltyDelta(int previousMilestone, int currentMilestone) {
        return LATE_MILESTONE_PENALTIES.stream()
                .filter(penalty -> penalty.daysPastDue() > previousMilestone)
                .filter(penalty -> penalty.daysPastDue() <= currentMilestone)
                .mapToInt(MilestonePenalty::ratingDelta)
                .sum();
    }

    private record MilestonePenalty(int daysPastDue, int ratingDelta) {
    }
}
