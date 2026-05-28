package com.loanapproval.dss.loan;

import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import com.loanapproval.dss.verification.dto.CustomerVerificationResponse;
import com.loanapproval.dss.verification.dto.UpdateCustomerVerificationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanApplicationVerificationService {

    private final LoanApplicationSnapshotRepository loanApplicationSnapshotRepository;

    public LoanApplicationVerificationService(LoanApplicationSnapshotRepository loanApplicationSnapshotRepository) {
        this.loanApplicationSnapshotRepository = loanApplicationSnapshotRepository;
    }

    public CustomerVerification getOrDefault(Long loanRequestId, Long customerId) {
        return loanApplicationSnapshotRepository.findByLoanRequestId(loanRequestId)
                .map(snapshot -> toVerification(snapshot, customerId))
                .orElseGet(() -> defaultPending(customerId));
    }

    @Transactional
    public CustomerVerificationResponse update(
            Long loanRequestId,
            Long customerId,
            Long staffUserId,
            UpdateCustomerVerificationRequest request) {
        CustomerVerification current = getOrDefault(loanRequestId, customerId);
        VerificationStatus documentStatus = currentOrRequested(current.documentStatus(), request.documentStatus());
        VerificationStatus identityStatus = currentOrRequested(current.identityStatus(), request.identityStatus());
        VerificationStatus faceMatchStatus = currentOrRequested(current.faceMatchStatus(), request.faceMatchStatus());
        VerificationStatus incomeStatus = currentOrRequested(current.incomeStatus(), request.incomeStatus());
        VerificationStatus kycStatus = currentOrRequested(current.kycStatus(), request.kycStatus());
        VerificationStatus amlStatus = currentOrRequested(current.amlStatus(), request.amlStatus());
        boolean fraudFlag = request.fraudFlag() != null ? request.fraudFlag() : current.fraudFlag();
        BigDecimal verifiedMonthlyIncome = resolveVerifiedMonthlyIncome(current, request, incomeStatus);
        String note = normalize(request.note());
        Instant verifiedAt = Instant.now();

        loanApplicationSnapshotRepository.updateVerification(
                loanRequestId,
                documentStatus,
                identityStatus,
                faceMatchStatus,
                incomeStatus,
                kycStatus,
                amlStatus,
                fraudFlag,
                note,
                staffUserId,
                verifiedAt,
                verifiedMonthlyIncome);

        return toResponse(getOrDefault(loanRequestId, customerId));
    }

    public boolean isFullyVerified(LoanType loanType, CustomerVerification verification) {
        boolean faceMatchRequired = loanType != LoanType.SECURED;
        return verification.documentStatus() == VerificationStatus.PASSED
                && verification.identityStatus() == VerificationStatus.PASSED
                && (!faceMatchRequired || verification.faceMatchStatus() == VerificationStatus.PASSED)
                && verification.incomeStatus() == VerificationStatus.PASSED
                && verification.kycStatus() == VerificationStatus.PASSED
                && verification.amlStatus() == VerificationStatus.PASSED
                && !verification.fraudFlag();
    }

    private CustomerVerification toVerification(LoanApplicationSnapshot snapshot, Long customerId) {
        return new CustomerVerification(
                customerId,
                snapshot.documentStatus(),
                snapshot.identityStatus(),
                snapshot.faceMatchStatus(),
                snapshot.incomeStatus(),
                snapshot.kycStatus(),
                snapshot.amlStatus(),
                snapshot.fraudFlag(),
                snapshot.verificationNote(),
                snapshot.verifiedBy(),
                snapshot.verifiedAt(),
                snapshot.snapshotAt(),
                snapshot.snapshotAt());
    }

    private CustomerVerification defaultPending(Long customerId) {
        return new CustomerVerification(
                customerId,
                VerificationStatus.PENDING,
                VerificationStatus.PENDING,
                VerificationStatus.PENDING,
                VerificationStatus.PENDING,
                VerificationStatus.PENDING,
                VerificationStatus.PENDING,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private CustomerVerificationResponse toResponse(CustomerVerification verification) {
        return new CustomerVerificationResponse(
                verification.customerId(),
                verification.documentStatus(),
                verification.identityStatus(),
                verification.faceMatchStatus(),
                verification.incomeStatus(),
                verification.kycStatus(),
                verification.amlStatus(),
                verification.fraudFlag(),
                verification.note(),
                verification.verifiedBy(),
                verification.verifiedAt(),
                verification.hasHardRejectFlag(),
                verification.isPending());
    }

    private VerificationStatus currentOrRequested(
            VerificationStatus currentStatus,
            VerificationStatus requestedStatus) {
        return requestedStatus != null ? requestedStatus : currentStatus;
    }

    private BigDecimal resolveVerifiedMonthlyIncome(
            CustomerVerification current,
            UpdateCustomerVerificationRequest request,
            VerificationStatus incomeStatus) {
        if (incomeStatus != VerificationStatus.PASSED) {
            return null;
        }
        BigDecimal verifiedMonthlyIncome = request.verifiedMonthlyIncome();
        if (verifiedMonthlyIncome == null || verifiedMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cần nhập thu nhập đã xác minh lớn hơn 0 khi đánh dấu bước thu nhập là đạt.");
        }
        return verifiedMonthlyIncome;
    }

    private String normalize(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }
}
