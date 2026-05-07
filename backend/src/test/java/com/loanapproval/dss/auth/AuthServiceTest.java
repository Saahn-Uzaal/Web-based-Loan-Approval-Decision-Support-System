package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.AuthRequest;
import com.loanapproval.dss.auth.dto.RegisterRequest;
import com.loanapproval.dss.security.AuthenticatedUser;
import com.loanapproval.dss.security.JwtService;
import com.loanapproval.dss.shared.Role;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimitService loginRateLimitService;

    @InjectMocks
    private AuthService authService;

    @Test
    void publicRegisterShouldRejectStaffRole() {
        RegisterRequest request = new RegisterRequest("staff@example.com", "secret123", Role.STAFF);

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request));

        Assertions.assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(userRepository, never()).create(any(), any(), any());
    }

    @Test
    void publicRegisterShouldCreateCustomerOnly() {
        RegisterRequest request = new RegisterRequest(" Customer@Example.com ", "secret123", Role.CUSTOMER);
        UserAccount account = new UserAccount(10L, "customer@example.com", "encoded", Role.CUSTOMER);

        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userRepository.create("customer@example.com", "encoded", Role.CUSTOMER)).thenReturn(account);
        when(jwtService.generateAccessToken(account)).thenReturn("token");
        when(jwtService.generateRefreshToken(account)).thenReturn("refresh");

        var response = authService.register(request);

        Assertions.assertEquals("token", response.accessToken());
        Assertions.assertEquals("refresh", response.refreshToken());
        Assertions.assertEquals(Role.CUSTOMER, response.user().role());
        verify(userRepository).create(eq("customer@example.com"), eq("encoded"), eq(Role.CUSTOMER));
    }

    @Test
    void loginShouldRecordFailureWhenPasswordIsInvalid() {
        AuthRequest request = new AuthRequest("customer@example.com", "wrong-password");
        UserAccount account = new UserAccount(10L, "customer@example.com", "encoded", Role.CUSTOMER);

        when(userRepository.findByEmail("customer@example.com")).thenReturn(java.util.Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        ResponseStatusException exception = Assertions.assertThrows(
            ResponseStatusException.class,
            () -> authService.login(request, "127.0.0.1")
        );

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(loginRateLimitService).assertAllowed("customer@example.com", "127.0.0.1");
        verify(loginRateLimitService).recordFailure("customer@example.com", "127.0.0.1");
    }

    @Test
    void refreshShouldIssueNewTokens() {
        UserAccount account = new UserAccount(10L, "customer@example.com", "encoded", Role.CUSTOMER);
        AuthenticatedUser principal = new AuthenticatedUser(10L, "customer@example.com", Role.CUSTOMER);

        when(jwtService.parseRefreshToken("valid-refresh-token")).thenReturn(principal);
        when(userRepository.findById(10L)).thenReturn(java.util.Optional.of(account));
        when(jwtService.generateAccessToken(account)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(account)).thenReturn("new-refresh-token");

        var response = authService.refresh("valid-refresh-token");

        Assertions.assertEquals("new-access-token", response.accessToken());
        Assertions.assertEquals("new-refresh-token", response.refreshToken());
        Assertions.assertEquals("customer@example.com", response.user().email());
    }
}
