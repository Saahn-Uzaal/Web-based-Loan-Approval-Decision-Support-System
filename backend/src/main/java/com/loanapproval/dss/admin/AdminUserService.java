package com.loanapproval.dss.admin;

import com.loanapproval.dss.admin.dto.AdminUserResponse;
import com.loanapproval.dss.shared.Role;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    public List<AdminUserResponse> listManagedUsers(Role role) {
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role ADMIN is not managed here");
        }
        return adminUserRepository.findManagedUsers(role).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void deleteManagedUser(Long actingAdminId, Long targetUserId) {
        if (actingAdminId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own admin account");
        }

        ManagedUserRecord target = adminUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (target.role() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleting admin users is not allowed");
        }

        int affectedRows = target.role() == Role.CUSTOMER
            ? adminUserRepository.deleteCustomerAndRelations(targetUserId)
            : adminUserRepository.deleteStaffAndRelations(targetUserId);

        if (affectedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
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
