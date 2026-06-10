package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.AuthRequest;
import com.loanapproval.dss.auth.dto.AuthResponse;
import com.loanapproval.dss.auth.dto.EmailVerificationResponse;
import com.loanapproval.dss.auth.dto.RegisterRequest;
import com.loanapproval.dss.auth.dto.RegisterResponse;
import com.loanapproval.dss.auth.dto.UserResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.security.JwtService;
import com.loanapproval.dss.shared.Role;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        LoginRateLimitService loginRateLimitService,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.emailVerificationService = emailVerificationService;
    }

    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Role role = request.role() != null ? request.role() : Role.CUSTOMER;
        if (role != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đăng ký công khai chỉ dành cho khách hàng");
        }

        UserEmailVerificationRecord existingVerification = userRepository.findEmailVerificationByEmail(normalizedEmail).orElse(null);
        if (existingVerification != null) {
            if (existingVerification.emailVerifiedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại");
            }
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email này đã đăng ký nhưng chưa xác minh. Vui lòng kiểm tra hộp thư hoặc gửi lại email xác minh."
            );
        }

        boolean verificationRequired = emailVerificationService.isEnabled();
        if (verificationRequired) {
            emailVerificationService.assertReady();
        }

        UserAccount user = userRepository.create(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            role,
            !verificationRequired
        );
        log.info(
            "New user registered: userId={}, email={}, role={}, verificationRequired={}",
            user.id(),
            normalizedEmail,
            role,
            verificationRequired
        );

        if (verificationRequired) {
            emailVerificationService.sendVerificationEmail(user);
            return new RegisterResponse(
                normalizedEmail,
                "Đã tạo tài khoản. Vui lòng kiểm tra email để xác minh trước khi đăng nhập.",
                true
            );
        }

        return new RegisterResponse(
            normalizedEmail,
            "Đã tạo tài khoản. Bạn có thể đăng nhập ngay.",
            false
        );
    }

    public AuthResponse login(AuthRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase();
        loginRateLimitService.assertAllowed(normalizedEmail, clientIp);
        UserAccount user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            log.warn("Failed login attempt for email={}", normalizedEmail);
            loginRateLimitService.recordFailure(normalizedEmail, clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }
        if (!userRepository.isEmailVerified(user.id())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Email chưa được xác minh. Vui lòng kiểm tra hộp thư hoặc gửi lại email xác minh."
            );
        }
        loginRateLimitService.recordSuccess(normalizedEmail, clientIp);
        log.info("User logged in: userId={}, email={}", user.id(), normalizedEmail);
        return toAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        AuthenticatedUser principal;
        try {
            principal = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn");
        }

        UserAccount user = userRepository.findById(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        return toAuthResponse(user);
    }

    public UserResponse me(AuthenticatedUser authenticatedUser) {
        UserAccount user = userRepository.findById(authenticatedUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        return toUserResponse(user);
    }

    public EmailVerificationResponse verifyEmail(String token) {
        return emailVerificationService.verifyEmail(token);
    }

    public EmailVerificationResponse resendVerificationEmail(String email) {
        return emailVerificationService.resendVerificationEmail(email);
    }

    private AuthResponse toAuthResponse(UserAccount user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken, toUserResponse(user));
    }

    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.role());
    }
}
