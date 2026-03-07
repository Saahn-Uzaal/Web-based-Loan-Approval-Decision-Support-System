package com.loanapproval.dss.loan;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.loan.dto.LoanSummaryResponse;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskAssessmentService;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.shared.PageResponse;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.CustomerVerificationService;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerLoanService {

    private static final Logger log = LoggerFactory.getLogger(CustomerLoanService.class);

    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;
    private final DecisionEngineService decisionEngineService;
    private final DssResultRepository dssResultRepository;
    private final CustomerVerificationService customerVerificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceAuditService complianceAuditService;
    private final LoanContractService loanContractService;

    public CustomerLoanService(
        LoanRepository loanRepository,
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtService customerDebtService,
        DecisionEngineService decisionEngineService,
        DssResultRepository dssResultRepository,
        CustomerVerificationService customerVerificationService,
        RiskAssessmentService riskAssessmentService,
        ComplianceAuditService complianceAuditService,
        LoanContractService loanContractService
    ) {
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
        this.decisionEngineService = decisionEngineService;
        this.dssResultRepository = dssResultRepository;
        this.customerVerificationService = customerVerificationService;
        this.riskAssessmentService = riskAssessmentService;
        this.complianceAuditService = complianceAuditService;
        this.loanContractService = loanContractService;
    }

    @Transactional
    public LoanDetailResponse create(Long customerId, CreateLoanRequest request) {
        LoanRecord loan = loanRepository.create(
            customerId,
            request.amount(),
            request.termMonths(),
            request.purpose()
        );

        log.info("Loan application created: loanId={}, customerId={}, amount={}, termMonths={}, purpose={}",
            loan.id(), customerId, request.amount(), request.termMonths(), request.purpose());

        CustomerProfile profile = customerProfileRepository.findByUserId(customerId).orElse(null);
        CustomerVerification verification = customerVerificationService.getOrDefault(customerId);
        BigDecimal existingMonthlyDebt = customerDebtService.sumActiveMonthlyDebt(customerId);
        BigDecimal projectedMonthlyPayment = loanContractService.calculateProjectedMonthlyPayment(
            request.amount(),
            request.termMonths()
        );
        BigDecimal projectedDti = resolveProjectedDti(profile, existingMonthlyDebt, projectedMonthlyPayment);

        DecisionInput decisionInput = new DecisionInput(
            customerId,
            profile != null ? profile.monthlyIncome() : null,
            projectedDti,
            profile != null ? profile.employmentStatus() : null,
            profile != null ? profile.dateOfBirth() : null,
            profile != null ? profile.employmentStartDate() : null,
            profile != null ? profile.creditHistoryScore() : null,
            request.collateralValue(),
            existingMonthlyDebt,
            request.amount(),
            request.termMonths(),
            request.purpose(),
            profile != null ? profile.paymentRating() : null,
            isFailed(verification.kycStatus()),
            isFailed(verification.amlStatus()),
            verification.fraudFlag(),
            asIncomeVerified(verification.incomeStatus())
        );

        DssResult dssResult = decisionEngineService.evaluate(decisionInput);
        dssResultRepository.upsert(loan.id(), dssResult);

        RiskAssessment riskAssessment = riskAssessmentService.evaluateAndSave(
            loan.id(),
            decisionInput,
            dssResult,
            verification
        );

        applyWorkflowTransition(customerId, loan, dssResult, riskAssessment, verification);
        loan = loanRepository.findOwnedById(loan.id(), customerId).orElse(loan);

        complianceAuditService.log(
            customerId,
            loan.id(),
            customerId,
            "LOAN_APPLICATION_EVALUATED",
            resolveComplianceOutcome(verification),
            String.format(
                "recommendation=%s, riskLevel=%s, creditRisk=%d, fraudRisk=%d, operationalRisk=%d, projectedDti=%s",
                dssResult.recommendation(),
                riskAssessment.overallRiskLevel(),
                riskAssessment.creditRiskScore(),
                riskAssessment.fraudRiskScore(),
                riskAssessment.operationalRiskScore(),
                projectedDti != null ? projectedDti.toPlainString() : "N/A"
            )
        );

        return toDetailResponse(loan);
    }

    public List<LoanSummaryResponse> listMine(Long customerId) {
        return loanRepository.findByCustomerId(customerId).stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    public PageResponse<LoanSummaryResponse> listMinePaged(Long customerId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        long total = loanRepository.countByCustomerId(customerId);
        List<LoanSummaryResponse> content = loanRepository
            .findByCustomerIdPaged(customerId, safeOffset, safeSize)
            .stream()
            .map(this::toSummaryResponse)
            .toList();
        return PageResponse.of(content, Math.max(page, 0), safeSize, total);
    }

    public LoanDetailResponse getMineById(Long customerId, Long id) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));
        return toDetailResponse(loan);
    }

    private void applyWorkflowTransition(
        Long customerId,
        LoanRecord loan,
        DssResult dssResult,
        RiskAssessment riskAssessment,
        CustomerVerification verification
    ) {
        if (verification.hasHardRejectFlag()) {
            String reason = "Auto rejected by compliance checks (KYC/AML/FRAUD).";
            loanRepository.updateStatusAndReason(loan.id(), LoanStatus.REJECTED, reason);
            complianceAuditService.log(
                customerId,
                loan.id(),
                customerId,
                "LOAN_APPLICATION_AUTO_REJECTED",
                ComplianceOutcome.FAILED,
                reason
            );
            return;
        }

        if (dssResult.recommendation() == DssRecommendation.ESCALATE_RECOMMENDED ||
            riskAssessment.overallRiskLevel() == RiskLevel.HIGH) {
            loanRepository.updateStatus(loan.id(), LoanStatus.WAITING_SUPERVISOR);
        }
    }

    private BigDecimal resolveProjectedDti(
        CustomerProfile profile,
        BigDecimal existingMonthlyDebt,
        BigDecimal projectedMonthlyPayment
    ) {
        if (profile == null || profile.monthlyIncome() == null || profile.monthlyIncome().compareTo(BigDecimal.ZERO) <= 0) {
            return profile != null ? profile.debtToIncomeRatio() : null;
        }
        BigDecimal totalMonthlyDebt = nonNegative(existingMonthlyDebt).add(nonNegative(projectedMonthlyPayment));
        return totalMonthlyDebt
            .multiply(BigDecimal.valueOf(100))
            .divide(profile.monthlyIncome(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private boolean isFailed(VerificationStatus status) {
        return status == VerificationStatus.FAILED;
    }

    private Boolean asIncomeVerified(VerificationStatus status) {
        if (status == VerificationStatus.PASSED) {
            return true;
        }
        if (status == VerificationStatus.FAILED) {
            return false;
        }
        return null;
    }

    private ComplianceOutcome resolveComplianceOutcome(CustomerVerification verification) {
        if (verification.hasHardRejectFlag()) {
            return ComplianceOutcome.FAILED;
        }
        if (verification.isPending()) {
            return ComplianceOutcome.INFO;
        }
        return ComplianceOutcome.PASSED;
    }

    private LoanSummaryResponse toSummaryResponse(LoanRecord loan) {
        return new LoanSummaryResponse(
            loan.id(),
            loan.amount(),
            loan.termMonths(),
            loan.purpose(),
            loan.status(),
            loan.finalReason(),
            loan.createdAt()
        );
    }

    private LoanDetailResponse toDetailResponse(LoanRecord loan) {
        return new LoanDetailResponse(
            loan.id(),
            loan.customerId(),
            loan.amount(),
            loan.termMonths(),
            loan.purpose(),
            loan.status(),
            loan.finalReason(),
            loan.createdAt(),
            loan.updatedAt()
        );
    }
}
