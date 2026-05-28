package com.loanapproval.dss.repayment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.repayment.dto.ReviewPaymentConfirmationRequest;
import com.loanapproval.dss.shared.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock
    private PaymentConfirmationRepository paymentConfirmationRepository;

    @Mock
    private PaymentProofStorageService paymentProofStorageService;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.loanapproval.dss.profile.CustomerProfileRepository customerProfileRepository;

    @Mock
    private RepaymentService repaymentService;

    @Mock
    private RepaymentRepository repaymentRepository;

    @Mock
    private LoanDelinquencyRepository loanDelinquencyRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentConfirmationService paymentConfirmationService;

    @Test
    void shouldCancelPendingConfirmationByCustomer() {
        Long customerId = 10L;
        Long confirmationId = 100L;
        PaymentConfirmationRequestRecord pending = confirmationRecord(
                confirmationId,
                customerId,
                200L,
                PaymentConfirmationStatus.PENDING_REVIEW,
                "proof-old.png");
        PaymentConfirmationRequestRecord cancelled = confirmationRecord(
                confirmationId,
                customerId,
                200L,
                PaymentConfirmationStatus.CANCELLED_BY_CUSTOMER,
                "proof-old.png");

        when(paymentConfirmationRepository.findByIdAndCustomerId(confirmationId, customerId))
                .thenReturn(Optional.of(pending), Optional.of(cancelled));
        when(paymentConfirmationRepository.markCancelledByCustomer(confirmationId, customerId)).thenReturn(1);

        var response = paymentConfirmationService.cancelByCustomer(customerId, confirmationId);

        assertThat(response.status()).isEqualTo(PaymentConfirmationStatus.CANCELLED_BY_CUSTOMER);
        verify(paymentConfirmationRepository).markCancelledByCustomer(confirmationId, customerId);
    }

    @Test
    void shouldReplacePendingConfirmationByCustomer() {
        Long customerId = 10L;
        Long confirmationId = 101L;
        Long loanRequestId = 201L;
        PaymentConfirmationRequestRecord pending = confirmationRecord(
                confirmationId,
                customerId,
                loanRequestId,
                PaymentConfirmationStatus.PENDING_REVIEW,
                "proof-old.png");
        LoanRecord loan = loanRecord(loanRequestId, customerId, LoanStatus.ACTIVE);
        LoanRepaymentSnapshot snapshot = new LoanRepaymentSnapshot(
                loanRequestId,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.of(2026, 5, 15),
                false);
        PaymentProofStorageService.StoredPaymentProof storedProof = new PaymentProofStorageService.StoredPaymentProof(
                "proof-new.png",
                "stored-proof-new.png",
                "image/png",
                1234L,
                Instant.now());
        PaymentConfirmationRequestRecord replacement = confirmationRecord(
                102L,
                customerId,
                loanRequestId,
                PaymentConfirmationStatus.PENDING_REVIEW,
                "proof-new.png");
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "proof-new.png",
                "image/png",
                new byte[] {1, 2, 3});

        when(paymentConfirmationRepository.findByIdAndCustomerId(confirmationId, customerId))
                .thenReturn(Optional.of(pending));
        when(loanRepository.findOwnedById(loanRequestId, customerId)).thenReturn(Optional.of(loan));
        when(repaymentService.snapshotForLoan(loan, customerId)).thenReturn(snapshot);
        when(paymentProofStorageService.store(customerId, proofFile)).thenReturn(storedProof);
        when(paymentConfirmationRepository.markCancelledByCustomer(confirmationId, customerId)).thenReturn(1);
        when(paymentConfirmationRepository.create(
                        eq(loanRequestId),
                        eq(customerId),
                        eq(snapshot.currentAmountDue()),
                        eq(snapshot.outstandingAmount()),
                        eq(snapshot.installmentNumber()),
                        eq(snapshot.dueDate()),
                        eq(storedProof),
                        eq("biên lai mới"),
                        eq(null)))
                .thenReturn(replacement);

        var response = paymentConfirmationService.replaceByCustomer(customerId, confirmationId, "biên lai mới", proofFile);

        assertThat(response.id()).isEqualTo(102L);
        assertThat(response.status()).isEqualTo(PaymentConfirmationStatus.PENDING_REVIEW);
        verify(paymentConfirmationRepository).markCancelledByCustomer(confirmationId, customerId);
    }

    @Test
    void shouldNotifyStaffWhenCustomerSubmitsPaymentConfirmation() {
        Long customerId = 10L;
        Long loanRequestId = 205L;
        Long assignedStaffUserId = 77L;
        LoanRecord loan = loanRecord(loanRequestId, customerId, LoanStatus.ACTIVE);
        LoanRepaymentSnapshot snapshot = new LoanRepaymentSnapshot(
                loanRequestId,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.of(2026, 5, 15),
                false);
        PaymentProofStorageService.StoredPaymentProof storedProof = new PaymentProofStorageService.StoredPaymentProof(
                "proof-new.png",
                "stored-proof-new.png",
                "image/png",
                1234L,
                Instant.now());
        PaymentConfirmationRequestRecord created = confirmationRecord(
                105L,
                customerId,
                loanRequestId,
                PaymentConfirmationStatus.PENDING_REVIEW,
                "proof-new.png");
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "proof-new.png",
                "image/png",
                new byte[] {1, 2, 3});

        when(loanRepository.findOwnedById(loanRequestId, customerId)).thenReturn(Optional.of(loan));
        when(repaymentService.snapshotForLoan(loan, customerId)).thenReturn(snapshot);
        when(paymentProofStorageService.store(customerId, proofFile)).thenReturn(storedProof);
        when(paymentConfirmationRepository.create(
                        eq(loanRequestId),
                        eq(customerId),
                        eq(snapshot.currentAmountDue()),
                        eq(snapshot.outstandingAmount()),
                        eq(snapshot.installmentNumber()),
                        eq(snapshot.dueDate()),
                        eq(storedProof),
                        eq("đã chuyển khoản"),
                        eq(null)))
                .thenReturn(created);
        when(loanRepository.findAssignedStaffUserId(loanRequestId)).thenReturn(Optional.of(assignedStaffUserId));

        var response = paymentConfirmationService.create(
                customerId,
                loanRequestId,
                "đã chuyển khoản",
                proofFile,
                null);

        assertThat(response.id()).isEqualTo(105L);
        verify(notificationService).notifyStaffPaymentConfirmationSubmitted(
                105L,
                loanRequestId,
                customerId,
                assignedStaffUserId,
                snapshot.installmentNumber(),
                snapshot.currentAmountDue());
    }

    @Test
    void shouldRejectCancelWhenConfirmationIsAlreadyReviewed() {
        Long customerId = 10L;
        Long confirmationId = 103L;
        PaymentConfirmationRequestRecord reviewed = confirmationRecord(
                confirmationId,
                customerId,
                203L,
                PaymentConfirmationStatus.CONFIRMED,
                "proof.png");

        when(paymentConfirmationRepository.findByIdAndCustomerId(confirmationId, customerId))
                .thenReturn(Optional.of(reviewed));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentConfirmationService.cancelByCustomer(customerId, confirmationId));

        assertThat(exception.getReason()).contains("Chỉ có thể hủy hoặc thay biên lai");
    }

    @Test
    void shouldExposeLatePenaltyDeltaWhenRepaymentRecordHasZeroDelta() {
        Long customerId = 10L;
        Long confirmationId = 104L;
        Long loanRequestId = 204L;
        PaymentConfirmationRequestRecord confirmed = new PaymentConfirmationRequestRecord(
                confirmationId,
                loanRequestId,
                customerId,
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(45_000_000),
                3,
                LocalDate.of(2026, 5, 15),
                "proof.png",
                "stored-proof.png",
                "image/png",
                1234L,
                null,
                null,
                PaymentConfirmationStatus.CONFIRMED,
                8L,
                Instant.now(),
                BigDecimal.valueOf(4_500_000),
                Instant.parse("2026-05-16T02:00:00Z"),
                "TXN-104",
                null,
                null,
                900L,
                Instant.now(),
                Instant.now());
        RepaymentRecord repaymentRecord = new RepaymentRecord(
                900L,
                loanRequestId,
                customerId,
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                LocalDate.of(2026, 5, 15),
                Instant.parse("2026-05-16T02:00:00Z"),
                RepaymentStatus.LATE,
                0,
                "ghi nhận trễ hạn",
                Instant.now());
        LoanDelinquencyRecord delinquencyRecord = new LoanDelinquencyRecord(
                700L,
                loanRequestId,
                customerId,
                3,
                LocalDate.of(2026, 5, 15),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.ZERO,
                1,
                1,
                -6,
                BigDecimal.ZERO,
                LoanDelinquencyStatus.CURED,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now());

        when(paymentConfirmationRepository.findByCustomerId(customerId)).thenReturn(java.util.List.of(confirmed));
        when(repaymentRepository.findByIdAndCustomerId(900L, customerId)).thenReturn(Optional.of(repaymentRecord));
        when(loanDelinquencyRepository.findByLoanAndInstallment(
                loanRequestId,
                3,
                LocalDate.of(2026, 5, 15))).thenReturn(Optional.of(delinquencyRecord));

        var items = paymentConfirmationService.listMine(customerId);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).repaymentStatus()).isEqualTo(RepaymentStatus.LATE);
        assertThat(items.get(0).ratingDelta()).isEqualTo(-6);
    }

    @Test
    void shouldNotifyCustomerWhenStaffRejectsPaymentConfirmation() {
        Long customerId = 10L;
        Long confirmationId = 106L;
        Long loanRequestId = 206L;
        Long staffUserId = 8L;
        PaymentConfirmationRequestRecord pending = confirmationRecord(
                confirmationId,
                customerId,
                loanRequestId,
                PaymentConfirmationStatus.PENDING_REVIEW,
                "proof.png");
        PaymentConfirmationRequestRecord rejected = new PaymentConfirmationRequestRecord(
                confirmationId,
                loanRequestId,
                customerId,
                pending.expectedAmountDue(),
                pending.expectedOutstandingAmount(),
                pending.expectedInstallmentNumber(),
                pending.expectedDueDate(),
                pending.proofOriginalFileName(),
                pending.proofStorageName(),
                pending.proofContentType(),
                pending.proofFileSize(),
                pending.customerNote(),
                null,
                PaymentConfirmationStatus.REJECTED,
                staffUserId,
                Instant.now(),
                null,
                null,
                null,
                "thiếu thông tin",
                "bill mờ",
                null,
                pending.createdAt(),
                Instant.now());
        LoanRecord loan = loanRecord(loanRequestId, customerId, LoanStatus.ACTIVE);
        LoanRepaymentSnapshot snapshot = new LoanRepaymentSnapshot(
                loanRequestId,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(5_000_000),
                BigDecimal.valueOf(45_000_000),
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(4_500_000),
                3,
                LocalDate.of(2026, 5, 15),
                false);

        when(paymentConfirmationRepository.findById(confirmationId)).thenReturn(Optional.of(pending), Optional.of(rejected));
        when(paymentConfirmationRepository.markRejected(
                        eq(confirmationId),
                        eq(staffUserId),
                        org.mockito.ArgumentMatchers.any(Instant.class),
                        eq("thiếu thông tin"),
                        eq("bill mờ")))
                .thenReturn(1);
        when(loanRepository.findById(loanRequestId)).thenReturn(Optional.of(loan));
        when(repaymentService.trySnapshotForLoan(loan, customerId)).thenReturn(snapshot);
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(new com.loanapproval.dss.auth.UserAccount(customerId, "customer@example.com", "hash", Role.CUSTOMER)));

        var response = paymentConfirmationService.review(
                staffUserId,
                confirmationId,
                new ReviewPaymentConfirmationRequest(
                        PaymentConfirmationReviewAction.REJECT,
                        null,
                        null,
                        null,
                        "thiếu thông tin",
                        "bill mờ"));

        assertThat(response.status()).isEqualTo(PaymentConfirmationStatus.REJECTED);
        verify(notificationService).notifyCustomerPaymentRejected(
                confirmationId,
                loanRequestId,
                customerId,
                staffUserId,
                "bill mờ");
    }

    private PaymentConfirmationRequestRecord confirmationRecord(
            Long id,
            Long customerId,
            Long loanRequestId,
            PaymentConfirmationStatus status,
            String proofFileName) {
        Instant now = Instant.now();
        return new PaymentConfirmationRequestRecord(
                id,
                loanRequestId,
                customerId,
                BigDecimal.valueOf(4_500_000),
                BigDecimal.valueOf(45_000_000),
                3,
                LocalDate.of(2026, 5, 15),
                proofFileName,
                "stored-" + proofFileName,
                "image/png",
                1234L,
                "ghi chú cũ",
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    private LoanRecord loanRecord(Long loanRequestId, Long customerId, LoanStatus status) {
        Instant now = Instant.now();
        return new LoanRecord(
                loanRequestId,
                customerId,
                LoanType.UNSECURED,
                BigDecimal.valueOf(50_000_000),
                12,
                LoanPurpose.PERSONAL,
                null,
                null,
                status,
                null,
                BigDecimal.valueOf(50_000_000),
                BigDecimal.valueOf(50_000_000),
                12,
                BigDecimal.valueOf(0.12),
                BigDecimal.valueOf(4_500_000),
                "TEST_POLICY",
                null,
                now,
                now);
    }
}
