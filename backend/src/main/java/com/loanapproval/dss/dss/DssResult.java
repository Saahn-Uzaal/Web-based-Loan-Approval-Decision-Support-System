package com.loanapproval.dss.dss;

public record DssResult(
    Integer creditScore,
    RiskRank riskRank,
    CustomerSegment customerSegment,
    DssRecommendation recommendation,
    String explanation
) {
}
