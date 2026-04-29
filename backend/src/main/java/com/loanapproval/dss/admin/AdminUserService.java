package com.loanapproval.dss.admin;

import com.loanapproval.dss.admin.dto.AdminUserResponse;
import com.loanapproval.dss.admin.dto.AdminCreateUserRequest;
import com.loanapproval.dss.auth.UserAccount;
import com.loanapproval.dss.auth.UserRepository;
import com.loanapproval.dss.shared.Role;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
        AdminUserRepository adminUserRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.adminUserRepository = adminUserRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminUserResponse> listManagedUsers(Role role) {
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò ADMIN không được quản lý ở màn hình này");
        }
        return adminUserRepository.findManagedUsers(role).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AdminUserResponse createManagedUser(AdminCreateUserRequest request) {
        Role role = request.role();
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không cho phép tạo tài khoản ADMIN tại màn hình này");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại");
        }

        UserAccount user = userRepository.create(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            role
        );
        return new AdminUserResponse(user.id(), user.email(), user.role(), java.time.Instant.now());
    }

    @Transactional
    public void deleteManagedUser(Long actingAdminId, Long targetUserId) {
        if (actingAdminId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn không thể xóa chính tài khoản quản trị của mình");
        }

        ManagedUserRecord target = adminUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

        if (target.role() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không cho phép xóa tài khoản quản trị");
        }

        int affectedRows = target.role() == Role.CUSTOMER
            ? adminUserRepository.deleteCustomerAndRelations(targetUserId)
            : adminUserRepository.deleteStaffAndRelations(targetUserId);

        if (affectedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
        }
    }

    private AdminUserResponse toResponse(ManagedUserRecord record) {
        return new AdminUserResponse(
            record.id(),
            record.email(),
            record.role(),
            record.createdAt()
        );
    }
}
