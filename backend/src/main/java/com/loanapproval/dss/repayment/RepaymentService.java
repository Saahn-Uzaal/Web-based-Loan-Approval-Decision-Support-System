package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanInstallmentService;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanStatusHistoryService;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.dto.RepaymentCreateResponse;
import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
import com.loanapproval.dss.repayment.dto.RepaymentItemResponse;
import com.loanapproval.dss.shared.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepaymentService {

    private static final Logger log = LoggerFactory.getLogger(RepaymentService.class);
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final RepaymentRepository repaymentRepository;
    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final LoanContractService loanContractService;
    private final LoanInstallmentService loanInstallmentService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final LoanDelinquencyRepository loanDelinquencyRepository;
    private final LoanDelinquencyService loanDelinquencyService;
    private final LoanStatusHistoryService loanStatusHistoryService;
    private final NotificationService notificationService;

    public RepaymentService(
            RepaymentRepository repaymentRepository,
            LoanRepository loanRepository,
            CustomerProfileRepository customerProfileRepository,
            LoanContractService loanContractService,
            LoanInstallmentService loanInstallmentService,
            RepaymentScheduleService repaymentScheduleService,
            LoanDelinquencyRepository loanDelinquencyRepository,
            LoanDelinquencyService loanDelinquencyService,
            LoanStatusHistoryService loanStatusHistoryService,
            NotificationService notificationService) {
        this.repaymentRepository = repaymentRepository;
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.loanContractService = loanContractService;
        this.loanInstallmentService = loanInstallmentService;
        this.repaymentScheduleService = repaymentScheduleService;
        this.loanDelinquencyRepository = loanDelinquencyRepository;
        this.loanDelinquencyService = loanDelinquencyService;
        this.loanStatusHistoryService = loanStatusHistoryService;
        this.notificationService = notificationService;
    }

    public RepaymentHistoryResponse listMine(Long customerId) {
        int currentRating = customerProfileRepository.findPaymentRatingByUserId(customerId).orElse(0);
        List<RepaymentItemResponse> items = repaymentRepository.findByCustomerId(customerId).stream()
                .map(this::toItemResponse)
                .toList();
        return new RepaymentHistoryResponse(currentRating, items);
    }

    public RepaymentHistoryResponse listMinePaged(Long customerId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;
        int currentRating = customerProfileRepository.findPaymentRatingByUserId(customerId).orElse(0);
        long total = repaymentRepository.countByCustomerId(customerId);
        List<RepaymentItemResponse> items = repaymentRepository
                .findByCustomerIdPaged(customerId, safeOffset, safeSize)
                .stream()
                .map(this::toItemResponse)
                .toList();
        PageResponse<RepaymentItemResponse> page0 = PageResponse.of(
                items,
                Math.max(page, 0),
                safeSize,
                total);
        return new RepaymentHistoryResponse(
                currentRating,
                page0.content(),
                java.util.List.of(),
                page0.page(),
                page0.size(),
                page0.totalElements(),
                page0.totalPages(),
                page0.last());
    }

    @Transactional
    public RepaymentCreateResponse createByStaff(
            Long loanRequestId,
            Long customerId,
            BigDecimal amountPaid,
            Instant effectivePaidAt,
            String note) {
        return createByStaff(loanRequestId, customerId, amountPaid, effectivePaidAt, note, null);
    }

    @Transactional
    public RepaymentCreateResponse createByStaff(
            Long loanRequestId,
            Long customerId,
            BigDecimal amountPaid,
            Instant effectivePaidAt,
            String note,
            Long actorUserId) {
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan application not found"));
        if (!loan.customerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan does not belong to the confirmed customer");
        }
        return createForLoan(loan, customerId, amountPaid, effectivePaidAt, sanitizeNote(note), actorUserId);
    }

    public LoanRepaymentSnapshot snapshotForLoan(LoanRecord loan, Long customerId) {
        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan does not have an active contract");
        }
        return repaymentScheduleService.snapshot(loan, contract, customerId);
    }

    public LoanRepaymentSnapshot trySnapshotForLoan(LoanRecord loan, Long customerId) {
        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            return null;
        }
        return repaymentScheduleService.snapshot(loan, contract, customerId);
    }

    private RepaymentCreateResponse createForLoan(
            LoanRecord loan,
            Long customerId,
            BigDecimal rawAmountPaid,
            Instant effectivePaidAt,
            String note,
            Long actorUserId) {
        if (rawAmountPaid == null || rawAmountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than 0");
        }
        if (loan.status() != LoanStatus.ACTIVE
                && loan.status() != LoanStatus.OVERDUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only active or overdue loans can receive repayments");
        }

        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan does not have an active contract");
        }

        if (customerProfileRepository.findPaymentRatingByUserId(customerId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer profile must be completed before recording repayments");
        }

        Instant paidAt = effectivePaidAt != null ? effectivePaidAt : Instant.now();
        var paidDate = paidAt.atZone(SYSTEM_ZONE).toLocalDate();
        LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(loan, contract, customerId, paidDate);
        if (snapshot.fullyPaid() || snapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This loan has already been fully settled");
        }

        BigDecimal amountPaid = rawAmountPaid.setScale(0, RoundingMode.HALF_UP);
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than 0");
        }
        if (amountPaid.compareTo(snapshot.outstandingAmount()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount cannot exceed the remaining outstanding balance");
        }

        if (snapshot.overdue()) {
            loanDelinquencyService.assessLoan(loan.id(), paidDate);
        }

        RepaymentStatus repaymentStatus = resolveRepaymentStatus(paidDate, snapshot.dueDate());
        boolean completedCurrentInstallment = amountPaid.compareTo(snapshot.currentAmountDue()) >= 0;
        int ratingDelta = completedCurrentInstallment
                ? RepaymentRatingPolicy.rewardDelta(repaymentStatus)
                : 0;

        RepaymentRecord record = repaymentRepository.create(
                loan.id(),
                customerId,
                snapshot.currentAmountDue(),
                amountPaid,
                snapshot.dueDate(),
                paidAt,
                repaymentStatus,
                ratingDelta,
                note);
        loanInstallmentService.rebuildLedger(contract);

        int currentRating = customerProfileRepository.adjustPaymentRating(customerId, ratingDelta)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Customer profile was not found"));

        LoanRepaymentSnapshot afterSnapshot = repaymentScheduleService.snapshot(loan, contract, customerId, paidDate);
        if (afterSnapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            if (snapshot.overdue() || loan.status() == LoanStatus.OVERDUE) {
                loanDelinquencyService.assessLoan(loan.id(), paidDate);
            }
            loanRepository.updateStatus(loan.id(), LoanStatus.CLOSED);
            loanStatusHistoryService.recordTransition(
                    loan,
                    LoanStatus.CLOSED,
                    actorUserId,
                    "REPAYMENT",
                    "Loan fully settled by recorded repayment");
            loanContractService.closeContract(contract.id());
            notificationService.notifyCustomerLoanClosed(loan.id(), customerId, actorUserId);
        } else if (snapshot.overdue() || loan.status() == LoanStatus.OVERDUE) {
            loanDelinquencyService.assessLoan(loan.id(), paidDate);
        }

        log.info(
                "Repayment recorded: loanRequestId={}, customerId={}, amountPaid={}, status={}, ratingDelta={}, newRating={}",
                loan.id(),
                customerId,
                amountPaid,
                repaymentStatus,
                ratingDelta,
                currentRating);

        return new RepaymentCreateResponse(toItemResponse(record), currentRating);
    }

    private RepaymentItemResponse toItemResponse(RepaymentRecord record) {
        return new RepaymentItemResponse(
                record.id(),
                record.loanRequestId(),
                record.amountDue(),
                record.amountPaid(),
                record.dueDate(),
                record.paidAt(),
                record.repaymentStatus(),
                resolveVisibleRatingDelta(record),
                record.note(),
                record.createdAt());
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private RepaymentStatus resolveRepaymentStatus(java.time.LocalDate paidDate, java.time.LocalDate dueDate) {
        if (paidDate.isBefore(dueDate)) {
            return RepaymentStatus.EARLY;
        }
        if (paidDate.isAfter(dueDate)) {
            return RepaymentStatus.LATE;
        }
        return RepaymentStatus.ON_TIME;
    }

    private Integer resolveVisibleRatingDelta(RepaymentRecord record) {
        Integer repaymentDelta = record.ratingDelta();
        if (record.repaymentStatus() != RepaymentStatus.LATE) {
            return repaymentDelta;
        }
        if (repaymentDelta != null && repaymentDelta != 0) {
            return repaymentDelta;
        }
        LoanDelinquencyRecord delinquency = loanDelinquencyRepository
                .findLatestByLoanAndDueDate(record.loanRequestId(), record.dueDate())
                .orElse(null);
        if (delinquency != null
                && delinquency.totalRatingDelta() != null
                && delinquency.totalRatingDelta() < 0) {
            return delinquency.totalRatingDelta();
        }
        return RepaymentRatingPolicy.firstLatePenalty();
    }
}
