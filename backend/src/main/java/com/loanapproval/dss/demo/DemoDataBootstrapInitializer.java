package com.loanapproval.dss.demo;

import com.loanapproval.dss.auth.UserAccount;
import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.contract.LoanContractRepository;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.customerinfo.CustomerInformationVerificationRepository;
import com.loanapproval.dss.debt.CustomerDebtRepository;
import com.loanapproval.dss.dss.CustomerSegment;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.dss.RiskRank;
import com.loanapproval.dss.loan.CollateralType;
import com.loanapproval.dss.loan.LoanDocumentRepository;
import com.loanapproval.dss.loan.LoanDocumentStorageService;
import com.loanapproval.dss.loan.LoanDocumentType;
import com.loanapproval.dss.loan.LoanEligibilityService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.RepaymentRepository;
import com.loanapproval.dss.repayment.RepaymentStatus;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskAssessmentRepository;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.shared.Role;
import com.loanapproval.dss.staff.SecuredLoanProcedureRepository;
import com.loanapproval.dss.staff.SecuredProcedureStatus;
import com.loanapproval.dss.staff.StaffDecisionAction;
import com.loanapproval.dss.staff.StaffReviewRepository;
import com.loanapproval.dss.staff.dto.StaffSecuredProcedureRequest;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationRepository;
import com.loanapproval.dss.verification.VerificationStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataBootstrapInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataBootstrapInitializer.class);
    private static final String STAFF_EMAIL = "staff.demo@loan.local";
    private static final String CUSTOMER_DEMO_EMAIL = "customer.demo@loan.local";
    private static final String CUSTOMER_PENDING_EMAIL = "customer.pending@loan.local";
    private static final String CUSTOMER_FAILED_EMAIL = "customer.failed@loan.local";
    private static final List<String> SAMPLE_EMAILS = List.of(
        STAFF_EMAIL,
        CUSTOMER_DEMO_EMAIL,
        CUSTOMER_PENDING_EMAIL,
        CUSTOMER_FAILED_EMAIL
    );
    private static final byte[] SAMPLE_PDF_BYTES = (
        "%PDF-1.4\n" +
        "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n" +
        "2 0 obj<< /Type /Pages /Count 1 /Kids [3 0 R] >>endobj\n" +
        "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R >>endobj\n" +
        "4 0 obj<< /Length 61 >>stream\n" +
        "BT /F1 12 Tf 40 90 Td (Demo payslip for loan system testing) Tj ET\n" +
        "endstream endobj\n" +
        "trailer<< /Root 1 0 R >>\n" +
        "%%EOF\n"
    ).getBytes(StandardCharsets.UTF_8);
    private static final byte[] SAMPLE_PNG_BYTES = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9FpuwAAAAASUVORK5CYII="
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtRepository customerDebtRepository;
    private final CustomerInformationVerificationRepository customerInformationVerificationRepository;
    private final CustomerVerificationRepository customerVerificationRepository;
    private final LoanRepository loanRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final DssResultRepository dssResultRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final LoanEligibilityService loanEligibilityService;
    private final StaffReviewRepository staffReviewRepository;
    private final SecuredLoanProcedureRepository securedLoanProcedureRepository;
    private final LoanContractRepository loanContractRepository;
    private final RepaymentRepository repaymentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final String demoPassword;
    private final Path payslipStorageRoot;
    private final Path loanDocumentStorageRoot;
    private final Path paymentProofStorageRoot;

    public DemoDataBootstrapInitializer(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtRepository customerDebtRepository,
        CustomerInformationVerificationRepository customerInformationVerificationRepository,
        CustomerVerificationRepository customerVerificationRepository,
        LoanRepository loanRepository,
        LoanDocumentRepository loanDocumentRepository,
        DssResultRepository dssResultRepository,
        RiskAssessmentRepository riskAssessmentRepository,
        LoanEligibilityService loanEligibilityService,
        StaffReviewRepository staffReviewRepository,
        SecuredLoanProcedureRepository securedLoanProcedureRepository,
        LoanContractRepository loanContractRepository,
        RepaymentRepository repaymentRepository,
        JdbcTemplate jdbcTemplate,
        @Value("${app.bootstrap.demo.enabled:true}") boolean enabled,
        @Value("${app.bootstrap.demo.password:123456}") String demoPassword,
        @Value("${app.storage.customer-payslips-path:./storage/profile-documents}") String payslipStoragePath,
        @Value("${app.storage.loan-documents-path:./storage/loan-documents}") String loanDocumentStoragePath,
        @Value("${app.storage.payment-proofs-path:./storage/payment-proofs}") String paymentProofStoragePath
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtRepository = customerDebtRepository;
        this.customerInformationVerificationRepository = customerInformationVerificationRepository;
        this.customerVerificationRepository = customerVerificationRepository;
        this.loanRepository = loanRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.dssResultRepository = dssResultRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.loanEligibilityService = loanEligibilityService;
        this.staffReviewRepository = staffReviewRepository;
        this.securedLoanProcedureRepository = securedLoanProcedureRepository;
        this.loanContractRepository = loanContractRepository;
        this.repaymentRepository = repaymentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.demoPassword = demoPassword;
        this.payslipStorageRoot = Path.of(payslipStoragePath).toAbsolutePath().normalize();
        this.loanDocumentStorageRoot = Path.of(loanDocumentStoragePath).toAbsolutePath().normalize();
        this.paymentProofStorageRoot = Path.of(paymentProofStoragePath).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        cleanupExistingDemoData();

        UserAccount staff = createUser(STAFF_EMAIL, Role.STAFF);
        UserAccount demoCustomer = createUser(CUSTOMER_DEMO_EMAIL, Role.CUSTOMER);
        UserAccount pendingCustomer = createUser(CUSTOMER_PENDING_EMAIL, Role.CUSTOMER);
        UserAccount failedCustomer = createUser(CUSTOMER_FAILED_EMAIL, Role.CUSTOMER);

        seedApprovedCustomer(staff, demoCustomer);
        seedPendingCustomer(pendingCustomer);
        seedFailedCustomer(staff, failedCustomer);

        logger.info(
            "Bootstrapped demo data for testing: staff={}, customers={}, {}, {}",
            staff.email(),
            demoCustomer.email(),
            pendingCustomer.email(),
            failedCustomer.email()
        );
    }

    private void cleanupExistingDemoData() {
        List<Long> userIds = findSampleUserIds();
        if (userIds.isEmpty()) {
            return;
        }

        List<Long> loanIds = findSampleLoanIds(userIds);
        deleteStorageDirectories(payslipStorageRoot, userIds);
        deleteStorageDirectories(loanDocumentStorageRoot, loanIds);
        deleteStorageDirectories(paymentProofStorageRoot, userIds);

        deleteByIds("DELETE FROM compliance_audit_logs WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM decision_audits WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM payment_confirmation_requests WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM loan_repayments WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM loan_contracts WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM secured_loan_procedures WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM loan_appointments WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM risk_assessments WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM dss_results WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM loan_request_documents WHERE loan_request_id IN (%s)", loanIds);
        deleteByIds("DELETE FROM loan_requests WHERE id IN (%s)", loanIds);

        deleteByIds("DELETE FROM customer_verifications WHERE customer_id IN (%s)", userIds);
        deleteByIds("DELETE FROM customer_information_verifications WHERE customer_id IN (%s)", userIds);
        deleteByIds("DELETE FROM customer_debts WHERE customer_id IN (%s)", userIds);
        deleteByIds("DELETE FROM customer_profiles WHERE user_id IN (%s)", userIds);
        deleteByIds("DELETE FROM compliance_audit_logs WHERE customer_id IN (%s)", userIds);
        deleteByIds("DELETE FROM compliance_audit_logs WHERE actor_user_id IN (%s)", userIds);
        deleteByIds("DELETE FROM notifications WHERE recipient_user_id IN (%s)", userIds);
        deleteByIds("DELETE FROM notifications WHERE actor_user_id IN (%s)", userIds);
        deleteByIds("DELETE FROM users WHERE id IN (%s)", userIds);
    }

    private void seedApprovedCustomer(UserAccount staff, UserAccount customer) {
        StoredDemoFile payslip = writePayslip(customer.id(), "demo-payslip.pdf");
        upsertProfile(
            customer.id(),
            "Nguyễn Minh An",
            "0901234567",
            LocalDate.of(1994, 5, 12),
            money("32000000"),
            money("30000000"),
            money("13.50"),
            "Nhân viên kỹ thuật",
            LocalDate.of(2018, 3, 1),
            745,
            72,
            payslip
        );

        customerDebtRepository.create(customer.id(), "Vay mua xe", money("2600000"), money("68000000"), "Techcombank");
        customerDebtRepository.create(customer.id(), "Thẻ tín dụng", money("1450000"), money("18500000"), "VPBank");

        customerInformationVerificationRepository.upsertDecision(
            customer.id(),
            VerificationStatus.PASSED,
            null,
            staff.id(),
            Instant.now().minus(12, ChronoUnit.DAYS)
        );
        customerVerificationRepository.upsert(new CustomerVerification(
            customer.id(),
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            false,
            "Đồng bộ từ bộ dữ liệu demo",
            staff.id(),
            Instant.now().minus(12, ChronoUnit.DAYS),
            null,
            Instant.now().minus(12, ChronoUnit.DAYS)
        ));

        seedPendingUnsecuredLoan(customer);
        seedApprovedUnsecuredLoan(customer, staff);
        seedAppointmentScheduledSecuredLoan(customer, staff);
        seedActiveUnsecuredLoan(customer, staff);
        seedRejectedSecuredLoan(customer);
        seedContractedSecuredLoan(customer, staff);
    }

    private void seedPendingCustomer(UserAccount customer) {
        upsertProfile(
            customer.id(),
            "Trần Thu Hà",
            "0912345678",
            LocalDate.of(1997, 9, 21),
            money("22000000"),
            null,
            money("9.55"),
            "Chuyên viên vận hành",
            LocalDate.of(2022, 6, 10),
            690,
            55,
            null
        );
        customerDebtRepository.create(customer.id(), "Vay tiêu dùng", money("2100000"), money("32000000"), "MBBank");
        customerInformationVerificationRepository.markPending(customer.id());
        customerVerificationRepository.upsert(new CustomerVerification(
            customer.id(),
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            VerificationStatus.PENDING,
            false,
            "Chờ bộ phận thẩm định xác minh hồ sơ demo",
            null,
            null,
            null,
            Instant.now()
        ));
    }

    private void seedFailedCustomer(UserAccount staff, UserAccount customer) {
        StoredDemoFile payslip = writePayslip(customer.id(), "failed-customer-payslip.pdf");
        upsertProfile(
            customer.id(),
            "Lê Quốc Bảo",
            "0987654321",
            LocalDate.of(1991, 2, 8),
            money("18000000"),
            null,
            money("24.17"),
            "Nhân viên kinh doanh",
            LocalDate.of(2023, 1, 15),
            630,
            38,
            payslip
        );
        customerDebtRepository.create(customer.id(), "Trả góp điện máy", money("4350000"), money("26000000"), "Home Credit");
        customerInformationVerificationRepository.upsertDecision(
            customer.id(),
            VerificationStatus.FAILED,
            "Phiếu lương không khớp sao kê 3 tháng gần nhất",
            staff.id(),
            Instant.now().minus(5, ChronoUnit.DAYS)
        );
        customerVerificationRepository.upsert(new CustomerVerification(
            customer.id(),
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            VerificationStatus.FAILED,
            VerificationStatus.PASSED,
            VerificationStatus.PASSED,
            false,
            "Từ chối ở bước xác minh thông tin: Phiếu lương không khớp sao kê 3 tháng gần nhất",
            staff.id(),
            Instant.now().minus(5, ChronoUnit.DAYS),
            null,
            Instant.now().minus(5, ChronoUnit.DAYS)
        ));
    }

    private void seedPendingUnsecuredLoan(UserAccount customer) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.UNSECURED,
            money("120000000"),
            24,
            LoanPurpose.BUSINESS,
            null,
            money("145000000"),
            "Hồ sơ demo cho hàng đợi thẩm định"
        );
        createUnsecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            701,
            RiskRank.B,
            CustomerSegment.LOW_RISK_HIGH_VALUE,
            DssRecommendation.APPROVE_RECOMMENDED,
            "DTI ở ngưỡng an toàn, cần nhân viên xem xét mục đích vay và thông tin kinh doanh"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            43,
            25,
            34,
            RiskLevel.MEDIUM,
            "Hồ sơ có thể phê duyệt nhưng không đủ điều kiện tự động duyệt",
            Instant.now()
        ));
    }

    private void seedApprovedUnsecuredLoan(UserAccount customer, UserAccount staff) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.UNSECURED,
            money("60000000"),
            12,
            LoanPurpose.EDUCATION,
            null,
            money("98000000"),
            "Hồ sơ demo đã duyệt, chờ nhân viên hoàn tất hợp đồng"
        );
        createUnsecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            726,
            RiskRank.A,
            CustomerSegment.LOW_RISK_LOW_VALUE,
            DssRecommendation.APPROVE_RECOMMENDED,
            "Thu nhập và lịch sử tín dụng tốt, đề xuất duyệt nhanh"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            18,
            16,
            12,
            RiskLevel.LOW,
            "Rủi ro tổng thể thấp, phù hợp để tạo hợp đồng demo",
            Instant.now()
        ));

        BigDecimal annualRate = loanEligibilityService.defaultAnnualRate(LoanType.UNSECURED);
        BigDecimal approvedAmount = money("55000000");
        int approvedTerm = 12;
        BigDecimal monthlyPayment = loanEligibilityService.calculateMonthlyPayment(approvedAmount, approvedTerm, annualRate);
        String reason = "Đã duyệt hồ sơ";
        loanRepository.updateDecision(
            loan.id(),
            LoanStatus.APPROVED,
            reason,
            money("98000000"),
            approvedAmount,
            approvedTerm,
            annualRate,
            monthlyPayment,
            LoanEligibilityService.POLICY_VERSION
        );
        staffReviewRepository.insertDecisionAudit(loan.id(), staff.id(), StaffDecisionAction.APPROVE, reason);
    }

    private void seedAppointmentScheduledSecuredLoan(UserAccount customer, UserAccount staff) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.SECURED,
            money("280000000"),
            48,
            LoanPurpose.BUSINESS,
            CollateralType.VEHICLE_REGISTRATION,
            money("322000000"),
            "Hồ sơ demo đã lên lịch hẹn thẩm định xe"
        );
        createSecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            735,
            RiskRank.A,
            CustomerSegment.LOW_RISK_HIGH_VALUE,
            DssRecommendation.APPROVE_RECOMMENDED,
            "Tài sản bảo đảm hợp lệ, chờ bước gặp mặt và định giá trực tiếp"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            22,
            18,
            17,
            RiskLevel.LOW,
            "Rủi ro thấp sau khi đối chiếu hồ sơ, cần gặp mặt để xác nhận tài sản",
            Instant.now()
        ));

        BigDecimal annualRate = loanEligibilityService.defaultAnnualRate(LoanType.SECURED);
        BigDecimal approvedAmount = money("260000000");
        int approvedTerm = 48;
        BigDecimal monthlyPayment = loanEligibilityService.calculateMonthlyPayment(approvedAmount, approvedTerm, annualRate);
        Instant scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS);
        String reason = "Lịch hẹn gặp mặt: " + scheduledAt + "; địa điểm: 25 Nguyễn Huệ, Q1; ghi chú: Mang bản gốc đăng ký xe";

        loanRepository.updateDecision(
            loan.id(),
            LoanStatus.APPOINTMENT_SCHEDULED,
            reason,
            money("322000000"),
            approvedAmount,
            approvedTerm,
            annualRate,
            monthlyPayment,
            LoanEligibilityService.POLICY_VERSION
        );
        staffReviewRepository.insertAppointment(
            loan.id(),
            customer.id(),
            staff.id(),
            scheduledAt,
            "25 Nguyễn Huệ, Q1",
            "Mang bản gốc đăng ký xe"
        );
        staffReviewRepository.insertDecisionAudit(loan.id(), staff.id(), StaffDecisionAction.APPROVE, reason);
    }

    private void seedActiveUnsecuredLoan(UserAccount customer, UserAccount staff) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.UNSECURED,
            money("90000000"),
            18,
            LoanPurpose.PERSONAL,
            null,
            money("115000000"),
            "Hồ sơ demo đang trả nợ"
        );
        createUnsecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            742,
            RiskRank.A,
            CustomerSegment.LOW_RISK_HIGH_VALUE,
            DssRecommendation.APPROVE_RECOMMENDED,
            "Khách hàng có lịch sử thanh toán tốt và đủ khả năng trả nợ"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            16,
            14,
            15,
            RiskLevel.LOW,
            "Rủi ro tổng thể thấp, khoản vay đang ở giai đoạn thu nợ demo",
            Instant.now()
        ));

        BigDecimal annualRate = loanEligibilityService.defaultAnnualRate(LoanType.UNSECURED);
        BigDecimal approvedAmount = money("85000000");
        int approvedTerm = 18;
        BigDecimal monthlyPayment = loanEligibilityService.calculateMonthlyPayment(approvedAmount, approvedTerm, annualRate);
        String reason = "Đã duyệt hồ sơ";

        loanRepository.updateDecision(
            loan.id(),
            LoanStatus.APPROVED,
            reason,
            money("115000000"),
            approvedAmount,
            approvedTerm,
            annualRate,
            monthlyPayment,
            LoanEligibilityService.POLICY_VERSION
        );
        staffReviewRepository.insertDecisionAudit(loan.id(), staff.id(), StaffDecisionAction.APPROVE, reason);

        LocalDate startDate = LocalDate.now().minusMonths(4);
        loanContractRepository.create(
            loan.id(),
            customer.id(),
            approvedAmount,
            annualRate,
            approvedTerm,
            startDate,
            startDate.plusMonths(approvedTerm),
            startDate.plusMonths(1),
            String.valueOf(startDate.plusMonths(1).getDayOfMonth()),
            startDate.plusMonths(approvedTerm),
            monthlyPayment.setScale(2),
            monthlyPayment.multiply(BigDecimal.valueOf(approvedTerm)).subtract(approvedAmount).setScale(2)
        );
        loanRepository.updateStatus(loan.id(), LoanStatus.CONTRACTED);
        loanRepository.updateStatus(loan.id(), LoanStatus.ACTIVE);

        repaymentRepository.create(
            loan.id(),
            customer.id(),
            monthlyPayment,
            monthlyPayment,
            startDate.plusMonths(1),
            startDate.plusMonths(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            RepaymentStatus.ON_TIME,
            8,
            "Kỳ 1 thanh toán đúng hạn"
        );
        repaymentRepository.create(
            loan.id(),
            customer.id(),
            monthlyPayment,
            monthlyPayment,
            startDate.plusMonths(2),
            startDate.plusMonths(2).plusDays(6).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            RepaymentStatus.LATE,
            -12,
            "Kỳ 2 thanh toán trễ hạn"
        );
        repaymentRepository.create(
            loan.id(),
            customer.id(),
            monthlyPayment,
            monthlyPayment,
            startDate.plusMonths(3),
            startDate.plusMonths(3).minusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            RepaymentStatus.ON_TIME,
            10,
            "Kỳ 3 đã bù đủ lịch sử thanh toán"
        );
    }

    private void seedRejectedSecuredLoan(UserAccount customer) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.SECURED,
            money("200000000"),
            36,
            LoanPurpose.BUSINESS,
            CollateralType.VEHICLE_REGISTRATION,
            money("175000000"),
            "Hồ sơ demo bị tự động từ chối do điểm tín dụng thấp"
        );
        createSecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            560,
            RiskRank.D,
            CustomerSegment.HIGH_RISK_HIGH_VALUE,
            DssRecommendation.REJECT_RECOMMENDED,
            "Điểm tín dụng quá thấp, DTI và mức độ biến động thu nhập không đạt"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            88,
            36,
            44,
            RiskLevel.HIGH,
            "Hồ sơ demo bị từ chối để test thông điệp hủy hồ sơ vay thế chấp",
            Instant.now()
        ));
        loanRepository.updateDecision(
            loan.id(),
            LoanStatus.REJECTED,
            "Hồ sơ vay thế chấp đã bị tự động từ chối vì điểm tín dụng quá thấp nên hồ sơ đã bị hủy.",
            money("175000000"),
            null,
            null,
            null,
            null,
            null
        );
    }

    private void seedContractedSecuredLoan(UserAccount customer, UserAccount staff) {
        LoanRecord loan = createLoan(
            customer.id(),
            LoanType.SECURED,
            money("180000000"),
            24,
            LoanPurpose.BUSINESS,
            CollateralType.VEHICLE_REGISTRATION,
            money("210000000"),
            "Hồ sơ demo đã hoàn tất thủ tục thế chấp"
        );
        createSecuredDocuments(loan.id());
        dssResultRepository.upsert(loan.id(), new DssResult(
            718,
            RiskRank.B,
            CustomerSegment.LOW_RISK_HIGH_VALUE,
            DssRecommendation.APPROVE_RECOMMENDED,
            "Hồ sơ đạt yêu cầu, đã hoàn tất thực địa và định giá"
        ));
        riskAssessmentRepository.upsert(new RiskAssessment(
            loan.id(),
            28,
            20,
            18,
            RiskLevel.LOW,
            "Khoản vay demo đã đủ hồ sơ và sẵn sàng cho thủ tục thế chấp",
            Instant.now()
        ));

        BigDecimal annualRate = loanEligibilityService.defaultAnnualRate(LoanType.SECURED);
        BigDecimal approvedAmount = money("170000000");
        int approvedTerm = 24;
        BigDecimal monthlyPayment = loanEligibilityService.calculateMonthlyPayment(approvedAmount, approvedTerm, annualRate);
        Instant scheduledAt = Instant.now().minus(2, ChronoUnit.DAYS);
        String reason = "Lịch hẹn gặp mặt: " + scheduledAt + "; địa điểm: 12 Lê Lợi, Q1; ghi chú: Đã đối chiếu tài sản trực tiếp";

        loanRepository.updateDecision(
            loan.id(),
            LoanStatus.APPOINTMENT_SCHEDULED,
            reason,
            money("210000000"),
            approvedAmount,
            approvedTerm,
            annualRate,
            monthlyPayment,
            LoanEligibilityService.POLICY_VERSION
        );
        staffReviewRepository.insertAppointment(
            loan.id(),
            customer.id(),
            staff.id(),
            scheduledAt,
            "12 Lê Lợi, Q1",
            "Đã đối chiếu tài sản trực tiếp"
        );
        securedLoanProcedureRepository.markLatestAppointmentCompleted(loan.id());
        staffReviewRepository.insertDecisionAudit(loan.id(), staff.id(), StaffDecisionAction.APPROVE, reason);

        LocalDate startDate = LocalDate.now().minusMonths(1);
        loanContractRepository.create(
            loan.id(),
            customer.id(),
            approvedAmount,
            annualRate,
            approvedTerm,
            startDate,
            startDate.plusMonths(approvedTerm),
            startDate.plusMonths(1),
            "Ngày 15 hằng tháng",
            startDate.plusMonths(approvedTerm),
            monthlyPayment.setScale(2),
            monthlyPayment.multiply(BigDecimal.valueOf(approvedTerm)).subtract(approvedAmount).setScale(2)
        );
        securedLoanProcedureRepository.upsert(
            loan.id(),
            staff.id(),
            new StaffSecuredProcedureRequest(
                "Công ty Tài chính Demo",
                "1 Lê Thánh Tôn, Q1",
                "0312345678",
                "02812345678",
                "HDTC-2026-001",
                LocalDate.now().minusDays(1),
                "Việt Nam",
                "079123456789",
                "45/8 Cách Mạng Tháng 8, Q3",
                "45/8 Cách Mạng Tháng 8, Q3",
                "Quản lý kho",
                "Tổ trưởng",
                "Xe tải nhỏ",
                "Toyota",
                "ENG-DEMO-001",
                "FRM-DEMO-001",
                "Nguyễn Minh An",
                "TSBD-001",
                "51D-12345",
                money("300000000"),
                money("120000000"),
                money("305000000"),
                annualRate.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP),
                monthlyPayment,
                startDate.plusMonths(1),
                "Ngày 15 hằng tháng",
                startDate.plusMonths(approvedTerm),
                "APR-001",
                "BH-001",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                SecuredProcedureStatus.COMPLETED,
                "Bộ thủ tục thế chấp demo đã được nhân viên xác nhận đầy đủ"
            )
        );
        loanRepository.updateStatus(loan.id(), LoanStatus.CONTRACTED);
    }

    private LoanRecord createLoan(
        Long customerId,
        LoanType loanType,
        BigDecimal amount,
        int termMonths,
        LoanPurpose purpose,
        CollateralType collateralType,
        BigDecimal eligibleLimit,
        String intakeNote
    ) {
        return loanRepository.create(
            customerId,
            loanType,
            amount,
            termMonths,
            purpose,
            collateralType,
            eligibleLimit,
            intakeNote
        );
    }

    private void createUnsecuredDocuments(Long loanRequestId) {
        createLoanDocument(loanRequestId, LoanDocumentType.ID_CARD_FRONT, "cccd-front.png");
        createLoanDocument(loanRequestId, LoanDocumentType.ID_CARD_BACK, "cccd-back.png");
        createLoanDocument(loanRequestId, LoanDocumentType.FACE_CAPTURE, "face-capture.png");
    }

    private void createSecuredDocuments(Long loanRequestId) {
        createLoanDocument(loanRequestId, LoanDocumentType.VEHICLE_REGISTRATION, "vehicle-registration.png");
        createLoanDocument(loanRequestId, LoanDocumentType.LICENSE_PLATE_IMAGE, "license-plate.png");
    }

    private void createLoanDocument(Long loanRequestId, LoanDocumentType documentType, String originalFileName) {
        StoredDemoFile file = writeLoanImage(loanRequestId, documentType, originalFileName);
        loanDocumentRepository.create(
            loanRequestId,
            documentType,
            new LoanDocumentStorageService.StoredLoanDocument(
                file.originalFileName(),
                file.storageName(),
                file.contentType(),
                file.fileSize(),
                file.uploadedAt()
            )
        );
    }

    private void upsertProfile(
        Long userId,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        BigDecimal monthlyIncome,
        BigDecimal verifiedMonthlyIncome,
        BigDecimal debtToIncomeRatio,
        String employmentStatus,
        LocalDate employmentStartDate,
        Integer creditHistoryScore,
        Integer paymentRating,
        StoredDemoFile payslip
    ) {
        customerProfileRepository.upsert(new CustomerProfile(
            userId,
            fullName,
            phone,
            dateOfBirth,
            monthlyIncome,
            verifiedMonthlyIncome,
            debtToIncomeRatio,
            employmentStatus,
            employmentStartDate,
            creditHistoryScore,
            paymentRating,
            payslip != null ? payslip.originalFileName() : null,
            payslip != null ? payslip.storageName() : null,
            payslip != null ? payslip.contentType() : null,
            payslip != null ? payslip.fileSize() : null,
            payslip != null ? payslip.uploadedAt() : null
        ));

        jdbcTemplate.update(
            """
            UPDATE customer_profiles
            SET verified_monthly_income = ?,
                payment_rating = ?,
                payslip_original_filename = ?,
                payslip_storage_name = ?,
                payslip_content_type = ?,
                payslip_file_size = ?,
                payslip_uploaded_at = ?
            WHERE user_id = ?
            """,
            verifiedMonthlyIncome,
            paymentRating,
            payslip != null ? payslip.originalFileName() : null,
            payslip != null ? payslip.storageName() : null,
            payslip != null ? payslip.contentType() : null,
            payslip != null ? payslip.fileSize() : null,
            payslip != null ? java.sql.Timestamp.from(payslip.uploadedAt()) : null,
            userId
        );
    }

    private UserAccount createUser(String email, Role role) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
            .orElseGet(() -> userRepository.create(normalizedEmail, passwordEncoder.encode(demoPassword), role));
    }

    private List<Long> findSampleUserIds() {
        String placeholders = placeholders(SAMPLE_EMAILS.size());
        return jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE email IN (" + placeholders + ")",
            Long.class,
            SAMPLE_EMAILS.toArray()
        );
    }

    private List<Long> findSampleLoanIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(userIds.size());
        return jdbcTemplate.queryForList(
            "SELECT id FROM loan_requests WHERE customer_id IN (" + placeholders + ")",
            Long.class,
            userIds.toArray()
        );
    }

    private void deleteByIds(String sqlTemplate, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = placeholders(ids.size());
        jdbcTemplate.update(String.format(sqlTemplate, placeholders), ids.toArray());
    }

    private void deleteStorageDirectories(Path storageRoot, List<Long> ids) {
        for (Long id : ids) {
            Path target = storageRoot.resolve(String.valueOf(id)).normalize();
            if (!target.startsWith(storageRoot)) {
                continue;
            }
            if (!Files.exists(target)) {
                continue;
            }
            try (var walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("Failed to delete demo storage path: " + path, ex);
                    }
                });
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to clean demo storage root: " + target, ex);
            }
        }
    }

    private StoredDemoFile writePayslip(Long userId, String originalFileName) {
        return writeFile(
            payslipStorageRoot.resolve(String.valueOf(userId)),
            "demo-payslip-" + userId + ".pdf",
            originalFileName,
            "application/pdf",
            SAMPLE_PDF_BYTES
        );
    }

    private StoredDemoFile writeLoanImage(Long loanRequestId, LoanDocumentType documentType, String originalFileName) {
        return writeFile(
            loanDocumentStorageRoot.resolve(String.valueOf(loanRequestId)),
            documentType.name().toLowerCase(Locale.ROOT) + "-demo.png",
            originalFileName,
            "image/png",
            SAMPLE_PNG_BYTES
        );
    }

    private StoredDemoFile writeFile(
        Path directory,
        String storageName,
        String originalFileName,
        String contentType,
        byte[] content
    ) {
        try {
            Files.createDirectories(directory);
            Path filePath = directory.resolve(storageName).normalize();
            if (!filePath.startsWith(directory.toAbsolutePath().normalize())) {
                throw new IllegalStateException("Invalid demo storage path: " + filePath);
            }
            Files.write(filePath, content);
            return new StoredDemoFile(
                originalFileName,
                storageName,
                contentType,
                (long) content.length,
                Instant.now().minus(7, ChronoUnit.DAYS)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write demo file: " + originalFileName, ex);
        }
    }

    private String placeholders(int size) {
        List<String> placeholders = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            placeholders.add("?");
        }
        return String.join(", ", placeholders);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private record StoredDemoFile(
        String originalFileName,
        String storageName,
        String contentType,
        Long fileSize,
        Instant uploadedAt
    ) {
    }
}
