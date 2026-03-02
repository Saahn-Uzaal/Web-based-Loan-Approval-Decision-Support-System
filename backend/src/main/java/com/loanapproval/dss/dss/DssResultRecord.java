package com.loanapproval.dss.dss;

import java.time.Instant;

public record DssResultRecord(
    Long loanRequestId,
    Integer creditScore,
    RiskRank riskRank,
    CustomerSegment customerSegment,
    DssRecommendation recommendation,
    String explanation,
    Instant createdAt
) {
}
