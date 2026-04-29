package com.loanapproval.dss.auth;

import com.loanapproval.dss.auth.dto.RegisterRequest;
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

        var response = authService.register(request);

        Assertions.assertEquals("token", response.accessToken());
        Assertions.assertEquals(Role.CUSTOMER, response.user().role());
        verify(userRepository).create(eq("customer@example.com"), eq("encoded"), eq(Role.CUSTOMER));
    }
}
