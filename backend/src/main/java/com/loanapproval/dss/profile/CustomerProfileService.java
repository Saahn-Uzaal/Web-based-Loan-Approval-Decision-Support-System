package com.loanapproval.dss.profile;

import com.loanapproval.dss.profile.dto.CustomerProfileRequest;
import com.loanapproval.dss.profile.dto.CustomerProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;

    public CustomerProfileService(CustomerProfileRepository customerProfileRepository) {
        this.customerProfileRepository = customerProfileRepository;
    }

    public CustomerProfileResponse getByUserId(Long userId) {
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        return toResponse(profile);
    }

    public CustomerProfileResponse upsert(Long userId, CustomerProfileRequest request) {
        CustomerProfile profile = new CustomerProfile(
            userId,
            request.fullName(),
            request.phone(),
            request.monthlyIncome(),
            request.debtToIncomeRatio(),
            request.employmentStatus(),
            null
        );
        customerProfileRepository.upsert(profile);
        return customerProfileRepository.findByUserId(userId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save profile"));
    }

    private CustomerProfileResponse toResponse(CustomerProfile profile) {
        return new CustomerProfileResponse(
            profile.userId(),
            profile.fullName(),
            profile.phone(),
            profile.monthlyIncome(),
            profile.debtToIncomeRatio(),
            profile.employmentStatus(),
            profile.paymentRating()
        );
    }
}
