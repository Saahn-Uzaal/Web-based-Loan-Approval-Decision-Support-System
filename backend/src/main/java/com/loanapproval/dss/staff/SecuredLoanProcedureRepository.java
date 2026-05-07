package com.loanapproval.dss.staff;

import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.staff.dto.StaffRequestDetailResponse;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureRequest;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureResponse;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecuredLoanProcedureRepository {

    private final JdbcTemplate jdbcTemplate;

    public SecuredLoanProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StaffSecuredProcedureSummaryResponse> findSecuredProcedureQueue() {
        return jdbcTemplate.query(
                """
                        SELECT
                            lr.id AS loan_request_id,
                            u.email AS customer_email,
                            cp.full_name AS customer_name,
                            lr.assigned_staff_user_id,
                            assigned_staff.email AS assigned_staff_email,
                            lr.assigned_at,
                            lr.amount,
                            lr.status AS loan_status,
                            la.scheduled_at AS appointment_scheduled_at,
                            la.location AS appointment_location,
                            COALESCE(sp.status, 'DRAFT') AS procedure_status,
                            sp.updated_at AS procedure_updated_at
                        FROM loan_requests lr
                        INNER JOIN users u ON u.id = lr.customer_id
                        LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                        LEFT JOIN users assigned_staff ON assigned_staff.id = lr.assigned_staff_user_id
                        LEFT JOIN loan_appointments la ON la.id = (
                            SELECT la_latest.id
                            FROM loan_appointments la_latest
                            WHERE la_latest.loan_request_id = lr.id
                            ORDER BY la_latest.created_at DESC, la_latest.id DESC
                            LIMIT 1
                        )
                        LEFT JOIN secured_loan_procedures sp ON sp.loan_request_id = lr.id
                        WHERE lr.loan_type = 'SECURED'
                          AND lr.status = 'APPOINTMENT_SCHEDULED'
                          AND COALESCE(sp.status, 'DRAFT') <> 'COMPLETED'
                        ORDER BY
                            CASE COALESCE(sp.status, 'DRAFT')
                                WHEN 'IN_PROGRESS' THEN 1
                                ELSE 0
                            END,
                            COALESCE(la.scheduled_at, lr.created_at) ASC,
                            lr.id DESC
                        """,
                (rs, rowNum) -> new StaffSecuredProcedureSummaryResponse(
                        rs.getLong("loan_request_id"),
                        rs.getString("customer_email"),
                        rs.getString("customer_name"),
                        (Long) rs.getObject("assigned_staff_user_id"),
                        rs.getString("assigned_staff_email"),
                        toInstant(rs.getTimestamp("assigned_at")),
                        rs.getBigDecimal("amount"),
                        LoanStatus.valueOf(rs.getString("loan_status")),
                        toInstant(rs.getTimestamp("appointment_scheduled_at")),
                        rs.getString("appointment_location"),
                        SecuredProcedureStatus.valueOf(rs.getString("procedure_status")),
                        toInstant(rs.getTimestamp("procedure_updated_at"))));
    }

    public Optional<StaffSecuredProcedureResponse> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            lr.id AS loan_request_id,
                            lr.customer_id,
                            u.email AS customer_email,
                            cp.full_name AS customer_name,
                            cp.phone AS customer_phone,
                            cp.date_of_birth AS customer_date_of_birth,
                            cp.employment_status AS customer_employment_status,
                            lr.amount,
                            lr.term_months,
                            lr.approved_amount,
                            lr.approved_term_months,
                            lr.approved_annual_rate,
                            lr.approved_monthly_payment,
                            lr.collateral_value AS declared_collateral_value,
                            lr.status AS loan_status,
                            lr.assigned_staff_user_id,
                            assigned_staff.email AS assigned_staff_email,
                            lr.assigned_at,
                            la.id AS appointment_id,
                            la.staff_id AS appointment_staff_id,
                            appointment_staff.email AS appointment_staff_email,
                            la.scheduled_at AS appointment_scheduled_at,
                            la.location AS appointment_location,
                            la.note AS appointment_note,
                            la.status AS appointment_status,
                            la.created_at AS appointment_created_at,
                            sp.id AS procedure_id,
                            sp.staff_user_id,
                            procedure_staff.email AS procedure_staff_email,
                            sp.mortgagee_name,
                            sp.mortgagee_address,
                            sp.mortgagee_business_code,
                            sp.mortgagee_phone,
                            sp.contract_number,
                            sp.contract_signed_date,
                            sp.nationality,
                            sp.identity_document_number,
                            sp.permanent_address,
                            sp.current_address,
                            sp.occupation,
                            sp.job_title,
                            sp.asset_type,
                            sp.asset_manufacturer,
                            sp.engine_number,
                            sp.frame_number,
                            sp.collateral_owner_name,
                            sp.collateral_identifier,
                            sp.registration_number,
                            sp.sale_price,
                            sp.down_payment,
                            sp.appraisal_value,
                            sp.monthly_interest_rate,
                            sp.monthly_payment_amount,
                            sp.first_payment_date,
                            sp.monthly_payment_day,
                            sp.final_payment_date,
                            sp.appraisal_report_code,
                            sp.insurance_policy_number,
                            sp.original_certificate_received,
                            sp.certified_copy_delivered,
                            sp.collateral_registration_completed,
                            sp.dispute_checked,
                            sp.seizure_notice_acknowledged,
                            sp.documents_checked,
                            sp.asset_inspected,
                            sp.valuation_approved,
                            sp.contract_signed,
                            sp.collateral_handover_confirmed,
                            sp.disbursement_ready,
                            COALESCE(sp.status, 'DRAFT') AS procedure_status,
                            sp.note,
                            sp.completed_at,
                            sp.updated_at AS procedure_updated_at
                        FROM loan_requests lr
                        INNER JOIN users u ON u.id = lr.customer_id
                        LEFT JOIN users assigned_staff ON assigned_staff.id = lr.assigned_staff_user_id
                        LEFT JOIN customer_profiles cp ON cp.user_id = lr.customer_id
                        LEFT JOIN loan_appointments la ON la.id = (
                            SELECT la_latest.id
                            FROM loan_appointments la_latest
                            WHERE la_latest.loan_request_id = lr.id
                            ORDER BY la_latest.created_at DESC, la_latest.id DESC
                            LIMIT 1
                        )
                        LEFT JOIN users appointment_staff ON appointment_staff.id = la.staff_id
                        LEFT JOIN secured_loan_procedures sp ON sp.loan_request_id = lr.id
                        LEFT JOIN users procedure_staff ON procedure_staff.id = sp.staff_user_id
                        WHERE lr.id = ?
                          AND lr.loan_type = 'SECURED'
                        """,
                (rs, rowNum) -> mapDetail(rs),
                loanRequestId).stream().findFirst();
    }

    public void upsert(Long loanRequestId, Long staffUserId, StaffSecuredProcedureRequest request) {
        SecuredProcedureStatus status = request.status() != null
                ? request.status()
                : SecuredProcedureStatus.DRAFT;
        jdbcTemplate.update(
                """
                        INSERT INTO secured_loan_procedures (
                            loan_request_id,
                            staff_user_id,
                            mortgagee_name,
                            mortgagee_address,
                            mortgagee_business_code,
                            mortgagee_phone,
                            contract_number,
                            contract_signed_date,
                            nationality,
                            identity_document_number,
                            permanent_address,
                            current_address,
                            occupation,
                            job_title,
                            asset_type,
                            asset_manufacturer,
                            engine_number,
                            frame_number,
                            collateral_owner_name,
                            collateral_identifier,
                            registration_number,
                            sale_price,
                            down_payment,
                            appraisal_value,
                            monthly_interest_rate,
                            monthly_payment_amount,
                            first_payment_date,
                            monthly_payment_day,
                            final_payment_date,
                            appraisal_report_code,
                            insurance_policy_number,
                            original_certificate_received,
                            certified_copy_delivered,
                            collateral_registration_completed,
                            dispute_checked,
                            seizure_notice_acknowledged,
                            documents_checked,
                            asset_inspected,
                            valuation_approved,
                            contract_signed,
                            collateral_handover_confirmed,
                            disbursement_ready,
                            status,
                            note,
                            completed_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END
                        )
                        ON DUPLICATE KEY UPDATE
                            staff_user_id = VALUES(staff_user_id),
                            mortgagee_name = VALUES(mortgagee_name),
                            mortgagee_address = VALUES(mortgagee_address),
                            mortgagee_business_code = VALUES(mortgagee_business_code),
                            mortgagee_phone = VALUES(mortgagee_phone),
                            contract_number = VALUES(contract_number),
                            contract_signed_date = VALUES(contract_signed_date),
                            nationality = VALUES(nationality),
                            identity_document_number = VALUES(identity_document_number),
                            permanent_address = VALUES(permanent_address),
                            current_address = VALUES(current_address),
                            occupation = VALUES(occupation),
                            job_title = VALUES(job_title),
                            asset_type = VALUES(asset_type),
                            asset_manufacturer = VALUES(asset_manufacturer),
                            engine_number = VALUES(engine_number),
                            frame_number = VALUES(frame_number),
                            collateral_owner_name = VALUES(collateral_owner_name),
                            collateral_identifier = VALUES(collateral_identifier),
                            registration_number = VALUES(registration_number),
                            sale_price = VALUES(sale_price),
                            down_payment = VALUES(down_payment),
                            appraisal_value = VALUES(appraisal_value),
                            monthly_interest_rate = VALUES(monthly_interest_rate),
                            monthly_payment_amount = VALUES(monthly_payment_amount),
                            first_payment_date = VALUES(first_payment_date),
                            monthly_payment_day = VALUES(monthly_payment_day),
                            final_payment_date = VALUES(final_payment_date),
                            appraisal_report_code = VALUES(appraisal_report_code),
                            insurance_policy_number = VALUES(insurance_policy_number),
                            original_certificate_received = VALUES(original_certificate_received),
                            certified_copy_delivered = VALUES(certified_copy_delivered),
                            collateral_registration_completed = VALUES(collateral_registration_completed),
                            dispute_checked = VALUES(dispute_checked),
                            seizure_notice_acknowledged = VALUES(seizure_notice_acknowledged),
                            documents_checked = VALUES(documents_checked),
                            asset_inspected = VALUES(asset_inspected),
                            valuation_approved = VALUES(valuation_approved),
                            contract_signed = VALUES(contract_signed),
                            collateral_handover_confirmed = VALUES(collateral_handover_confirmed),
                            disbursement_ready = VALUES(disbursement_ready),
                            status = VALUES(status),
                            note = VALUES(note),
                            completed_at = CASE
                                WHEN VALUES(status) = 'COMPLETED' THEN COALESCE(completed_at, CURRENT_TIMESTAMP)
                                ELSE NULL
                            END,
                            updated_at = CURRENT_TIMESTAMP
                """,
                loanRequestId,
                staffUserId,
                normalize(request.mortgageeName()),
                normalize(request.mortgageeAddress()),
                normalize(request.mortgageeBusinessCode()),
                normalize(request.mortgageePhone()),
                normalize(request.contractNumber()),
                request.contractSignedDate(),
                normalize(request.nationality()),
                normalize(request.identityDocumentNumber()),
                normalize(request.permanentAddress()),
                normalize(request.currentAddress()),
                normalize(request.occupation()),
                normalize(request.jobTitle()),
                normalize(request.assetType()),
                normalize(request.assetManufacturer()),
                normalize(request.engineNumber()),
                normalize(request.frameNumber()),
                normalize(request.collateralOwnerName()),
                normalize(request.collateralIdentifier()),
                normalize(request.registrationNumber()),
                request.salePrice(),
                request.downPayment(),
                request.appraisalValue(),
                request.monthlyInterestRate(),
                request.monthlyPaymentAmount(),
                request.firstPaymentDate(),
                normalize(request.monthlyPaymentDay()),
                request.finalPaymentDate(),
                normalize(request.appraisalReportCode()),
                normalize(request.insurancePolicyNumber()),
                bool(request.originalCertificateReceived()),
                bool(request.certifiedCopyDelivered()),
                bool(request.collateralRegistrationCompleted()),
                bool(request.disputeChecked()),
                bool(request.seizureNoticeAcknowledged()),
                bool(request.documentsChecked()),
                bool(request.assetInspected()),
                bool(request.valuationApproved()),
                bool(request.contractSigned()),
                bool(request.collateralHandoverConfirmed()),
                bool(request.disbursementReady()),
                status.name(),
                normalize(request.note()),
                status.name());
    }

    public int markLatestAppointmentCompleted(Long loanRequestId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_appointments
                        SET status = 'COMPLETED',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status NOT IN ('CANCELLED', 'NO_SHOW')
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                loanRequestId);
    }

    public int rescheduleLatestAppointment(
            Long loanRequestId,
            Long staffUserId,
            Instant scheduledAt,
            String location,
            String note) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_appointments
                        SET staff_id = ?,
                            scheduled_at = ?,
                            location = ?,
                            note = ?,
                            status = 'SCHEDULED',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status <> 'COMPLETED'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                staffUserId,
                Timestamp.from(scheduledAt),
                normalize(location),
                normalize(note),
                loanRequestId);
    }

    public int cancelLatestAppointment(Long loanRequestId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_appointments
                        SET status = 'CANCELLED',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status = 'SCHEDULED'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                loanRequestId);
    }

    public int markLatestAppointmentNoShow(Long loanRequestId) {
        return jdbcTemplate.update(
                """
                        UPDATE loan_appointments
                        SET status = 'NO_SHOW',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE loan_request_id = ?
                          AND status = 'SCHEDULED'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                loanRequestId);
    }

    private StaffSecuredProcedureResponse mapDetail(ResultSet rs) throws SQLException {
        StaffRequestDetailResponse.AppointmentSummary appointment = null;
        if (rs.getObject("appointment_id") != null) {
            appointment = new StaffRequestDetailResponse.AppointmentSummary(
                    rs.getLong("appointment_id"),
                    rs.getLong("appointment_staff_id"),
                    rs.getString("appointment_staff_email"),
                    toInstant(rs.getTimestamp("appointment_scheduled_at")),
                    rs.getString("appointment_location"),
                    rs.getString("appointment_note"),
                    rs.getString("appointment_status"),
                    toInstant(rs.getTimestamp("appointment_created_at")));
        }

        StaffRequestDetailResponse.AssignmentSummary assignment = null;
        if (rs.getObject("assigned_staff_user_id") != null) {
            assignment = new StaffRequestDetailResponse.AssignmentSummary(
                    rs.getLong("assigned_staff_user_id"),
                    rs.getString("assigned_staff_email"),
                    toInstant(rs.getTimestamp("assigned_at")));
        }

        return new StaffSecuredProcedureResponse(
                rs.getLong("loan_request_id"),
                rs.getLong("customer_id"),
                rs.getString("customer_email"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                toLocalDate(rs.getDate("customer_date_of_birth")),
                rs.getString("customer_employment_status"),
                rs.getBigDecimal("amount"),
                rs.getInt("term_months"),
                rs.getBigDecimal("approved_amount"),
                (Integer) rs.getObject("approved_term_months"),
                rs.getBigDecimal("approved_annual_rate"),
                rs.getBigDecimal("approved_monthly_payment"),
                rs.getBigDecimal("declared_collateral_value"),
                LoanStatus.valueOf(rs.getString("loan_status")),
                assignment,
                appointment,
                (Long) rs.getObject("procedure_id"),
                (Long) rs.getObject("staff_user_id"),
                rs.getString("procedure_staff_email"),
                rs.getString("mortgagee_name"),
                rs.getString("mortgagee_address"),
                rs.getString("mortgagee_business_code"),
                rs.getString("mortgagee_phone"),
                rs.getString("contract_number"),
                toLocalDate(rs.getDate("contract_signed_date")),
                rs.getString("nationality"),
                rs.getString("identity_document_number"),
                rs.getString("permanent_address"),
                rs.getString("current_address"),
                rs.getString("occupation"),
                rs.getString("job_title"),
                rs.getString("asset_type"),
                rs.getString("asset_manufacturer"),
                rs.getString("engine_number"),
                rs.getString("frame_number"),
                rs.getString("collateral_owner_name"),
                rs.getString("collateral_identifier"),
                rs.getString("registration_number"),
                rs.getBigDecimal("sale_price"),
                rs.getBigDecimal("down_payment"),
                rs.getBigDecimal("appraisal_value"),
                rs.getBigDecimal("monthly_interest_rate"),
                rs.getBigDecimal("monthly_payment_amount"),
                toLocalDate(rs.getDate("first_payment_date")),
                rs.getString("monthly_payment_day"),
                toLocalDate(rs.getDate("final_payment_date")),
                rs.getString("appraisal_report_code"),
                rs.getString("insurance_policy_number"),
                rs.getBoolean("original_certificate_received"),
                rs.getBoolean("certified_copy_delivered"),
                rs.getBoolean("collateral_registration_completed"),
                rs.getBoolean("dispute_checked"),
                rs.getBoolean("seizure_notice_acknowledged"),
                rs.getBoolean("documents_checked"),
                rs.getBoolean("asset_inspected"),
                rs.getBoolean("valuation_approved"),
                rs.getBoolean("contract_signed"),
                rs.getBoolean("collateral_handover_confirmed"),
                rs.getBoolean("disbursement_ready"),
                SecuredProcedureStatus.valueOf(rs.getString("procedure_status")),
                rs.getString("note"),
                toInstant(rs.getTimestamp("completed_at")),
                toInstant(rs.getTimestamp("procedure_updated_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private static java.time.LocalDate toLocalDate(java.sql.Date value) {
        return value != null ? value.toLocalDate() : null;
    }

    private static boolean bool(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
