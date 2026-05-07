package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.AuthRequest;
import com.loanapproval.dss.auth.dto.AuthResponse;
import com.loanapproval.dss.auth.dto.RefreshTokenRequest;
import com.loanapproval.dss.auth.dto.RegisterRequest;
import com.loanapproval.dss.auth.dto.UserResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, extractClientIp(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.me(extractUser(authentication));
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
        }
        return user;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int separatorIndex = forwardedFor.indexOf(',');
            String firstHop = separatorIndex >= 0 ? forwardedFor.substring(0, separatorIndex) : forwardedFor;
            if (!firstHop.isBlank()) {
                return firstHop.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
