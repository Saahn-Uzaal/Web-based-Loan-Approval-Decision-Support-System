package com.loanapproval.dss.profile;

import com.loanapproval.dss.debt.CustomerDebtService;
import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerDebtService customerDebtService;

    public CustomerProfileService(
        CustomerProfileRepository customerProfileRepository,
        CustomerDebtService customerDebtService
    ) {
        this.customerProfileRepository = customerProfileRepository;
        this.customerDebtService = customerDebtService;
    }

    public CustomerProfileResponse getByUserId(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        return toResponse(profile);
    }

    @Transactional
    public CustomerProfileResponse upsert(Long userId, CustomerProfileRequest request) {
        CustomerProfile profile = new CustomerProfile(
            userId,
            request.fullName(),
            request.phone(),
            request.dateOfBirth(),
            request.monthlyIncome(),
            request.debtToIncomeRatio(),
            request.employmentStatus(),
            request.employmentStartDate(),
            request.creditHistoryScore(),
            null
        );
        customerProfileRepository.upsert(profile);
        BigDecimal calculatedDti = customerDebtService.recalculateAndSyncDti(userId);
        return customerProfileRepository.findByUserId(userId)
            .map(saved -> calculatedDti == null ? saved : new CustomerProfile(
                saved.userId(),
                saved.fullName(),
                saved.phone(),
                saved.dateOfBirth(),
                saved.monthlyIncome(),
                calculatedDti,
                saved.employmentStatus(),
                saved.employmentStartDate(),
                saved.creditHistoryScore(),
                saved.paymentRating()
            ))
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save profile"));
    }

    private CustomerProfileResponse toResponse(CustomerProfile profile) {
        return new CustomerProfileResponse(
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.dateOfBirth(),
            profile.monthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus(),
            profile.employmentStartDate(),
            profile.creditHistoryScore(),
            profile.paymentRating()
        );
    }
}
