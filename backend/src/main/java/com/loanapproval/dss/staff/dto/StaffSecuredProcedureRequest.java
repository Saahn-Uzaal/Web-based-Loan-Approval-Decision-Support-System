package com.loanapproval.dss.staff.dto;

import com.loanapproval.dss.staff.SecuredProcedureStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record StaffSecuredProcedureRequest(
        @Size(max = 150) String mortgageeName,
        @Size(max = 255) String mortgageeAddress,
        @Size(max = 100) String mortgageeBusinessCode,
        @Size(max = 50) String mortgageePhone,
        @Size(max = 100) String contractNumber,
        LocalDate contractSignedDate,
        @Size(max = 80) String nationality,
        @Size(max = 100) String identityDocumentNumber,
        @Size(max = 255) String permanentAddress,
        @Size(max = 255) String currentAddress,
        @Size(max = 120) String occupation,
        @Size(max = 120) String jobTitle,
        @Size(max = 100) String assetType,
        @Size(max = 120) String assetManufacturer,
        @Size(max = 100) String engineNumber,
        @Size(max = 100) String frameNumber,
        @Size(max = 150) String collateralOwnerName,
        @Size(max = 100) String collateralIdentifier,
        @Size(max = 100) String registrationNumber,
        @DecimalMin("0.0") BigDecimal salePrice,
        @DecimalMin("0.0") BigDecimal downPayment,
        @DecimalMin("0.0") BigDecimal appraisalValue,
        @DecimalMin("0.0") BigDecimal monthlyInterestRate,
        @DecimalMin("0.0") BigDecimal monthlyPaymentAmount,
        LocalDate firstPaymentDate,
        @Size(max = 30) String monthlyPaymentDay,
        LocalDate finalPaymentDate,
        @Size(max = 100) String appraisalReportCode,
        @Size(max = 100) String insurancePolicyNumber,
        Boolean originalCertificateReceived,
        Boolean certifiedCopyDelivered,
        Boolean collateralRegistrationCompleted,
        Boolean disputeChecked,
        Boolean seizureNoticeAcknowledged,
        Boolean documentsChecked,
        Boolean assetInspected,
        Boolean valuationApproved,
        Boolean contractSigned,
        Boolean collateralHandoverConfirmed,
        Boolean disbursementReady,
        SecuredProcedureStatus status,
        @Size(max = 1000) String note) {
}
