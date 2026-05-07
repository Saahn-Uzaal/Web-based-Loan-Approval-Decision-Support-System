package com.loanapproval.dss.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreditPolicyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CreditPolicyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<CreditPolicyDefinition> findActivePolicy() {
        return jdbcTemplate.query(
                """
                SELECT version, policy_payload
                FROM credit_policies
                WHERE is_active = TRUE
                ORDER BY updated_at DESC, created_at DESC
                LIMIT 1
                """,
                this::mapPolicy).stream().findFirst();
    }

    private CreditPolicyDefinition mapPolicy(ResultSet rs, int rowNum) throws SQLException {
        String payload = rs.getString("policy_payload");
        try {
            CreditPolicyDefinition definition = objectMapper.readValue(payload, CreditPolicyDefinition.class);
            if (definition.version() != null) {
                return definition;
            }
            return new CreditPolicyDefinition(
                    rs.getString("version"),
                    definition.unsecuredAnnualRate(),
                    definition.securedAnnualRate(),
                    definition.unsecuredIncomeMultiple(),
                    definition.securedVehicleLtv(),
                    definition.maxDsr(),
                    definition.unsecuredProductCap(),
                    definition.securedProductCap(),
                    definition.riskAdjustmentA(),
                    definition.riskAdjustmentB(),
                    definition.riskAdjustmentC(),
                    definition.riskAdjustmentD(),
                    definition.dssWeightDti(),
                    definition.dssWeightIncome(),
                    definition.dssWeightCreditHistory(),
                    definition.dssWeightBurden(),
                    definition.dssWeightEmployment(),
                    definition.dssWeightAge(),
                    definition.dssWeightCollateral(),
                    definition.dssWeightPurpose(),
                    definition.dssWeightVerification(),
                    definition.dssScoreMin(),
                    definition.dssScoreMax(),
                    definition.dssScoreMultiplier(),
                    definition.dssRankAThreshold(),
                    definition.dssRankBThreshold(),
                    definition.dssRankCThreshold(),
                    definition.dtiLowThreshold(),
                    definition.dtiHighDowngradeThreshold(),
                    definition.dtiModerateDowngradeThreshold(),
                    definition.dtiExtremeThreshold(),
                    definition.dtiRejectThreshold());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to parse credit policy payload for version " + rs.getString("version"), ex);
        }
    }
}
