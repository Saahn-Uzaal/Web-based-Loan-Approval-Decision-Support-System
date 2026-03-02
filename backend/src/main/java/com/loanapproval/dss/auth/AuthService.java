package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.AuthRequest;
import com.loanapproval.dss.auth.dto.AuthResponse;
import com.loanapproval.dss.auth.dto.RegisterRequest;
import com.loanapproval.dss.auth.dto.UserResponse;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.security.JwtService;
import com.loanapproval.dss.shared.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        Role role = request.role() != null ? request.role() : Role.CUSTOMER;
        UserAccount user = userRepository.create(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            role
        );
        return toAuthResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        UserAccount user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return toAuthResponse(user);
    }

    public UserResponse me(AuthenticatedUser authenticatedUser) {
        UserAccount user = userRepository.findById(authenticatedUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        return toUserResponse(user);
    }

    private AuthResponse toAuthResponse(UserAccount user) {
        String token = jwtService.generateAccessToken(user);
        return new AuthResponse(token, toUserResponse(user));
    }

    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.role());
    }
}
