package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.AuthRequest;
import com.loanapproval.dss.auth.dto.AuthResponse;
import com.loanapproval.dss.auth.dto.RegisterRequest;
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

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        LoginRateLimitService loginRateLimitService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại");
        }

        Role role = request.role() != null ? request.role() : Role.CUSTOMER;
        if (role != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đăng ký công khai chỉ dành cho khách hàng");
        }
        UserAccount user = userRepository.create(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            role
        );
        log.info("New user registered: userId={}, email={}, role={}", user.id(), normalizedEmail, role);
        return toAuthResponse(user);
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

    private AuthResponse toAuthResponse(UserAccount user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken, toUserResponse(user));
    }

    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.role());
    }
}
