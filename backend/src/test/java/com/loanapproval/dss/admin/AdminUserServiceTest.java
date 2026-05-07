package com.loanapproval.dss.admin;

import com.loanapproval.dss.shared.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private com.loanapproval.dss.auth.UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void listManagedUsersPagedShouldApplySafePagingBounds() {
        when(adminUserRepository.countManagedUsers(null)).thenReturn(1L);
        when(adminUserRepository.findManagedUsersPaged(null, 0, 100)).thenReturn(List.of(
            new ManagedUserRecord(2L, "customer@example.com", Role.CUSTOMER, Instant.parse("2026-01-01T00:00:00Z"))
        ));

        var page = adminUserService.listManagedUsersPaged(null, -4, 999);

        Assertions.assertEquals(0, page.page());
        Assertions.assertEquals(100, page.size());
        Assertions.assertEquals(1, page.totalElements());
        Assertions.assertEquals(1, page.content().size());
        verify(adminUserRepository).findManagedUsersPaged(null, 0, 100);
    }

    @Test
    void listManagedUsersPagedShouldRejectAdminRoleFilter() {
        ResponseStatusException exception = Assertions.assertThrows(
            ResponseStatusException.class,
            () -> adminUserService.listManagedUsersPaged(Role.ADMIN, 0, 10)
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
