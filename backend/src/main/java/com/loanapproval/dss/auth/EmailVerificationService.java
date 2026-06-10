package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.EmailVerificationResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final EmailVerificationTokenService tokenService;
    private final boolean enabled;
    private final long resendCooldownSeconds;
    private final String frontendUrl;
    private final String fromAddress;
    private final String smtpUsername;
    private final String smtpPassword;

    public EmailVerificationService(
        UserRepository userRepository,
        JavaMailSender mailSender,
        EmailVerificationTokenService tokenService,
        @Value("${app.auth.email-verification.enabled:false}") boolean enabled,
        @Value("${app.auth.email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
        @Value("${app.auth.email-verification.frontend-url:http://localhost:5173/verify-email}") String frontendUrl,
        @Value("${app.auth.email-verification.from:}") String fromAddress,
        @Value("${spring.mail.username:}") String smtpUsername,
        @Value("${spring.mail.password:}") String smtpPassword
    ) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.tokenService = tokenService;
        this.enabled = enabled;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.frontendUrl = frontendUrl;
        this.fromAddress = fromAddress;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void assertReady() {
        if (!enabled) {
            return;
        }
        if (fromAddress == null || fromAddress.isBlank() || smtpUsername == null || smtpUsername.isBlank()
            || smtpPassword == null || smtpPassword.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Cấu hình gửi email xác minh chưa hoàn tất. Vui lòng kiểm tra APP_MAIL_USERNAME và APP_MAIL_PASSWORD."
            );
        }
    }

    public void sendVerificationEmail(UserAccount user) {
        if (!enabled) {
            return;
        }

        UserEmailVerificationRecord verification = userRepository.findEmailVerificationById(user.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản cần xác minh"));
        if (verification.emailVerifiedAt() != null) {
            return;
        }

        Instant now = Instant.now();
        Instant lastSentAt = verification.verificationEmailSentAt();
        long cooldown = Math.max(resendCooldownSeconds, 0);
        if (lastSentAt != null && cooldown > 0 && lastSentAt.plusSeconds(cooldown).isAfter(now)) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Vui lòng chờ ít nhất " + cooldown + " giây trước khi gửi lại email xác minh"
            );
        }

        String token = tokenService.generateToken(user.id(), user.email());
        String verificationLink = buildVerificationLink(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.email());
        message.setSubject("Xác minh email đăng ký tài khoản");
        message.setText("""
            Chào bạn,

            Bạn vừa đăng ký tài khoản trên hệ thống hỗ trợ quyết định phê duyệt khoản vay.
            Vui lòng bấm vào liên kết dưới đây để xác minh email trước khi đăng nhập:

            %s

            Nếu bạn không thực hiện đăng ký, bạn có thể bỏ qua email này.
            """.formatted(verificationLink));

        mailSender.send(message);
        userRepository.markVerificationEmailSent(user.id(), now);
        log.info("Sent verification email to userId={}, email={}", user.id(), user.email());
    }

    public EmailVerificationResponse verifyEmail(String token) {
        EmailVerificationTokenService.EmailVerificationTokenPayload payload = tokenService.parseToken(token);
        UserEmailVerificationRecord verification = userRepository.findEmailVerificationById(payload.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản cần xác minh"));

        if (!verification.email().equalsIgnoreCase(payload.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết xác minh email không khớp với tài khoản");
        }
        if (verification.emailVerifiedAt() != null) {
            return new EmailVerificationResponse(
                verification.email(),
                "Email này đã được xác minh trước đó. Bạn có thể đăng nhập ngay.",
                true
            );
        }

        userRepository.markEmailVerified(payload.userId(), Instant.now());
        log.info("Verified email for userId={}, email={}", payload.userId(), verification.email());
        return new EmailVerificationResponse(
            verification.email(),
            "Xác minh email thành công. Bạn có thể đăng nhập vào hệ thống.",
            true
        );
    }

    public EmailVerificationResponse resendVerificationEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        UserEmailVerificationRecord verification = userRepository.findEmailVerificationByEmail(normalizedEmail).orElse(null);
        if (verification == null) {
            return new EmailVerificationResponse(
                normalizedEmail,
                "Nếu email tồn tại và chưa được xác minh, hệ thống sẽ gửi lại thư xác minh.",
                false
            );
        }
        if (verification.emailVerifiedAt() != null) {
            return new EmailVerificationResponse(
                verification.email(),
                "Email này đã được xác minh. Bạn có thể đăng nhập ngay.",
                true
            );
        }

        assertReady();
        sendVerificationEmail(new UserAccount(verification.id(), verification.email(), "", verification.role()));
        return new EmailVerificationResponse(
            verification.email(),
            "Đã gửi lại email xác minh. Vui lòng kiểm tra hộp thư đến và thư rác.",
            false
        );
    }

    private String buildVerificationLink(String token) {
        String separator = frontendUrl.contains("?") ? "&" : "?";
        return frontendUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
