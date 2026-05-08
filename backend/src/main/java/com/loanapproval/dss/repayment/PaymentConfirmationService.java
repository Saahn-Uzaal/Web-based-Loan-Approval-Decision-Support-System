package com.loanapproval.dss.repayment;

import com.loanapproval.dss.auth.UserAccount;
import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.notification.NotificationService;
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
    private final RepaymentRepository repaymentRepository;
    private final LoanDelinquencyRepository loanDelinquencyRepository;
    private final NotificationService notificationService;

    public PaymentConfirmationService(
            PaymentConfirmationRepository paymentConfirmationRepository,
            PaymentProofStorageService paymentProofStorageService,
            LoanRepository loanRepository,
            UserRepository userRepository,
            CustomerProfileRepository customerProfileRepository,
            RepaymentService repaymentService,
            RepaymentRepository repaymentRepository,
            LoanDelinquencyRepository loanDelinquencyRepository,
            NotificationService notificationService) {
        this.paymentConfirmationRepository = paymentConfirmationRepository;
        this.paymentProofStorageService = paymentProofStorageService;
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.repaymentService = repaymentService;
        this.repaymentRepository = repaymentRepository;
        this.loanDelinquencyRepository = loanDelinquencyRepository;
        this.notificationService = notificationService;
    }

    public List<PaymentConfirmationItemResponse> listMine(Long customerId) {
        return paymentConfirmationRepository.findByCustomerId(customerId).stream()
                .map(this::toCustomerItemResponse)
                .toList();
    }

    @Transactional
    public PaymentConfirmationItemResponse create(
            Long customerId,
            Long loanRequestId,
            String note,
            MultipartFile proofFile,
            String idempotencyKey) {
        String normalizedIdempotencyKey = sanitizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            PaymentConfirmationRequestRecord existing = paymentConfirmationRepository
                    .findByCustomerIdAndIdempotencyKey(customerId, normalizedIdempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return toCustomerItemResponse(existing);
            }
        }

        LoanRecord loan = requireLoanEligibleForPaymentConfirmation(customerId, loanRequestId);
        if (paymentConfirmationRepository.existsPendingByLoanRequestAndCustomer(loanRequestId, customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khoản vay này đã có một yêu cầu xác nhận thanh toán đang chờ nhân viên đối chiếu");
        }

        LoanRepaymentSnapshot snapshot = requireOpenSnapshot(loan, customerId);
        StoredPaymentProof storedProof = paymentProofStorageService.store(customerId, proofFile);
        return createPendingConfirmation(
                customerId,
                loanRequestId,
                note,
                snapshot,
                storedProof,
                normalizedIdempotencyKey);
    }

    @Transactional
    public PaymentConfirmationItemResponse cancelByCustomer(Long customerId, Long confirmationId) {
        PaymentConfirmationRequestRecord record = getPendingCustomerConfirmation(customerId, confirmationId);
        int updated = paymentConfirmationRepository.markCancelledByCustomer(confirmationId, customerId);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yêu cầu xác nhận thanh toán đã thay đổi trạng thái trong lúc xử lý");
        }
        return paymentConfirmationRepository.findByIdAndCustomerId(record.id(), customerId)
                .map(this::toCustomerItemResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy yêu cầu xác nhận thanh toán"));
    }

    @Transactional
    public PaymentConfirmationItemResponse replaceByCustomer(
            Long customerId,
            Long confirmationId,
            String note,
            MultipartFile proofFile) {
        PaymentConfirmationRequestRecord existing = getPendingCustomerConfirmation(customerId, confirmationId);
        LoanRecord loan = requireLoanEligibleForPaymentConfirmation(customerId, existing.loanRequestId());
        LoanRepaymentSnapshot snapshot = requireOpenSnapshot(loan, customerId);
        StoredPaymentProof storedProof = paymentProofStorageService.store(customerId, proofFile);

        int updated = paymentConfirmationRepository.markCancelledByCustomer(confirmationId, customerId);
        if (updated == 0) {
            paymentProofStorageService.delete(customerId, storedProof.storageName());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yêu cầu xác nhận thanh toán đã thay đổi trạng thái trong lúc xử lý");
        }

        try {
            return createPendingConfirmation(customerId, existing.loanRequestId(), note, snapshot, storedProof, null);
        } catch (RuntimeException ex) {
            paymentProofStorageService.delete(customerId, storedProof.storageName());
            throw ex;
        }
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
            notificationService.notifyCustomerPaymentRejected(
                    confirmationId,
                    record.loanRequestId(),
                    record.customerId(),
                    staffUserId,
                    rejectionReason);
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

        if (confirmedAmount.compareTo(currentSnapshot.outstandingAmount()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Confirmed amount cannot exceed the remaining outstanding balance");
        }
        String repaymentNote = buildRepaymentNote(record.id(), bankTransactionCode, request.staffNote());
        RepaymentCreateResponse repayment = repaymentService.createByStaff(
                record.loanRequestId(),
                record.customerId(),
                confirmedAmount,
                confirmedPaidAt,
                repaymentNote,
                staffUserId);

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
        notificationService.notifyCustomerPaymentConfirmed(
                confirmationId,
                record.loanRequestId(),
                record.customerId(),
                staffUserId,
                confirmedAmount);

        return getForStaff(confirmationId);
    }

    private PaymentConfirmationItemResponse createPendingConfirmation(
            Long customerId,
            Long loanRequestId,
            String note,
            LoanRepaymentSnapshot snapshot,
            StoredPaymentProof storedProof,
            String idempotencyKey) {
        PaymentConfirmationRequestRecord created = paymentConfirmationRepository.create(
                loanRequestId,
                customerId,
                snapshot.currentAmountDue(),
                snapshot.outstandingAmount(),
                snapshot.installmentNumber(),
                snapshot.dueDate(),
                storedProof,
                sanitizeNote(note),
                idempotencyKey);
        notificationService.notifyStaffPaymentConfirmationSubmitted(
                created.id(),
                loanRequestId,
                customerId,
                loanRepository.findAssignedStaffUserId(loanRequestId).orElse(null),
                snapshot.installmentNumber(),
                snapshot.currentAmountDue());
        return toCustomerItemResponse(created);
    }

    private LoanRecord requireLoanEligibleForPaymentConfirmation(Long customerId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findOwnedById(loanRequestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khoản vay cần gửi xác nhận"));

        if (loan.status() != LoanStatus.ACTIVE
                && loan.status() != LoanStatus.OVERDUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ các khoản vay đang hoạt động hoặc đang quá hạn mới được gửi xác nhận thanh toán");
        }
        return loan;
    }

    private LoanRepaymentSnapshot requireOpenSnapshot(LoanRecord loan, Long customerId) {
        LoanRepaymentSnapshot snapshot = repaymentService.snapshotForLoan(loan, customerId);
        if (snapshot.fullyPaid() || snapshot.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản vay này đã được tất toán");
        }
        return snapshot;
    }

    private PaymentConfirmationRequestRecord getPendingCustomerConfirmation(Long customerId, Long confirmationId) {
        PaymentConfirmationRequestRecord record = paymentConfirmationRepository.findByIdAndCustomerId(confirmationId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy yêu cầu xác nhận thanh toán"));
        if (record.status() != PaymentConfirmationStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể hủy hoặc thay biên lai khi yêu cầu vẫn đang chờ nhân viên đối chiếu");
        }
        return record;
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

        RepaymentRecord repaymentRecord = findRepaymentRecord(record);
        RepaymentStatus repaymentStatus = repaymentRecord != null ? repaymentRecord.repaymentStatus() : null;
        Integer ratingDelta = resolveVisibleRatingDelta(record, repaymentRecord);

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
        RepaymentRecord repaymentRecord = findRepaymentRecord(record);
        RepaymentStatus repaymentStatus = repaymentRecord != null ? repaymentRecord.repaymentStatus() : null;
        Integer ratingDelta = resolveVisibleRatingDelta(record, repaymentRecord);

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

    private RepaymentRecord findRepaymentRecord(PaymentConfirmationRequestRecord record) {
        if (record.repaymentId() == null) {
            return null;
        }
        return repaymentRepository.findByIdAndCustomerId(record.repaymentId(), record.customerId()).orElse(null);
    }

    private Integer resolveVisibleRatingDelta(
            PaymentConfirmationRequestRecord record,
            RepaymentRecord repaymentRecord) {
        if (repaymentRecord == null) {
            return null;
        }
        Integer repaymentDelta = repaymentRecord.ratingDelta();
        if (repaymentRecord.repaymentStatus() != RepaymentStatus.LATE) {
            return repaymentDelta;
        }
        if (repaymentDelta != null && repaymentDelta != 0) {
            return repaymentDelta;
        }
        if (record.expectedInstallmentNumber() == null || record.expectedDueDate() == null) {
            return RepaymentRatingPolicy.firstLatePenalty();
        }
        LoanDelinquencyRecord delinquency = loanDelinquencyRepository
                .findByLoanAndInstallment(
                        record.loanRequestId(),
                        record.expectedInstallmentNumber(),
                        record.expectedDueDate())
                .orElse(null);
        if (delinquency != null
                && delinquency.totalRatingDelta() != null
                && delinquency.totalRatingDelta() < 0) {
            return delinquency.totalRatingDelta();
        }
        return RepaymentRatingPolicy.firstLatePenalty();
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

    private String sanitizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
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
