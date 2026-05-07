package com.loanapproval.dss.repayment;

import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanDelinquencyService {

    private static final Logger log = LoggerFactory.getLogger(LoanDelinquencyService.class);

    private final LoanDelinquencyRepository loanDelinquencyRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final CustomerProfileRepository customerProfileRepository;
    private final LoanRepository loanRepository;
    private final LoanStatusHistoryService loanStatusHistoryService;

    public LoanDelinquencyService(
            LoanDelinquencyRepository loanDelinquencyRepository,
            RepaymentScheduleService repaymentScheduleService,
            CustomerProfileRepository customerProfileRepository,
            LoanRepository loanRepository,
            LoanStatusHistoryService loanStatusHistoryService) {
        this.loanDelinquencyRepository = loanDelinquencyRepository;
        this.repaymentScheduleService = repaymentScheduleService;
        this.customerProfileRepository = customerProfileRepository;
        this.loanRepository = loanRepository;
        this.loanStatusHistoryService = loanStatusHistoryService;
    }

    @Transactional
    public LoanDelinquencyRunSummary assessAll(LocalDate assessmentDate) {
        LocalDate today = resolveDate(assessmentDate);
        int scannedLoans = 0;
        int openedOrUpdated = 0;
        int cured = 0;
        int ratingAdjustments = 0;

        for (LoanDelinquencyCandidate candidate : loanDelinquencyRepository.findActiveCandidates()) {
            scannedLoans++;
            AssessmentResult result = assessCandidate(candidate, today);
            openedOrUpdated += result.openedOrUpdated();
            cured += result.cured();
            ratingAdjustments += result.ratingAdjustments();
        }

        LoanDelinquencyRunSummary summary =
                new LoanDelinquencyRunSummary(scannedLoans, openedOrUpdated, cured, ratingAdjustments);
        log.info(
                "Loan delinquency assessment finished: scannedLoans={}, openedOrUpdated={}, cured={}, ratingAdjustments={}",
                summary.scannedLoans(),
                summary.openedOrUpdated(),
                summary.cured(),
                summary.ratingAdjustments());
        return summary;
    }

    @Transactional
    public LoanDelinquencyRunSummary assessLoan(Long loanRequestId, LocalDate assessmentDate) {
        LocalDate today = resolveDate(assessmentDate);
        return loanDelinquencyRepository.findCandidateByLoanRequestId(loanRequestId)
                .map(candidate -> {
                    AssessmentResult result = assessCandidate(candidate, today);
                    return new LoanDelinquencyRunSummary(
                            1,
                            result.openedOrUpdated(),
                            result.cured(),
                            result.ratingAdjustments());
                })
                .orElseGet(() -> new LoanDelinquencyRunSummary(0, 0, 0, 0));
    }

    private AssessmentResult assessCandidate(LoanDelinquencyCandidate candidate, LocalDate assessmentDate) {
        LoanRecord loan = candidate.loan();
        LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(
                loan,
                candidate.contract(),
                loan.customerId(),
                assessmentDate);

        if (!snapshot.overdue() || snapshot.currentAmountDue().compareTo(BigDecimal.ZERO) <= 0) {
            int cured = loanDelinquencyRepository.markAllOpenCured(loan.id());
            if (loan.status() == LoanStatus.OVERDUE) {
                loanRepository.updateStatus(loan.id(), LoanStatus.ACTIVE);
                loanStatusHistoryService.recordTransition(
                        loan,
                        LoanStatus.ACTIVE,
                        null,
                        "DELINQUENCY_ASSESSMENT",
                        "Loan cured after overdue assessment");
            }
            return new AssessmentResult(0, cured, 0);
        }

        int daysPastDue = Math.toIntExact(Math.min(snapshot.overdueDays(), Integer.MAX_VALUE));
        LoanDelinquencyRecord delinquency = loanDelinquencyRepository.upsertOpen(
                loan.id(),
                loan.customerId(),
                snapshot.installmentNumber(),
                snapshot.dueDate(),
                snapshot.scheduledInstallmentAmount(),
                snapshot.currentAmountDue(),
                daysPastDue);
        int cured = loanDelinquencyRepository.markOpenOthersCured(
                loan.id(),
                snapshot.installmentNumber(),
                snapshot.dueDate());

        if (loan.status() == LoanStatus.ACTIVE) {
            loanRepository.updateStatus(loan.id(), LoanStatus.OVERDUE);
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.OVERDUE,
                    null,
                    "DELINQUENCY_ASSESSMENT",
                    "Loan moved to overdue after installment delinquency assessment");
        }

        int currentMilestone = RepaymentRatingPolicy.currentLateMilestone(daysPastDue);
        int previousMilestone = delinquency.highestMilestone() != null ? delinquency.highestMilestone() : 0;
        if (currentMilestone <= previousMilestone) {
            return new AssessmentResult(1, cured, 0);
        }

        int ratingDelta = RepaymentRatingPolicy.latePenaltyDelta(previousMilestone, currentMilestone);
        if (ratingDelta == 0) {
            loanDelinquencyRepository.updateMilestoneAndDelta(delinquency.id(), currentMilestone, 0);
            return new AssessmentResult(1, cured, 0);
        }

        if (customerProfileRepository.adjustPaymentRating(loan.customerId(), ratingDelta).isEmpty()) {
            log.warn(
                    "Skipped delinquency rating penalty because customer profile was not found: loanRequestId={}, customerId={}, milestone={}",
                    loan.id(),
                    loan.customerId(),
                    currentMilestone);
            return new AssessmentResult(1, cured, 0);
        }

        loanDelinquencyRepository.updateMilestoneAndDelta(delinquency.id(), currentMilestone, ratingDelta);
        return new AssessmentResult(1, cured, 1);
    }

    private LocalDate resolveDate(LocalDate assessmentDate) {
        return assessmentDate != null ? assessmentDate : LocalDate.now();
    }

    private record AssessmentResult(int openedOrUpdated, int cured, int ratingAdjustments) {
    }
}
