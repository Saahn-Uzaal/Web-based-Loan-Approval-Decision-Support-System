package com.loanapproval.dss.notification;

import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.dto.NotificationFeedResponse;
import com.loanapproval.dss.notification.dto.NotificationResponse;
import com.loanapproval.dss.shared.Role;
import com.loanapproval.dss.verification.VerificationStatus;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VIETNAM_LOCALE = Locale.forLanguageTag("vi-VN");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
        NotificationRepository notificationRepository,
        UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public NotificationFeedResponse getFeed(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<NotificationResponse> items = notificationRepository.findLatestByRecipientUserId(userId, safeLimit).stream()
            .map(this::toResponse)
            .toList();
        long unreadCount = notificationRepository.countUnreadByRecipientUserId(userId);
        return new NotificationFeedResponse(items, unreadCount);
    }

    public void markAsRead(Long userId, Long notificationId) {
        int updated = notificationRepository.markAsRead(userId, notificationId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo");
        }
    }

    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    public void notifyStaffInformationReviewSubmitted(Long customerId) {
        createForStaff(
            customerId,
            NotificationCategory.INFORMATION_REVIEW_SUBMITTED,
            "Yêu cầu xác minh thông tin mới",
            "Khách hàng vừa gửi lại hồ sơ thông tin để nhân viên đối chiếu và phê duyệt.",
            "/staff/information-verifications/" + customerId
        );
    }

    public void notifyCustomerInformationReviewCompleted(
        Long customerId,
        Long staffUserId,
        VerificationStatus status,
        String reason
    ) {
        String title = status == VerificationStatus.PASSED
            ? "Hồ sơ thông tin đã được chấp thuận"
            : "Hồ sơ thông tin cần bổ sung";
        String message = status == VerificationStatus.PASSED
            ? "Nhân viên đã xác minh xong hồ sơ thông tin của bạn. Bạn có thể tiếp tục tạo hồ sơ vay."
            : reason == null || reason.isBlank()
                ? "Nhân viên đã từ chối hồ sơ thông tin. Vui lòng cập nhật lại và gửi duyệt."
                : "Nhân viên đã từ chối hồ sơ thông tin: " + reason;

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.INFORMATION_REVIEW_COMPLETED,
            title,
            message,
            "/customer/profile"
        );
    }

    public void notifyStaffLoanApplicationSubmitted(Long loanRequestId, Long customerId, LoanType loanType) {
        String title = loanType == LoanType.SECURED
            ? "Hồ sơ vay thế chấp mới cần thẩm định"
            : "Hồ sơ vay tín chấp mới cần thẩm định";
        String message = loanType == LoanType.SECURED
            ? "Khách hàng vừa gửi hồ sơ vay thế chấp. Nhân viên cần kiểm tra và đặt lịch hẹn gặp mặt."
            : "Khách hàng vừa gửi hồ sơ vay tín chấp. Nhân viên cần thẩm định và ra quyết định.";

        createForStaff(
            customerId,
            NotificationCategory.LOAN_APPLICATION_SUBMITTED,
            title,
            message,
            "/staff/requests/" + loanRequestId
        );
    }

    public void notifyCustomerLoanDecisionUpdated(
        Long loanRequestId,
        Long customerId,
        Long actorUserId,
        LoanType loanType,
        LoanStatus status,
        String reason,
        boolean automated
    ) {
        String loanLabel = loanType == LoanType.SECURED ? "vay thế chấp" : "vay tín chấp";
        String title;
        String message;

        if (status == LoanStatus.REJECTED) {
            title = "Hồ sơ " + loanLabel + " đã bị từ chối";
            message = automated
                ? "Hồ sơ " + loanLabel + " #" + loanRequestId + " đã bị từ chối theo kết quả đánh giá của hệ thống."
                : "Nhân viên đã từ chối hồ sơ " + loanLabel + " #" + loanRequestId + ".";
            if (reason != null && !reason.isBlank()) {
                message = message + " Lý do: " + reason;
            }
        } else if (status == LoanStatus.NEEDS_MORE_INFO) {
            title = "Hồ sơ " + loanLabel + " cần bổ sung";
            message = "Nhân viên yêu cầu bổ sung hồ sơ vay #" + loanRequestId + ".";
            if (reason != null && !reason.isBlank()) {
                message = message + " Nội dung: " + reason;
            }
        } else if (status == LoanStatus.APPOINTMENT_SCHEDULED) {
            title = "Hồ sơ vay thế chấp đã được duyệt sơ bộ";
            message = "Hồ sơ vay thế chấp #" + loanRequestId
                + " đã qua bước xét duyệt ban đầu và chuyển sang giai đoạn hẹn gặp mặt, thẩm định tài sản.";
        } else if (status == LoanStatus.APPROVED) {
            title = "Hồ sơ " + loanLabel + " đã được phê duyệt";
            message = automated
                ? "Hồ sơ " + loanLabel + " #" + loanRequestId + " đã được hệ thống phê duyệt."
                : "Nhân viên đã phê duyệt hồ sơ " + loanLabel + " #" + loanRequestId + ".";
        } else {
            return;
        }

        createForRecipients(
            List.of(customerId),
            actorUserId,
            NotificationCategory.LOAN_DECISION_UPDATED,
            title,
            message,
            "/customer/loans/" + loanRequestId
        );
    }

    public void notifyCustomerAppointmentScheduled(
        Long loanRequestId,
        Long customerId,
        Long staffUserId,
        Instant scheduledAt,
        String location
    ) {
        StringBuilder message = new StringBuilder("Nhân viên đã đặt lịch hẹn cho hồ sơ vay #")
            .append(loanRequestId)
            .append(" vào ")
            .append(formatDateTime(scheduledAt));
        if (location != null && !location.isBlank()) {
            message.append(" tại ").append(location.trim());
        }
        message.append(".");

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.APPOINTMENT_SCHEDULED,
            "Đã có lịch hẹn gặp mặt mới",
            message.toString(),
            "/customer/loans/" + loanRequestId
        );
    }

    public void notifyCustomerContractCreated(
        Long loanRequestId,
        Long customerId,
        Long staffUserId,
        LoanType loanType
    ) {
        String message = loanType == LoanType.SECURED
            ? "Hợp đồng vay thế chấp cho hồ sơ #" + loanRequestId + " đã được tạo sau khi hoàn tất thủ tục."
            : "Hợp đồng vay cho hồ sơ #" + loanRequestId + " đã được tạo và sẵn sàng để bạn theo dõi.";

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.CONTRACT_CREATED,
            "Hợp đồng vay đã được tạo",
            message,
            "/customer/loans/" + loanRequestId
        );
    }

    public void notifyCustomerLoanDisbursed(
        Long loanRequestId,
        Long customerId,
        Long staffUserId,
        BigDecimal disbursedAmount
    ) {
        String message = "Khoản vay #" + loanRequestId
            + " đã được giải ngân thành công và chính thức có hiệu lực.";
        if (disbursedAmount != null && disbursedAmount.compareTo(BigDecimal.ZERO) > 0) {
            message = message + " Số tiền giải ngân: " + formatMoney(disbursedAmount) + ".";
        }

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.LOAN_DISBURSED,
            "Khoản vay đã được giải ngân",
            message,
            "/customer/loans/" + loanRequestId
        );
    }

    public void notifyStaffLoanContractAccepted(
        Long loanRequestId,
        Long customerId,
        Long assignedStaffUserId,
        LoanType loanType
    ) {
        if (assignedStaffUserId == null) {
            return;
        }

        String loanLabel = loanType == LoanType.SECURED ? "vay thế chấp" : "vay tín chấp";
        createForRecipient(
            assignedStaffUserId,
            customerId,
            NotificationCategory.LOAN_CONTRACT_ACCEPTED,
            "Khách hàng đã chấp nhận hợp đồng vay",
            "Khách hàng vừa chấp nhận điều khoản cho hồ sơ " + loanLabel + " #" + loanRequestId
                + ". Bạn có thể vào hệ thống để thực hiện bước giải ngân tiếp theo.",
            "/staff/requests/" + loanRequestId
        );
    }

    public void notifyStaffLoanWithdrawn(
        Long loanRequestId,
        Long customerId,
        Long assignedStaffUserId,
        LoanType loanType
    ) {
        if (assignedStaffUserId == null) {
            return;
        }

        String loanLabel = loanType == LoanType.SECURED ? "vay thế chấp" : "vay tín chấp";
        createForRecipient(
            assignedStaffUserId,
            customerId,
            NotificationCategory.LOAN_WITHDRAWN,
            "Khách hàng đã rút hồ sơ vay",
            "Khách hàng vừa rút hồ sơ " + loanLabel + " #" + loanRequestId
                + ". Bạn nên dừng các bước thẩm định hoặc chuẩn bị hồ sơ liên quan.",
            "/staff/requests/" + loanRequestId
        );
    }

    public void notifyStaffPaymentConfirmationSubmitted(
        Long confirmationId,
        Long loanRequestId,
        Long customerId,
        Long assignedStaffUserId,
        Integer installmentNumber,
        BigDecimal expectedAmountDue
    ) {
        StringBuilder message = new StringBuilder("Khách hàng vừa gửi bill xác nhận thanh toán cho khoản vay #")
            .append(loanRequestId);
        if (installmentNumber != null) {
            message.append(", kỳ #").append(installmentNumber);
        }
        if (expectedAmountDue != null && expectedAmountDue.compareTo(BigDecimal.ZERO) > 0) {
            message.append(". Số tiền kỳ vọng: ").append(formatMoney(expectedAmountDue));
        }
        message.append(". Vui lòng vào hệ thống để đối soát.");

        createForAssignedStaffOrStaffRole(
            assignedStaffUserId,
            customerId,
            NotificationCategory.PAYMENT_CONFIRMATION_SUBMITTED,
            "Có yêu cầu xác nhận thanh toán mới",
            message.toString(),
            "/staff/payment-confirmations/" + confirmationId
        );
    }

    public void notifyCustomerPaymentConfirmed(
        Long confirmationId,
        Long loanRequestId,
        Long customerId,
        Long staffUserId,
        BigDecimal confirmedAmount
    ) {
        String message = "Yêu cầu xác nhận thanh toán #" + confirmationId
            + " cho khoản vay #" + loanRequestId
            + " đã được duyệt thành công.";
        if (confirmedAmount != null && confirmedAmount.compareTo(BigDecimal.ZERO) > 0) {
            message = message + " Số tiền đã ghi nhận: " + formatMoney(confirmedAmount) + ".";
        }

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.PAYMENT_CONFIRMED,
            "Thanh toán đã được ghi nhận",
            message,
            "/customer/payments"
        );
    }

    public void notifyCustomerPaymentRejected(
        Long confirmationId,
        Long loanRequestId,
        Long customerId,
        Long staffUserId,
        String rejectionReason
    ) {
        String message = "Yêu cầu xác nhận thanh toán #" + confirmationId
            + " cho khoản vay #" + loanRequestId
            + " đã bị từ chối.";
        if (rejectionReason != null && !rejectionReason.isBlank()) {
            message = message + " Lý do: " + rejectionReason.trim();
        }
        message = message + " Vui lòng kiểm tra lại biên lai và gửi lại nếu cần.";

        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.PAYMENT_REJECTED,
            "Thanh toán chưa được chấp nhận",
            message,
            "/customer/payments"
        );
    }

    public void notifyCustomerPaymentDueSoon(
        Long loanRequestId,
        Long customerId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal currentAmountDue,
        BigDecimal outstandingAmount
    ) {
        String message = "Khoản vay #" + loanRequestId
            + " sắp đến hạn thanh toán kỳ #" + installmentNumber
            + " vào ngày " + formatDate(dueDate)
            + ". Số tiền cần thanh toán kỳ này: " + formatMoney(currentAmountDue)
            + ". Dư nợ còn lại: " + formatMoney(outstandingAmount) + ".";

        createForRecipients(
            List.of(customerId),
            null,
            NotificationCategory.PAYMENT_DUE_SOON,
            "Khoản vay sắp đến hạn thanh toán",
            message,
            "/customer/payments"
        );
    }

    public void notifyCustomerLoanOverdue(
        Long loanRequestId,
        Long customerId,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal currentAmountDue,
        long overdueDays
    ) {
        StringBuilder message = new StringBuilder("Khoản vay #")
            .append(loanRequestId)
            .append(" đã quá hạn thanh toán");
        if (installmentNumber != null) {
            message.append(" ở kỳ #").append(installmentNumber);
        }
        if (dueDate != null) {
            message.append(" từ ngày ").append(formatDate(dueDate));
        }
        message.append(".");
        if (currentAmountDue != null && currentAmountDue.compareTo(BigDecimal.ZERO) > 0) {
            message.append(" Số tiền đang đến hạn: ").append(formatMoney(currentAmountDue)).append(".");
        }
        if (overdueDays > 0) {
            message.append(" Bạn đang chậm ").append(overdueDays).append(" ngày.");
        }
        message.append(" Vui lòng thanh toán sớm để tránh ảnh hưởng điểm thanh toán và phát sinh xử lý nợ quá hạn theo chính sách.");

        createForRecipients(
            List.of(customerId),
            null,
            NotificationCategory.LOAN_OVERDUE,
            "Khoản vay đã quá hạn thanh toán",
            message.toString(),
            "/customer/payments"
        );
    }

    public void notifyCustomerLoanClosed(Long loanRequestId, Long customerId, Long actorUserId) {
        createForRecipients(
            List.of(customerId),
            actorUserId,
            NotificationCategory.LOAN_CLOSED,
            "Khoản vay đã được tất toán",
            "Khoản vay #" + loanRequestId
                + " đã được tất toán hoàn toàn. Bạn không còn dư nợ trên hợp đồng này.",
            "/customer/loans/" + loanRequestId
        );
    }

    public void notifyCustomerAppointmentNoShow(Long loanRequestId, Long customerId, Long staffUserId) {
        createForRecipients(
            List.of(customerId),
            staffUserId,
            NotificationCategory.APPOINTMENT_NO_SHOW,
            "Bạn đã bỏ lỡ lịch hẹn gặp mặt",
            "Nhân viên đã ghi nhận bạn vắng mặt ở lịch hẹn của hồ sơ vay #" + loanRequestId
                + ". Vui lòng liên hệ lại để đặt lịch mới, nếu không hồ sơ có thể bị dừng hoặc hủy.",
            "/customer/loans/" + loanRequestId
        );
    }

    private void createForStaff(
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        createForRecipients(userRepository.findIdsByRole(Role.STAFF), actorUserId, type, title, message, link);
    }

    private void createForRecipient(
        Long recipientUserId,
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        if (recipientUserId == null) {
            return;
        }
        createForRecipients(List.of(recipientUserId), actorUserId, type, title, message, link);
    }

    private void createForAssignedStaffOrStaffRole(
        Long assignedStaffUserId,
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        if (assignedStaffUserId != null) {
            createForRecipient(assignedStaffUserId, actorUserId, type, title, message, link);
            return;
        }
        createForStaff(actorUserId, type, title, message, link);
    }

    private void createForRecipients(
        List<Long> recipientUserIds,
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        notificationRepository.createBatch(recipientUserIds, actorUserId, type, title, message, link);
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
            record.id(),
            record.type(),
            record.actorEmail(),
            record.title(),
            record.message(),
            record.link(),
            record.read(),
            record.createdAt()
        );
    }

    private String formatDateTime(Instant value) {
        if (value == null) {
            return "chưa xác định";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String formatDate(LocalDate value) {
        return value != null ? DATE_FORMATTER.format(value) : "chưa xác định";
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "0 ₫";
        }
        return NumberFormat.getCurrencyInstance(VIETNAM_LOCALE).format(value);
    }
}
