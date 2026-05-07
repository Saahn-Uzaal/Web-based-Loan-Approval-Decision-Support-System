package com.loanapproval.dss.loan;

import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.risk.RiskAssessment;
import com.loanapproval.dss.risk.RiskAssessmentService;
import com.loanapproval.dss.risk.RiskLevel;
import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanApprovalReassessmentService {

    private static final int MAX_REASSESSMENT_PASSES = 3;

    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;
    private final DecisionEngineService decisionEngineService;
    private final DssResultRepository dssResultRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final LoanEligibilityService loanEligibilityService;
    private final LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;

    public LoanApprovalReassessmentService(
            CustomerProfileRepository customerProfileRepository,
            CustomerDebtService customerDebtService,
            DecisionEngineService decisionEngineService,
            DssResultRepository dssResultRepository,
            RiskAssessmentService riskAssessmentService,
            LoanEligibilityService loanEligibilityService,
            LoanApplicationSnapshotRepository loanApplicationSnapshotRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
        this.decisionEngineService = decisionEngineService;
        this.dssResultRepository = dssResultRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.loanEligibilityService = loanEligibilityService;
        this.loanApplicationSnapshotRepository = loanApplicationSnapshotRepository;
    }

    public ReassessmentResult reassessAndPersist(
            LoanRecord loan,
            CustomerVerification verification,
            BigDecimal requestedApprovedAmount,
            Integer requestedTermMonths,
            BigDecimal requestedAnnualRate,
            BigDecimal collateralValue,
            boolean allowAutoAdjust) {
        LoanApplicationSnapshot snapshot = loanApplicationSnapshotRepository.findByLoanRequestId(loan.id()).orElse(null);
        CustomerProfile profile = snapshot != null
                ? profileFromSnapshot(snapshot)
                : customerProfileRepository.findByUserId(loan.customerId()).orElse(null);
        BigDecimal existingMonthlyDebt = customerDebtService.sumActiveMonthlyDebt(loan.customerId());
        CandidateTerms candidate = resolveCandidateTerms(
                loan,
                requestedApprovedAmount,
                requestedTermMonths,
                requestedAnnualRate);
        AssessmentPass stablePass = null;
        boolean amountAdjusted = false;

        for (int i = 0; i < MAX_REASSESSMENT_PASSES; i++) {
            AssessmentPass currentPass = assess(
                    loan,
                    profile,
                    existingMonthlyDebt,
                    verification,
                    candidate,
                    collateralValue);
            stablePass = currentPass;

            if (currentPass.approvedAmount().compareTo(candidate.approvedAmount()) == 0) {
                break;
            }
            if (!allowAutoAdjust) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        buildEligibilityExceededMessage(currentPass.eligibleLimit()));
            }
            if (currentPass.approvedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            candidate = candidate.withApprovedAmount(currentPass.approvedAmount());
            amountAdjusted = true;
        }

        if (stablePass == null || stablePass.approvedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không tìm được hạn mức an toàn để phê duyệt theo điều khoản hiện tại");
        }
        if (stablePass.dssResult().recommendation() == DssRecommendation.REJECT_RECOMMENDED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "DSS đánh giá lại và đề xuất từ chối với điều khoản vay hiện tại");
        }
        if (stablePass.riskAssessment().overallRiskLevel() == RiskLevel.HIGH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đánh giá rủi ro sau khi tính lại đang ở mức HIGH, không thể phê duyệt");
        }

        dssResultRepository.upsert(loan.id(), stablePass.dssResult());
        riskAssessmentService.save(stablePass.riskAssessment());

        return new ReassessmentResult(
                stablePass.eligibleLimit(),
                stablePass.approvedAmount(),
                stablePass.approvedTermMonths(),
                stablePass.approvedAnnualRate(),
                stablePass.approvedMonthlyPayment(),
                loanEligibilityService.currentPolicyVersion(),
                buildExplanation(stablePass, amountAdjusted, collateralValue),
                stablePass.projectedDti(),
                amountAdjusted);
    }

    private AssessmentPass assess(
            LoanRecord loan,
            CustomerProfile profile,
            BigDecimal existingMonthlyDebt,
            CustomerVerification verification,
            CandidateTerms candidate,
            BigDecimal collateralValue) {
        BigDecimal projectedMonthlyPayment = loanEligibilityService.calculateMonthlyPayment(
                candidate.approvedAmount(),
                candidate.approvedTermMonths(),
                candidate.approvedAnnualRate());
        BigDecimal projectedDti = resolveProjectedDti(profile, existingMonthlyDebt, projectedMonthlyPayment);
        DecisionInput decisionInput = buildDecisionInput(
                loan,
                profile,
                verification,
                existingMonthlyDebt,
                collateralValue,
                candidate.approvedAmount(),
                candidate.approvedTermMonths(),
                projectedMonthlyPayment,
                projectedDti);
        DssResult dssResult = decisionEngineService.evaluate(decisionInput);
        RiskAssessment riskAssessment = riskAssessmentService.evaluate(
                loan.id(),
                decisionInput,
                dssResult,
                verification);
        LoanEligibilityResult policyResult = loanEligibilityService.evaluateWithActualTerms(
                profile,
                existingMonthlyDebt,
                loan.loanType(),
                candidate.approvedAmount(),
                candidate.approvedTermMonths(),
                candidate.approvedAnnualRate(),
                collateralValue,
                dssResult.riskRank());
        BigDecimal eligibleLimit = mergeEligibleLimit(loan.eligibleLimit(), policyResult.eligibleLimit());
        BigDecimal approvedAmount = candidate.approvedAmount();
        if (eligibleLimit != null && approvedAmount.compareTo(eligibleLimit) > 0) {
            approvedAmount = eligibleLimit;
        }
        approvedAmount = approvedAmount.setScale(0, RoundingMode.HALF_UP);

        return new AssessmentPass(
                decisionInput,
                dssResult,
                riskAssessment,
                eligibleLimit,
                approvedAmount,
                candidate.approvedTermMonths(),
                candidate.approvedAnnualRate(),
                projectedMonthlyPayment,
                projectedDti);
    }

    private CandidateTerms resolveCandidateTerms(
            LoanRecord loan,
            BigDecimal requestedApprovedAmount,
            Integer requestedTermMonths,
            BigDecimal requestedAnnualRate) {
        BigDecimal approvedAmount = requestedApprovedAmount != null
                ? requestedApprovedAmount
                : loan.approvedAmount() != null ? loan.approvedAmount() : loan.amount();
        Integer approvedTermMonths = requestedTermMonths != null
                ? requestedTermMonths
                : loan.approvedTermMonths() != null ? loan.approvedTermMonths() : loan.termMonths();
        BigDecimal approvedAnnualRate = requestedAnnualRate != null
                ? requestedAnnualRate
                : loan.approvedAnnualRate() != null
                        ? loan.approvedAnnualRate()
                        : loanEligibilityService.defaultAnnualRate(loan.loanType());

        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền được duyệt phải lớn hơn 0");
        }
        if (loan.amount() != null && approvedAmount.compareTo(loan.amount()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền được duyệt không được vượt số tiền khách hàng yêu cầu");
        }

        return new CandidateTerms(
                approvedAmount.setScale(0, RoundingMode.HALF_UP),
                approvedTermMonths,
                approvedAnnualRate.setScale(6, RoundingMode.HALF_UP));
    }

    private DecisionInput buildDecisionInput(
            LoanRecord loan,
            CustomerProfile profile,
            CustomerVerification verification,
            BigDecimal existingMonthlyDebt,
            BigDecimal collateralValue,
            BigDecimal approvedAmount,
            Integer approvedTermMonths,
            BigDecimal projectedMonthlyPayment,
            BigDecimal projectedDti) {
        return new DecisionInput(
                loan.customerId(),
                profile != null ? profile.effectiveMonthlyIncome() : null,
                projectedDti,
                profile != null ? profile.employmentStatus() : null,
                profile != null ? profile.dateOfBirth() : null,
                profile != null ? profile.employmentStartDate() : null,
                null,
                collateralValue,
                existingMonthlyDebt,
                approvedAmount,
                approvedTermMonths,
                loan.purpose(),
                profile != null ? profile.paymentRating() : null,
                isFailed(verification.kycStatus()),
                isFailed(verification.amlStatus()),
                verification.fraudFlag(),
                asIncomeVerified(verification.incomeStatus()),
                projectedMonthlyPayment);
    }

    private BigDecimal resolveProjectedDti(
            CustomerProfile profile,
            BigDecimal existingMonthlyDebt,
            BigDecimal projectedMonthlyPayment) {
        BigDecimal income = profile != null ? profile.effectiveMonthlyIncome() : null;
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return profile != null ? profile.debtToIncomeRatio() : null;
        }
        BigDecimal totalMonthlyDebt = nonNegative(existingMonthlyDebt).add(nonNegative(projectedMonthlyPayment));
        return totalMonthlyDebt
                .multiply(BigDecimal.valueOf(100))
                .divide(income, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal mergeEligibleLimit(BigDecimal storedEligibleLimit, BigDecimal recalculatedEligibleLimit) {
        if (storedEligibleLimit == null) {
            return scaleLimit(recalculatedEligibleLimit);
        }
        if (recalculatedEligibleLimit == null) {
            return scaleLimit(storedEligibleLimit);
        }
        return scaleLimit(storedEligibleLimit.min(recalculatedEligibleLimit));
    }

    private BigDecimal scaleLimit(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private String buildEligibilityExceededMessage(BigDecimal eligibleLimit) {
        if (eligibleLimit == null) {
            return "Khoản vay vượt hạn mức an toàn sau khi tính lại theo điều khoản hiện tại";
        }
        return "Khoản vay vượt hạn mức an toàn sau khi tính lại. Hạn mức tối đa: "
                + eligibleLimit.toPlainString();
    }

    private String buildExplanation(
            AssessmentPass pass,
            boolean amountAdjusted,
            BigDecimal collateralValue) {
        StringBuilder builder = new StringBuilder();
        builder.append("DTI dự kiến=")
                .append(pass.projectedDti() != null ? pass.projectedDti().toPlainString() : "Không có")
                .append(", khoản trả hằng tháng đã duyệt=")
                .append(pass.approvedMonthlyPayment().toPlainString())
                .append(", mức rủi ro=")
                .append(pass.riskAssessment().overallRiskLevel())
                .append(", khuyến nghị DSS=")
                .append(pass.dssResult().recommendation());
        if (collateralValue != null) {
            builder.append(", giá trị tài sản sau thẩm định=").append(collateralValue.toPlainString());
        }
        if (amountAdjusted) {
            builder.append(", đã tự điều chỉnh về hạn mức an toàn=true");
        }
        return builder.toString();
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

    private CustomerProfile profileFromSnapshot(LoanApplicationSnapshot snapshot) {
        return new CustomerProfile(
                snapshot.customerId(),
                snapshot.fullName(),
                snapshot.phone(),
                snapshot.dateOfBirth(),
                snapshot.declaredMonthlyIncome(),
                snapshot.verifiedMonthlyIncome(),
                snapshot.debtToIncomeRatio(),
                snapshot.employmentStatus(),
                snapshot.employmentStartDate(),
                snapshot.creditHistoryScore(),
                snapshot.paymentRating(),
                null,
                null,
                null,
                null,
                null);
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

    private record CandidateTerms(
            BigDecimal approvedAmount,
            Integer approvedTermMonths,
            BigDecimal approvedAnnualRate) {
        private CandidateTerms withApprovedAmount(BigDecimal adjustedAmount) {
            return new CandidateTerms(
                    adjustedAmount.setScale(0, RoundingMode.HALF_UP),
                    approvedTermMonths,
                    approvedAnnualRate);
        }
    }

    private record AssessmentPass(
            DecisionInput decisionInput,
            DssResult dssResult,
            RiskAssessment riskAssessment,
            BigDecimal eligibleLimit,
            BigDecimal approvedAmount,
            Integer approvedTermMonths,
            BigDecimal approvedAnnualRate,
            BigDecimal approvedMonthlyPayment,
            BigDecimal projectedDti) {
    }

    public record ReassessmentResult(
            BigDecimal eligibleLimit,
            BigDecimal approvedAmount,
            Integer approvedTermMonths,
            BigDecimal approvedAnnualRate,
            BigDecimal approvedMonthlyPayment,
            String decisionPolicyVersion,
            String explanation,
            BigDecimal projectedDti,
            boolean amountAdjusted) {
    }
}

