package com.loanapproval.dss.risk;

import java.time.Instant;

public record RiskAssessment(
    Long loanRequestId,
    int creditRiskScore,
    int fraudRiskScore,
    int operationalRiskScore,
    RiskLevel overallRiskLevel,
    String riskReasons,
    Instant createdAt
) {
}
