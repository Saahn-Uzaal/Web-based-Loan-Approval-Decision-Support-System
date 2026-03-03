package com.loanapproval.dss.repayment;

import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.dto.CreateRepaymentRequest;
import com.loanapproval.dss.repayment.dto.RepaymentCreateResponse;
import com.loanapproval.dss.repayment.dto.RepaymentHistoryResponse;
import com.loanapproval.dss.repayment.dto.RepaymentItemResponse;
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

        BigDecimal expectedAmountDue = calculateExpectedMonthlyDue(loan);
        if (request.amountPaid().compareTo(expectedAmountDue) > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Amount paid cannot be greater than amount due"
            );
        }

        Instant paidAt = request.paidAt() != null ? request.paidAt() : Instant.now();
        boolean matchedDueAmount = request.amountPaid().compareTo(expectedAmountDue) == 0;
        int ratingDelta = matchedDueAmount ? ON_TIME_RATING_DELTA : LATE_RATING_DELTA;
        RepaymentStatus repaymentStatus = matchedDueAmount ? RepaymentStatus.ON_TIME : RepaymentStatus.LATE;

        RepaymentRecord record = repaymentRepository.create(
            request.loanRequestId(),
            customerId,
            expectedAmountDue,
            request.amountPaid(),
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
            2,
            RoundingMode.HALF_UP
        );
    }
}
