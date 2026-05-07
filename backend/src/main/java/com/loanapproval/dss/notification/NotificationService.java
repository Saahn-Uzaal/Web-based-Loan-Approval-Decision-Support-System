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

    private void createForStaff(
        Long actorUserId,
        NotificationCategory type,
        String title,
        String message,
        String link
    ) {
        createForRecipients(userRepository.findIdsByRole(Role.STAFF), actorUserId, type, title, message, link);
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
