package com.loanapproval.dss.creditcheck;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
public class CreditBureauRepository {

    private static final RowMapper<CreditBureauRecord> CREDIT_BUREAU_ROW_MAPPER = (rs, rowNum) -> new CreditBureauRecord(
        rs.getString("identity_number"),
        rs.getString("borrower_name"),
        CreditBureauStatus.valueOf(rs.getString("bureau_status")),
        (Integer) rs.getObject("credit_score"),
        (Integer) rs.getObject("active_loan_count"),
        (Integer) rs.getObject("days_past_due"),
        rs.getBoolean("manual_review_required"),
        rs.getBoolean("hard_reject"),
        rs.getString("risk_note"),
        rs.getBigDecimal("total_monthly_obligation"),
        rs.getBigDecimal("total_outstanding_balance"),
        rs.getBigDecimal("external_monthly_obligation"),
        rs.getBigDecimal("external_outstanding_balance"),
        (Integer) rs.getObject("reporting_institution_count"),
        rs.getBoolean("consent_granted"),
        toInstant(rs.getTimestamp("last_reported_at")),
        toInstant(rs.getTimestamp("updated_at"))
    );

    private static final RowMapper<CreditBureauLoanAccount> CREDIT_BUREAU_LOAN_ROW_MAPPER = (rs, rowNum) -> new CreditBureauLoanAccount(
        rs.getLong("id"),
        rs.getString("identity_number"),
        rs.getString("reporting_institution"),
        rs.getString("account_reference"),
        CreditLoanSourceType.valueOf(rs.getString("source_type")),
        rs.getString("loan_category"),
        CreditLoanAccountStatus.valueOf(rs.getString("account_status")),
        rs.getBigDecimal("original_amount"),
        rs.getBigDecimal("outstanding_balance"),
        rs.getBigDecimal("monthly_payment"),
        (Integer) rs.getObject("days_past_due"),
        rs.getString("note"),
        toInstant(rs.getTimestamp("reported_at")),
        toInstant(rs.getTimestamp("updated_at"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    public CreditBureauRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CreditBureauRecord> findByIdentityNumber(String identityNumber) {
        return jdbcTemplate.query(
            """
            SELECT
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note,
                total_monthly_obligation,
                total_outstanding_balance,
                external_monthly_obligation,
                external_outstanding_balance,
                reporting_institution_count,
                consent_granted,
                last_reported_at,
                updated_at
            FROM credit_bureau_records
            WHERE identity_number = :identityNumber
            """,
            new MapSqlParameterSource("identityNumber", identityNumber),
            CREDIT_BUREAU_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<CreditBureauLoanAccount> findLoanAccountsByIdentityNumber(String identityNumber) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                identity_number,
                reporting_institution,
                account_reference,
                source_type,
                loan_category,
                account_status,
                original_amount,
                outstanding_balance,
                monthly_payment,
                days_past_due,
                note,
                reported_at,
                updated_at
            FROM credit_bureau_loan_accounts
            WHERE identity_number = :identityNumber
            ORDER BY
                CASE account_status
                    WHEN 'BAD_DEBT' THEN 1
                    WHEN 'OVERDUE' THEN 2
                    WHEN 'CURRENT' THEN 3
                    ELSE 4
                END,
                reported_at DESC,
                id DESC
            """,
            new MapSqlParameterSource("identityNumber", identityNumber),
            CREDIT_BUREAU_LOAN_ROW_MAPPER
        );
    }

    public List<String> findIdentityNumbersBySourceType(CreditLoanSourceType sourceType) {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT identity_number
            FROM credit_bureau_loan_accounts
            WHERE source_type = :sourceType
            ORDER BY identity_number ASC
            """,
            new MapSqlParameterSource("sourceType", sourceType.name()),
            (rs, rowNum) -> rs.getString("identity_number")
        );
    }

    public long count(CreditBureauStatus status, String query) {
        QuerySpec querySpec = buildQuerySpec(status, query);
        Long count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM credit_bureau_records
            """ + querySpec.whereClause(),
            querySpec.params(),
            Long.class
        );
        return count != null ? count : 0L;
    }

    public List<CreditBureauRecord> findPaged(CreditBureauStatus status, String query, int offset, int limit) {
        QuerySpec querySpec = buildQuerySpec(status, query);
        MapSqlParameterSource params = copyParams(querySpec.params());
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        return jdbcTemplate.query(
            """
            SELECT
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note,
                total_monthly_obligation,
                total_outstanding_balance,
                external_monthly_obligation,
                external_outstanding_balance,
                reporting_institution_count,
                consent_granted,
                last_reported_at,
                updated_at
            FROM credit_bureau_records
            """ + querySpec.whereClause() + """
            ORDER BY
                CASE bureau_status
                    WHEN 'BAD_DEBT' THEN 1
                    WHEN 'WATCHLIST' THEN 2
                    WHEN 'FRAUD_SUSPECT' THEN 3
                    WHEN 'CLEAR' THEN 4
                    ELSE 5
                END,
                total_outstanding_balance DESC,
                updated_at DESC,
                identity_number ASC
            LIMIT :limit OFFSET :offset
            """,
            params,
            CREDIT_BUREAU_ROW_MAPPER
        );
    }

    public CreditBureauRecord upsert(CreditBureauRecord record) {
        jdbcTemplate.update(
            """
            INSERT INTO credit_bureau_records (
                identity_number,
                borrower_name,
                bureau_status,
                credit_score,
                active_loan_count,
                days_past_due,
                manual_review_required,
                hard_reject,
                risk_note,
                total_monthly_obligation,
                total_outstanding_balance,
                external_monthly_obligation,
                external_outstanding_balance,
                reporting_institution_count,
                consent_granted,
                last_reported_at
            ) VALUES (
                :identityNumber,
                :borrowerName,
                :bureauStatus,
                :creditScore,
                :activeLoanCount,
                :daysPastDue,
                :manualReviewRequired,
                :hardReject,
                :riskNote,
                :totalMonthlyObligation,
                :totalOutstandingBalance,
                :externalMonthlyObligation,
                :externalOutstandingBalance,
                :reportingInstitutionCount,
                :consentGranted,
                :lastReportedAt
            )
            ON DUPLICATE KEY UPDATE
                borrower_name = VALUES(borrower_name),
                bureau_status = VALUES(bureau_status),
                credit_score = VALUES(credit_score),
                active_loan_count = VALUES(active_loan_count),
                days_past_due = VALUES(days_past_due),
                manual_review_required = VALUES(manual_review_required),
                hard_reject = VALUES(hard_reject),
                risk_note = VALUES(risk_note),
                total_monthly_obligation = VALUES(total_monthly_obligation),
                total_outstanding_balance = VALUES(total_outstanding_balance),
                external_monthly_obligation = VALUES(external_monthly_obligation),
                external_outstanding_balance = VALUES(external_outstanding_balance),
                reporting_institution_count = VALUES(reporting_institution_count),
                consent_granted = VALUES(consent_granted),
                last_reported_at = VALUES(last_reported_at),
                updated_at = CURRENT_TIMESTAMP
            """,
            new MapSqlParameterSource()
                .addValue("identityNumber", record.identityNumber())
                .addValue("borrowerName", record.borrowerName())
                .addValue("bureauStatus", record.bureauStatus().name())
                .addValue("creditScore", valueOrZero(record.creditScore()))
                .addValue("activeLoanCount", valueOrZero(record.activeLoanCount()))
                .addValue("daysPastDue", valueOrZero(record.daysPastDue()))
                .addValue("manualReviewRequired", record.manualReviewRequired())
                .addValue("hardReject", record.hardReject())
                .addValue("riskNote", record.riskNote())
                .addValue("totalMonthlyObligation", valueOrZero(record.totalMonthlyObligation()))
                .addValue("totalOutstandingBalance", valueOrZero(record.totalOutstandingBalance()))
                .addValue("externalMonthlyObligation", valueOrZero(record.externalMonthlyObligation()))
                .addValue("externalOutstandingBalance", valueOrZero(record.externalOutstandingBalance()))
                .addValue("reportingInstitutionCount", valueOrZero(record.reportingInstitutionCount()))
                .addValue("consentGranted", record.consentGranted())
                .addValue("lastReportedAt", record.lastReportedAt() != null ? Timestamp.from(record.lastReportedAt()) : null)
        );
        return findByIdentityNumber(record.identityNumber()).orElse(record);
    }

    public void replaceLoanAccounts(String identityNumber, List<CreditBureauLoanAccount> accounts) {
        jdbcTemplate.update(
            "DELETE FROM credit_bureau_loan_accounts WHERE identity_number = :identityNumber",
            new MapSqlParameterSource("identityNumber", identityNumber)
        );
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<MapSqlParameterSource> batch = new ArrayList<>();
        for (CreditBureauLoanAccount account : accounts) {
            batch.add(new MapSqlParameterSource()
                .addValue("identityNumber", identityNumber)
                .addValue("reportingInstitution", account.reportingInstitution())
                .addValue("accountReference", account.accountReference())
                .addValue("sourceType", account.sourceType().name())
                .addValue("loanCategory", account.loanCategory())
                .addValue("accountStatus", account.accountStatus().name())
                .addValue("originalAmount", valueOrZero(account.originalAmount()))
                .addValue("outstandingBalance", valueOrZero(account.outstandingBalance()))
                .addValue("monthlyPayment", valueOrZero(account.monthlyPayment()))
                .addValue("daysPastDue", valueOrZero(account.daysPastDue()))
                .addValue("note", account.note())
                .addValue("reportedAt", account.reportedAt() != null ? Timestamp.from(account.reportedAt()) : Timestamp.from(Instant.now())));
        }

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO credit_bureau_loan_accounts (
                identity_number,
                reporting_institution,
                account_reference,
                source_type,
                loan_category,
                account_status,
                original_amount,
                outstanding_balance,
                monthly_payment,
                days_past_due,
                note,
                reported_at
            ) VALUES (
                :identityNumber,
                :reportingInstitution,
                :accountReference,
                :sourceType,
                :loanCategory,
                :accountStatus,
                :originalAmount,
                :outstandingBalance,
                :monthlyPayment,
                :daysPastDue,
                :note,
                :reportedAt
            )
            """,
            batch.toArray(SqlParameterSource[]::new)
        );
    }

    public int deleteByIdentityNumber(String identityNumber) {
        return jdbcTemplate.update(
            "DELETE FROM credit_bureau_records WHERE identity_number = :identityNumber",
            new MapSqlParameterSource("identityNumber", identityNumber)
        );
    }

    public CreditBureauRegistrySummarySnapshot summarize() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) AS borrower_count,
                COALESCE(SUM(CASE WHEN bureau_status = 'BAD_DEBT' THEN 1 ELSE 0 END), 0) AS bad_debt_count,
                COALESCE(SUM(CASE WHEN bureau_status = 'WATCHLIST' THEN 1 ELSE 0 END), 0) AS watchlist_count,
                COALESCE(SUM(active_loan_count), 0) AS total_active_loan_count,
                COALESCE(SUM(total_monthly_obligation), 0) AS total_monthly_obligation,
                COALESCE(SUM(total_outstanding_balance), 0) AS total_outstanding_balance
            FROM credit_bureau_records
            """,
            new MapSqlParameterSource(),
            (rs, rowNum) -> new CreditBureauRegistrySummarySnapshot(
                rs.getLong("borrower_count"),
                rs.getLong("bad_debt_count"),
                rs.getLong("watchlist_count"),
                rs.getLong("total_active_loan_count"),
                rs.getBigDecimal("total_monthly_obligation"),
                rs.getBigDecimal("total_outstanding_balance")
            )
        );
    }

    private QuerySpec buildQuerySpec(CreditBureauStatus status, String query) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (status != null) {
            conditions.add("bureau_status = :status");
            params.addValue("status", status.name());
        }
        if (query != null && !query.isBlank()) {
            conditions.add("""
                (
                    identity_number LIKE :query
                    OR borrower_name LIKE :query
                    OR risk_note LIKE :query
                    OR EXISTS (
                        SELECT 1
                        FROM credit_bureau_loan_accounts cla
                        WHERE cla.identity_number = credit_bureau_records.identity_number
                          AND (
                              cla.reporting_institution LIKE :query
                              OR cla.account_reference LIKE :query
                              OR cla.loan_category LIKE :query
                              OR cla.note LIKE :query
                          )
                    )
                )
                """);
            params.addValue("query", "%" + query.trim() + "%");
        }

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions) + " ";
        return new QuerySpec(whereClause, params);
    }

    private MapSqlParameterSource copyParams(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        String[] paramNames = source.getParameterNames();
        if (paramNames == null) {
            return copy;
        }
        for (String paramName : paramNames) {
            copy.addValue(paramName, source.getValue(paramName));
        }
        return copy;
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private record QuerySpec(String whereClause, MapSqlParameterSource params) {
    }
}
