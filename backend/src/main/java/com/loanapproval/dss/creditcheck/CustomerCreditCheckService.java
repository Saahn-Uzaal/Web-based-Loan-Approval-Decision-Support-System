package com.loanapproval.dss.creditcheck;

import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CustomerCreditCheckService {

    private static final int DEFAULT_NO_HIT_SCORE = 72;
    private static final String INTERNAL_SOURCE = "INTERNAL_BUREAU";

    private final CreditBureauRepository creditBureauRepository;
    private final CustomerCreditCheckRepository customerCreditCheckRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public CustomerCreditCheckService(
        CreditBureauRepository creditBureauRepository,
        CustomerCreditCheckRepository customerCreditCheckRepository,
        CustomerProfileRepository customerProfileRepository
    ) {
        this.creditBureauRepository = creditBureauRepository;
        this.customerCreditCheckRepository = customerCreditCheckRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    public CustomerCreditCheckSummary refreshForCustomer(Long customerId, CustomerProfile profile) {
        String identityNumber = normalizeIdentityNumber(profile != null ? profile.identityNumber() : null);
        if (identityNumber == null) {
            customerProfileRepository.updateCreditHistoryScore(customerId, null);
            return null;
        }

        CreditBureauRecord bureauRecord = creditBureauRepository.findByIdentityNumber(identityNumber).orElse(null);
        CustomerCreditCheckRecord saved = customerCreditCheckRepository.create(
            bureauRecord != null
                ? fromBureauRecord(customerId, identityNumber, bureauRecord)
                : noHitRecord(customerId, identityNumber)
        );
        customerProfileRepository.updateCreditHistoryScore(customerId, saved.creditScore());
        return saved.toSummary();
    }

    public Optional<CustomerCreditCheckSummary> findLatestByCustomerId(Long customerId) {
        return customerCreditCheckRepository.findLatestByCustomerId(customerId).map(CustomerCreditCheckRecord::toSummary);
    }

    private CustomerCreditCheckRecord fromBureauRecord(
        Long customerId,
        String identityNumber,
        CreditBureauRecord bureauRecord
    ) {
        Integer normalizedScore = normalizeScore(bureauRecord.creditScore(), bureauRecord.bureauStatus());
        boolean manualReviewRequired = bureauRecord.manualReviewRequired()
            || bureauRecord.bureauStatus() == CreditBureauStatus.WATCHLIST
            || bureauRecord.bureauStatus() == CreditBureauStatus.BAD_DEBT;
        boolean hardReject = bureauRecord.hardReject()
            || bureauRecord.bureauStatus() == CreditBureauStatus.FRAUD_SUSPECT;
        return new CustomerCreditCheckRecord(
            null,
            customerId,
            identityNumber,
            true,
            bureauRecord.bureauStatus(),
            normalizedScore,
            bureauRecord.activeLoanCount(),
            bureauRecord.daysPastDue(),
            manualReviewRequired,
            hardReject,
            defaultRiskNote(bureauRecord, hardReject, manualReviewRequired),
            INTERNAL_SOURCE,
            Instant.now()
        );
    }

    private CustomerCreditCheckRecord noHitRecord(Long customerId, String identityNumber) {
        return new CustomerCreditCheckRecord(
            null,
            customerId,
            identityNumber,
            false,
            CreditBureauStatus.NO_HIT,
            DEFAULT_NO_HIT_SCORE,
            0,
            0,
            false,
            false,
            "Không tìm thấy dữ liệu nợ xấu trong kho nội bộ cho số CCCD này.",
            INTERNAL_SOURCE,
            Instant.now()
        );
    }

    private Integer normalizeScore(Integer rawScore, CreditBureauStatus status) {
        int base = rawScore != null ? rawScore : DEFAULT_NO_HIT_SCORE;
        int capped = Math.min(100, Math.max(0, base));
        return switch (status) {
            case FRAUD_SUSPECT -> Math.min(capped, 15);
            case BAD_DEBT -> Math.min(capped, 35);
            case WATCHLIST -> Math.min(capped, 58);
            case CLEAR, NO_HIT -> capped;
        };
    }

    private String defaultRiskNote(
        CreditBureauRecord bureauRecord,
        boolean hardReject,
        boolean manualReviewRequired
    ) {
        if (bureauRecord.riskNote() != null && !bureauRecord.riskNote().isBlank()) {
            return bureauRecord.riskNote().trim();
        }
        if (hardReject) {
            return "CCCD khớp dữ liệu tín dụng có cờ từ chối cứng.";
        }
        if (manualReviewRequired) {
            return "CCCD khớp dữ liệu tín dụng cần thẩm định thủ công.";
        }
        return "Không có cờ rủi ro tín dụng đáng kể trong kho nội bộ.";
    }

    private String normalizeIdentityNumber(String value) {
        if (value == null) {
            return null;
        }
        String digitsOnly = value.replaceAll("\\s+", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }
}
