package com.loanapproval.dss.repayment;

import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.dto.CreateRepaymentRequest;
import com.loanapproval.dss.repayment.dto.RepaymentCreateResponse;
import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
import com.loanapproval.dss.repayment.dto.RepaymentItemResponse;
import com.loanapproval.dss.shared.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepaymentService {

    private static final int ON_TIME_RATING_DELTA = 5;
    private static final int LATE_RATING_DELTA = -8;

    private final RepaymentRepository repaymentRepository;
    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public RepaymentService(
        RepaymentRepository repaymentRepository,
        LoanRepository loanRepository,
        CustomerProfileRepository customerProfileRepository
    ) {
        this.repaymentRepository = repaymentRepository;
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
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
        PageResponse<RepaymentItemResponse> page0 = PageResponse.of(items, Math.max(page, 0), safeSize, total);
        return new RepaymentHistoryResponse(currentRating, page0.content(), page0.page(), page0.size(),
            page0.totalElements(), page0.totalPages(), page0.last());
    }

    @Transactional
    public RepaymentCreateResponse create(Long customerId, CreateRepaymentRequest request) {
        LoanRecord loan = loanRepository.findOwnedById(request.loanRequestId(), customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));

        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Repayment is only available for APPROVED loan requests"
            );
        }

        if (customerProfileRepository.findPaymentRatingByUserId(customerId).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Please complete your profile before recording repayments"
            );
        }

        BigDecimal totalPaidBefore = repaymentRepository
            .sumAmountPaidByLoanRequestAndCustomer(request.loanRequestId(), customerId);
        BigDecimal outstandingBefore = loan.amount()
            .subtract(totalPaidBefore)
            .max(BigDecimal.ZERO)
            .setScale(0, RoundingMode.HALF_UP);

        if (outstandingBefore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Loan has already been fully repaid"
            );
        }

        BigDecimal expectedMonthlyDue = calculateExpectedMonthlyDue(loan);
        BigDecimal expectedAmountDue = expectedMonthlyDue.min(outstandingBefore);
        BigDecimal amountPaid = request.amountPaid().setScale(0, RoundingMode.HALF_UP);

        Instant paidAt = request.paidAt() != null ? request.paidAt() : Instant.now();
        // ON_TIME when paid in full or more (early/overpayment); LATE when underpaid
        boolean paidInFull = amountPaid.compareTo(expectedAmountDue) >= 0;
        int ratingDelta = paidInFull ? ON_TIME_RATING_DELTA : LATE_RATING_DELTA;
        RepaymentStatus repaymentStatus = paidInFull ? RepaymentStatus.ON_TIME : RepaymentStatus.LATE;

        RepaymentRecord record = repaymentRepository.create(
            request.loanRequestId(),
            customerId,
            expectedAmountDue,
            amountPaid,
            request.dueDate(),
            paidAt,
            repaymentStatus,
            ratingDelta,
            sanitizeNote(request.note())
        );

        int currentRating = customerProfileRepository.adjustPaymentRating(customerId, ratingDelta)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile not found"));

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
            record.ratingDelta(),
            record.note(),
            record.createdAt()
        );
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private BigDecimal calculateExpectedMonthlyDue(LoanRecord loan) {
        if (loan.termMonths() == null || loan.termMonths() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan term is invalid");
        }

        return loan.amount().divide(
            BigDecimal.valueOf(loan.termMonths()),
            0,
            RoundingMode.HALF_UP
        );
    }
}
