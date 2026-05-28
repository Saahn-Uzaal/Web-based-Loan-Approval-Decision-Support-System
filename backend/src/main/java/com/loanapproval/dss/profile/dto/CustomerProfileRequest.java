package com.loanapproval.dss.profile.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerProfileRequest(
    @NotBlank @Size(max = 150) String fullName,
    @Size(max = 30) String phone,
    @NotBlank
    @Pattern(regexp = "\\d{12}", message = "Số CCCD phải gồm đúng 12 chữ số")
    String identityNumber,
    LocalDate dateOfBirth,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal monthlyIncome,
    @Size(max = 100) String employmentStatus,
    LocalDate employmentStartDate,
    @Size(max = 40) String bankAccountNumber,
    @Size(max = 150) String bankName
) {
}
