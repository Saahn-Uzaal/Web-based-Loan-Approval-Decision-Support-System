package com.loanapproval.dss.repayment;

import com.loanapproval.dss.auth.UserAccount;
import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.PaymentProofStorageService.StoredPaymentProof;
import com.loanapproval.dss.repayment.dto.PaymentConfirmationItemResponse;
import com.loanapproval.dss.repayment.dto.RepaymentCreateResponse;
import com.loanapproval.dss.repayment.dto.ReviewPaymentConfirmationRequest;
import com.loanapproval.dss.repayment.dto.StaffPaymentConfirmationDetailResponse;
import com.loanapproval.dss.repayment.dto.StaffPaymentConfirmationSummaryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentConfirmationService {

    private final PaymentConfirmationRepository paymentConfirmationRepository;
    private final PaymentProofStorageService paymentProofStorageService;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RepaymentService repaymentService;

    public PaymentConfirmationService(
            PaymentConfirmationRepository paymentConfirmationRepository,
            PaymentProofStorageService paymentProofStorageService,
            LoanRepository loanRepository,
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            RepaymentService repaymentService) {
        this.paymentConfirmationRepository = paymentConfirmationRepository;
        this.paymentProofStorageService = paymentProofStorageService;
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.repaymentService = repaymentService;
    }

    public List<PaymentConfirmationItemResponse> listMine(Long customerId) {
        return paymentConfirmationRepository.findByCustomerId(customerId).stream()
                .map(this::toCustomerItemResponse)
                .toList();
    }

    @Transactional
    public PaymentConfirmationItemResponse create(Long customerId, Long loanRequestId, String note, MultipartFile proofFile) {
        LoanRecord loan = loanRepository.findOwnedById(loanRequestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản vay cần gửi xác nhận"));

        if (loan.status() != LoanStatus.DISBURSED && loan.status() != LoanStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được gửi bill xác nhận cho khoản vay đã giải ngân hoặc đang hoạt động");
        }
        if (paymentConfirmationRepository.existsPendingByLoanRequestAndCustomer(loanRequestId, customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khoản vay này đã có một yêu cầu xác nhận thanh toán đang chờ nhân viên đối chiếu");
        }

        LoanRepaymentSnapshot snapshot = repaymentService.snapshotForLoan(loan, customerId);
        if (snapshot.fullyPaid() || snapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản vay này đã được tất toán");
        }

        StoredPaymentProof storedProof = paymentProofStorageService.store(customerId, proofFile);
        PaymentConfirmationRequestRecord created = paymentConfirmationRepository.create(
                loanRequestId,
                customerId,
                snapshot.currentAmountDue(),
                snapshot.outstandingAmount(),
                snapshot.installmentNumber(),
                snapshot.dueDate(),
                storedProof,
                sanitizeNote(note));

        return toCustomerItemResponse(created);
    }

    public PaymentProofDownload loadProofForCustomer(Long customerId, Long confirmationId) {
        PaymentConfirmationRequestRecord record = paymentConfirmationRepository.findByIdAndCustomerId(confirmationId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu xác nhận thanh toán"));
        return paymentProofStorageService.load(record);
    }

    public List<StaffPaymentConfirmationSummaryResponse> listForStaff(PaymentConfirmationStatus status) {
        return paymentConfirmationRepository.findSummaries(status);
    }

    public StaffPaymentConfirmationDetailResponse getForStaff(Long confirmationId) {
        PaymentConfirmationRequestRecord record = paymentConfirmationRepository.findById(confirmationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu xác nhận thanh toán"));
        return buildStaffDetail(record);
    }

    public PaymentProofDownload loadProofForStaff(Long confirmationId) {
        PaymentConfirmationRequestRecord record = paymentConfirmationRepository.findById(confirmationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu xác nhận thanh toán"));
        return paymentProofStorageService.load(record);
    }

    @Transactional
    public StaffPaymentConfirmationDetailResponse review(
            Long staffUserId,
            Long confirmationId,
            ReviewPaymentConfirmationRequest request) {
        PaymentConfirmationRequestRecord record = paymentConfirmationRepository.findById(confirmationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu xác nhận thanh toán"));

        if (record.status() != PaymentConfirmationStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã được xử lý trước đó");
        }

        Instant reviewedAt = Instant.now();
        if (request.action() == PaymentConfirmationReviewAction.REJECT) {
            String rejectionReason = sanitizeRequired(request.rejectionReason(), "Cần nhập lý do từ chối bill chuyển khoản");
            int updated = paymentConfirmationRepository.markRejected(
                    confirmationId,
                    staffUserId,
                    reviewedAt,
                    sanitizeNote(request.staffNote()),
                    rejectionReason);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã thay đổi trong lúc xử lý");
            }
            return getForStaff(confirmationId);
        }

        BigDecimal confirmedAmount = positiveAmount(request.confirmedAmount(), "Cần nhập số tiền thực nhận hợp lệ");
        Instant confirmedPaidAt = requireValue(request.confirmedPaidAt(), "Cần nhập thời điểm giao dịch trên bill");
        String bankTransactionCode = sanitizeRequired(
                request.bankTransactionCode(),
                "Cần nhập mã giao dịch hoặc mã tham chiếu trên bill");

        LoanRecord loan = loanRepository.findById(record.loanRequestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản vay"));
        LoanRepaymentSnapshot currentSnapshot = repaymentService.snapshotForLoan(loan, record.customerId());
        if (currentSnapshot.fullyPaid() || currentSnapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khoản vay này đã được tất toán, không thể ghi nhận thêm");
        }

        boolean matchesCurrentInstallment = confirmedAmount.compareTo(currentSnapshot.currentAmountDue()) == 0;
        boolean matchesFullSettlement = confirmedAmount.compareTo(currentSnapshot.outstandingAmount()) == 0;
        if (!matchesCurrentInstallment && !matchesFullSettlement) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền xác nhận phải bằng số tiền đến hạn kỳ hiện tại hoặc bằng dư nợ còn lại để tất toán");
        }

        String repaymentNote = buildRepaymentNote(record.id(), bankTransactionCode, request.staffNote());
        RepaymentCreateResponse repayment = repaymentService.createByStaff(
                record.loanRequestId(),
                record.customerId(),
                confirmedAmount,
                confirmedPaidAt,
                repaymentNote);

        int updated = paymentConfirmationRepository.markConfirmed(
                confirmationId,
                staffUserId,
                reviewedAt,
                confirmedAmount,
                confirmedPaidAt,
                bankTransactionCode,
                sanitizeNote(request.staffNote()),
                repayment.repayment().id());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã thay đổi trong lúc xử lý");
        }

        return getForStaff(confirmationId);
    }

    private StaffPaymentConfirmationDetailResponse buildStaffDetail(PaymentConfirmationRequestRecord record) {
        LoanRecord loan = loanRepository.findById(record.loanRequestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản vay"));
        LoanRepaymentSnapshot currentSnapshot = repaymentService.trySnapshotForLoan(loan, record.customerId());
        UserAccount customer = userRepository.findById(record.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));
        CustomerProfile profile = customerProfileRepository.findByUserId(record.customerId()).orElse(null);
        String reviewedByEmail = null;
        if (record.reviewedBy() != null) {
            reviewedByEmail = userRepository.findById(record.reviewedBy()).map(UserAccount::email).orElse(null);
        }

        RepaymentStatus repaymentStatus = null;
        Integer ratingDelta = null;
        if (record.status() == PaymentConfirmationStatus.CONFIRMED && record.loanRequestId() != null) {
            repaymentStatus = record.confirmedPaidAt() != null && record.expectedDueDate() != null
                    && record.confirmedPaidAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(record.expectedDueDate())
                    ? RepaymentStatus.LATE
                    : RepaymentStatus.ON_TIME;
            ratingDelta = repaymentStatus == RepaymentStatus.ON_TIME ? 5 : -8;
        }

        return new StaffPaymentConfirmationDetailResponse(
                record.id(),
                record.loanRequestId(),
                loan.status(),
                record.customerId(),
                customer.email(),
                profile != null ? profile.fullName() : null,
                record.expectedAmountDue(),
                record.expectedOutstandingAmount(),
                record.expectedInstallmentNumber(),
                record.expectedDueDate(),
                currentSnapshot != null ? currentSnapshot.currentAmountDue() : BigDecimal.ZERO,
                currentSnapshot != null ? currentSnapshot.outstandingAmount() : BigDecimal.ZERO,
                currentSnapshot != null ? currentSnapshot.installmentNumber() : null,
                currentSnapshot != null ? currentSnapshot.dueDate() : null,
                record.proofOriginalFileName(),
                record.proofContentType(),
                record.proofFileSize(),
                record.customerNote(),
                record.status(),
                record.confirmedAmount(),
                record.confirmedPaidAt(),
                record.bankTransactionCode(),
                repaymentStatus,
                ratingDelta,
                record.staffNote(),
                record.rejectionReason(),
                reviewedByEmail,
                record.reviewedAt(),
                record.createdAt());
    }

    private PaymentConfirmationItemResponse toCustomerItemResponse(PaymentConfirmationRequestRecord record) {
        RepaymentStatus repaymentStatus = null;
        Integer ratingDelta = null;
        if (record.status() == PaymentConfirmationStatus.CONFIRMED && record.confirmedPaidAt() != null) {
            repaymentStatus = record.confirmedPaidAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(record.expectedDueDate())
                    ? RepaymentStatus.LATE
                    : RepaymentStatus.ON_TIME;
            ratingDelta = repaymentStatus == RepaymentStatus.ON_TIME ? 5 : -8;
        }

        return new PaymentConfirmationItemResponse(
                record.id(),
                record.loanRequestId(),
                record.expectedAmountDue(),
                record.expectedOutstandingAmount(),
                record.expectedInstallmentNumber(),
                record.expectedDueDate(),
                record.proofOriginalFileName(),
                record.proofFileSize(),
                record.customerNote(),
                record.status(),
                record.confirmedAmount(),
                record.confirmedPaidAt(),
                record.bankTransactionCode(),
                repaymentStatus,
                ratingDelta,
                record.staffNote(),
                record.rejectionReason(),
                record.reviewedAt(),
                record.createdAt());
    }

    private String buildRepaymentNote(Long confirmationId, String bankTransactionCode, String staffNote) {
        StringBuilder builder = new StringBuilder("Xác nhận bill #").append(confirmationId);
        builder.append(" - GD ").append(bankTransactionCode);
        if (staffNote != null && !staffNote.isBlank()) {
            builder.append(" - ").append(staffNote.trim());
        }
        String note = builder.toString();
        return note.length() > 255 ? note.substring(0, 255) : note;
    }

    private String sanitizeNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sanitizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private BigDecimal positiveAmount(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.setScale(0, java.math.RoundingMode.HALF_UP);
    }
}

