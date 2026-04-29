package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
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
    private static final int ON_TIME_RATING_DELTA = 5;
    private static final int LATE_RATING_DELTA = -8;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final RepaymentRepository repaymentRepository;
    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final LoanContractService loanContractService;
    private final RepaymentScheduleService repaymentScheduleService;

    public RepaymentService(
            RepaymentRepository repaymentRepository,
            LoanRepository loanRepository,
            CustomerProfileRepository customerProfileRepository,
            LoanContractService loanContractService,
            RepaymentScheduleService repaymentScheduleService) {
        this.repaymentRepository = repaymentRepository;
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.loanContractService = loanContractService;
        this.repaymentScheduleService = repaymentScheduleService;
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
        LoanRecord loan = loanRepository.findById(loanRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ vay"));
        if (!loan.customerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản vay không thuộc khách hàng được xác nhận");
        }
        return createForLoan(loan, customerId, amountPaid, effectivePaidAt, sanitizeNote(note));
    }

    public LoanRepaymentSnapshot snapshotForLoan(LoanRecord loan, Long customerId) {
        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản vay chưa có hợp đồng đang hiệu lực");
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
            String note) {
        if (rawAmountPaid == null || rawAmountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán phải lớn hơn 0");
        }
        if (loan.status() != LoanStatus.DISBURSED && loan.status() != LoanStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ có thể thanh toán cho khoản vay đã giải ngân hoặc đang hoạt động");
        }

        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản vay chưa có hợp đồng đang hiệu lực");
        }

        if (customerProfileRepository.findPaymentRatingByUserId(customerId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng hoàn thiện hồ sơ trước khi ghi nhận thanh toán");
        }

        LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(loan, contract, customerId);
        if (snapshot.fullyPaid() || snapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản vay này đã được tất toán");
        }

        BigDecimal amountPaid = rawAmountPaid.setScale(0, RoundingMode.HALF_UP);
        if (amountPaid.compareTo(snapshot.outstandingAmount()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán không được vượt quá dư nợ còn lại");
        }
        if (amountPaid.compareTo(snapshot.currentAmountDue()) != 0
                && amountPaid.compareTo(snapshot.outstandingAmount()) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được xác nhận thanh toán đúng số tiền đến hạn kỳ này hoặc tất toán toàn bộ khoản vay");
        }
        Instant paidAt = effectivePaidAt != null ? effectivePaidAt : Instant.now();

        RepaymentStatus repaymentStatus = paidAt.atZone(SYSTEM_ZONE).toLocalDate().isAfter(snapshot.dueDate())
                ? RepaymentStatus.LATE
                : RepaymentStatus.ON_TIME;
        boolean completedCurrentInstallment = amountPaid.compareTo(snapshot.currentAmountDue()) >= 0;
        int ratingDelta = completedCurrentInstallment
                ? (repaymentStatus == RepaymentStatus.ON_TIME ? ON_TIME_RATING_DELTA : LATE_RATING_DELTA)
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

        int currentRating = customerProfileRepository.adjustPaymentRating(customerId, ratingDelta)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy hồ sơ khách hàng"));

        BigDecimal outstandingAfter = snapshot.outstandingAmount().subtract(amountPaid).max(BigDecimal.ZERO);
        if (outstandingAfter.compareTo(BigDecimal.ZERO) <= 0) {
            loanRepository.updateStatus(loan.id(), LoanStatus.CLOSED);
            loanContractService.closeContract(contract.id());
        } else if (loan.status() == LoanStatus.DISBURSED) {
            loanRepository.updateStatus(loan.id(), LoanStatus.ACTIVE);
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
                record.ratingDelta(),
                record.note(),
                record.createdAt());
    }

    private String sanitizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }
}
