package com.loanapproval.dss.creditcheck;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.creditcheck.dto.CreditBureauLoanAccountResponse;
import com.loanapproval.dss.creditcheck.dto.CreditBureauRecordResponse;
import com.loanapproval.dss.creditcheck.dto.CreditBureauRegistrySummaryResponse;
import com.loanapproval.dss.creditcheck.dto.CreditBureauSyncResultResponse;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauLoanAccountRequest;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauRecordRequest;
import com.loanapproval.dss.loan.LoanPurpose;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.LoanRepaymentSnapshot;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import com.loanapproval.dss.shared.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditBureauManagementService {

    private static final String INTERNAL_REPORTING_INSTITUTION = "Ứng dụng DSS nội bộ";
    private static final Set<LoanStatus> INTERNAL_SYNC_STATUSES = EnumSet.of(
        LoanStatus.CONTRACTED,
        LoanStatus.ACTIVE,
        LoanStatus.OVERDUE,
        LoanStatus.CLOSED
    );

    private final CreditBureauRepository creditBureauRepository;
    private final LoanRepository loanRepository;
    private final LoanContractService loanContractService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final CustomerProfileRepository customerProfileRepository;

    public CreditBureauManagementService(
        CreditBureauRepository creditBureauRepository,
        LoanRepository loanRepository,
        LoanContractService loanContractService,
        RepaymentScheduleService repaymentScheduleService,
        CustomerProfileRepository customerProfileRepository
    ) {
        this.creditBureauRepository = creditBureauRepository;
        this.loanRepository = loanRepository;
        this.loanContractService = loanContractService;
        this.repaymentScheduleService = repaymentScheduleService;
        this.customerProfileRepository = customerProfileRepository;
    }

    public PageResponse<CreditBureauRecordResponse> listPaged(
        CreditBureauStatus status,
        String query,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedQuery = normalizeQuery(query);
        long total = creditBureauRepository.count(status, normalizedQuery);
        List<CreditBureauRecordResponse> content = creditBureauRepository
            .findPaged(status, normalizedQuery, safePage * safeSize, safeSize)
            .stream()
            .map(this::toResponse)
            .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public CreditBureauRegistrySummaryResponse getSummary() {
        CreditBureauRegistrySummarySnapshot snapshot = creditBureauRepository.summarize();
        return new CreditBureauRegistrySummaryResponse(
            snapshot.borrowerCount(),
            snapshot.badDebtCount(),
            snapshot.watchlistCount(),
            snapshot.totalActiveLoanCount(),
            nonNegative(snapshot.totalMonthlyObligation()),
            nonNegative(snapshot.totalOutstandingBalance())
        );
    }

    @Transactional
    public CreditBureauRecordResponse create(UpsertCreditBureauRecordRequest request) {
        return save(null, request);
    }

    @Transactional
    public CreditBureauRecordResponse update(String currentIdentityNumber, UpsertCreditBureauRecordRequest request) {
        return save(currentIdentityNumber, request);
    }

    @Transactional
    public void delete(String identityNumber) {
        String normalizedIdentityNumber = normalizeIdentityNumber(identityNumber);
        if (creditBureauRepository.deleteByIdentityNumber(normalizedIdentityNumber) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ tra cứu tín dụng cần xóa");
        }
    }

    @Transactional
    public CreditBureauSyncResultResponse syncInternalLoans() {
        List<LoanRecord> internalLoans = loanRepository.findByStatuses(INTERNAL_SYNC_STATUSES);
        Map<String, SyncBorrowerContext> borrowersByIdentity = new LinkedHashMap<>();
        Set<Long> skippedBorrowerIds = new LinkedHashSet<>();

        for (LoanRecord loan : internalLoans) {
            CustomerProfile profile = customerProfileRepository.findByUserId(loan.customerId()).orElse(null);
            String identityNumber = normalizeNullableIdentityNumber(profile != null ? profile.identityNumber() : null);
            if (identityNumber == null) {
                skippedBorrowerIds.add(loan.customerId());
                continue;
            }
            SyncBorrowerContext borrower = borrowersByIdentity.computeIfAbsent(
                identityNumber,
                key -> new SyncBorrowerContext(
                    identityNumber,
                    fallbackBorrowerName(profile != null ? profile.fullName() : null, identityNumber),
                    new ArrayList<>()
                )
            );
            borrower.internalAccounts().add(toInternalLoanAccount(identityNumber, loan));
        }

        Set<String> allImpactedIdentities = new LinkedHashSet<>(borrowersByIdentity.keySet());
        allImpactedIdentities.addAll(creditBureauRepository.findIdentityNumbersBySourceType(CreditLoanSourceType.INTERNAL_SYSTEM));

        int syncedLoanCount = 0;
        for (String identityNumber : allImpactedIdentities) {
            CreditBureauRecord existing = creditBureauRepository.findByIdentityNumber(identityNumber).orElse(null);
            List<CreditBureauLoanAccount> mergedAccounts = new ArrayList<>(
                creditBureauRepository.findLoanAccountsByIdentityNumber(identityNumber).stream()
                    .filter(account -> account.sourceType() != CreditLoanSourceType.INTERNAL_SYSTEM)
                    .toList()
            );
            SyncBorrowerContext syncedBorrower = borrowersByIdentity.get(identityNumber);
            if (syncedBorrower != null) {
                mergedAccounts.addAll(syncedBorrower.internalAccounts());
                syncedLoanCount += syncedBorrower.internalAccounts().size();
            }

            if (mergedAccounts.isEmpty()) {
                if (existing != null) {
                    creditBureauRepository.deleteByIdentityNumber(identityNumber);
                }
                continue;
            }

            DerivedCreditMetrics derived = deriveMetrics(mergedAccounts, existing != null && existing.hardReject());
            String borrowerName = syncedBorrower != null
                ? syncedBorrower.borrowerName()
                : fallbackBorrowerName(existing != null ? existing.borrowerName() : null, identityNumber);
            CreditBureauRecord upserted = creditBureauRepository.upsert(new CreditBureauRecord(
                identityNumber,
                borrowerName,
                derived.status(),
                derived.creditScore(),
                derived.activeLoanCount(),
                derived.maxDaysPastDue(),
                derived.manualReviewRequired(),
                derived.hardReject(),
                existing != null ? existing.riskNote() : null,
                derived.totalMonthlyObligation(),
                derived.totalOutstandingBalance(),
                derived.externalMonthlyObligation(),
                derived.externalOutstandingBalance(),
                derived.reportingInstitutionCount(),
                existing == null || existing.consentGranted(),
                derived.lastReportedAt(),
                existing != null ? existing.updatedAt() : null
            ));
            creditBureauRepository.replaceLoanAccounts(identityNumber, mergedAccounts);
        }

        return new CreditBureauSyncResultResponse(
            borrowersByIdentity.size(),
            syncedLoanCount,
            skippedBorrowerIds.size()
        );
    }

    private CreditBureauRecordResponse save(String currentIdentityNumber, UpsertCreditBureauRecordRequest request) {
        String normalizedIdentityNumber = normalizeIdentityNumber(request.identityNumber());
        if (currentIdentityNumber != null) {
            String normalizedCurrentIdentityNumber = normalizeIdentityNumber(currentIdentityNumber);
            if (!normalizedCurrentIdentityNumber.equals(normalizedIdentityNumber)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể thay đổi CCCD của hồ sơ tín dụng hiện có"
                );
            }
        }

        List<CreditBureauLoanAccount> loanAccounts = normalizeLoanAccounts(normalizedIdentityNumber, request.loanAccounts());
        DerivedCreditMetrics derived = deriveMetrics(loanAccounts, Boolean.TRUE.equals(request.fraudSuspect()));

        CreditBureauRecord saved = creditBureauRepository.upsert(new CreditBureauRecord(
            normalizedIdentityNumber,
            normalizeBorrowerName(request.borrowerName(), "Tên người vay không được để trống"),
            derived.status(),
            derived.creditScore(),
            derived.activeLoanCount(),
            derived.maxDaysPastDue(),
            derived.manualReviewRequired(),
            derived.hardReject(),
            normalizeQuery(request.riskNote()),
            derived.totalMonthlyObligation(),
            derived.totalOutstandingBalance(),
            derived.externalMonthlyObligation(),
            derived.externalOutstandingBalance(),
            derived.reportingInstitutionCount(),
            Boolean.TRUE.equals(request.consentGranted()),
            derived.lastReportedAt(),
            null
        ));
        creditBureauRepository.replaceLoanAccounts(normalizedIdentityNumber, loanAccounts);
        return toResponse(saved);
    }

    private List<CreditBureauLoanAccount> normalizeLoanAccounts(
        String identityNumber,
        List<UpsertCreditBureauLoanAccountRequest> requests
    ) {
        List<CreditBureauLoanAccount> accounts = new ArrayList<>();
        if (requests == null) {
            return accounts;
        }

        Instant now = Instant.now();
        for (UpsertCreditBureauLoanAccountRequest request : requests) {
            accounts.add(new CreditBureauLoanAccount(
                null,
                identityNumber,
                normalizeBorrowerName(request.reportingInstitution(), "Đơn vị báo cáo không được để trống"),
                normalizeQuery(request.accountReference()),
                request.sourceType(),
                normalizeQuery(request.loanCategory()),
                request.accountStatus(),
                nonNegative(request.originalAmount()),
                nonNegative(request.outstandingBalance()),
                nonNegative(request.monthlyPayment()),
                valueOrZero(request.daysPastDue()),
                normalizeQuery(request.note()),
                now,
                null
            ));
        }
        return accounts;
    }

    private DerivedCreditMetrics deriveMetrics(List<CreditBureauLoanAccount> accounts, boolean fraudSuspect) {
        int activeLoanCount = 0;
        int maxDaysPastDue = 0;
        BigDecimal totalMonthlyObligation = BigDecimal.ZERO;
        BigDecimal totalOutstandingBalance = BigDecimal.ZERO;
        BigDecimal externalMonthlyObligation = BigDecimal.ZERO;
        BigDecimal externalOutstandingBalance = BigDecimal.ZERO;
        Instant lastReportedAt = null;
        Set<String> institutions = new LinkedHashSet<>();
        boolean hasBadDebt = false;
        boolean hasOverdue = false;

        for (CreditBureauLoanAccount account : accounts) {
            if (account.reportingInstitution() != null) {
                institutions.add(account.reportingInstitution().trim());
            }
            if (account.reportedAt() != null && (lastReportedAt == null || account.reportedAt().isAfter(lastReportedAt))) {
                lastReportedAt = account.reportedAt();
            }
            if (!isActive(account.accountStatus())) {
                continue;
            }
            activeLoanCount += 1;
            maxDaysPastDue = Math.max(maxDaysPastDue, valueOrZero(account.daysPastDue()));
            totalMonthlyObligation = totalMonthlyObligation.add(nonNegative(account.monthlyPayment()));
            totalOutstandingBalance = totalOutstandingBalance.add(nonNegative(account.outstandingBalance()));
            if (account.sourceType() != CreditLoanSourceType.INTERNAL_SYSTEM) {
                externalMonthlyObligation = externalMonthlyObligation.add(nonNegative(account.monthlyPayment()));
                externalOutstandingBalance = externalOutstandingBalance.add(nonNegative(account.outstandingBalance()));
            }
            if (account.accountStatus() == CreditLoanAccountStatus.BAD_DEBT || valueOrZero(account.daysPastDue()) >= 90) {
                hasBadDebt = true;
            } else if (account.accountStatus() == CreditLoanAccountStatus.OVERDUE || valueOrZero(account.daysPastDue()) > 0) {
                hasOverdue = true;
            }
        }

        CreditBureauStatus status;
        if (fraudSuspect) {
            status = CreditBureauStatus.FRAUD_SUSPECT;
        } else if (hasBadDebt) {
            status = CreditBureauStatus.BAD_DEBT;
        } else if (hasOverdue) {
            status = CreditBureauStatus.WATCHLIST;
        } else if (activeLoanCount == 0) {
            status = CreditBureauStatus.NO_HIT;
        } else {
            status = CreditBureauStatus.CLEAR;
        }

        boolean manualReviewRequired = status == CreditBureauStatus.WATCHLIST
            || status == CreditBureauStatus.BAD_DEBT
            || status == CreditBureauStatus.FRAUD_SUSPECT;
        boolean hardReject = status == CreditBureauStatus.FRAUD_SUSPECT;

        return new DerivedCreditMetrics(
            status,
            deriveCreditScore(status, activeLoanCount, maxDaysPastDue),
            activeLoanCount,
            maxDaysPastDue,
            totalMonthlyObligation,
            totalOutstandingBalance,
            externalMonthlyObligation,
            externalOutstandingBalance,
            institutions.size(),
            manualReviewRequired,
            hardReject,
            lastReportedAt != null ? lastReportedAt : Instant.now()
        );
    }

    private int deriveCreditScore(CreditBureauStatus status, int activeLoanCount, int maxDaysPastDue) {
        return switch (status) {
            case FRAUD_SUSPECT -> 15;
            case BAD_DEBT -> Math.max(20, 35 - Math.min(maxDaysPastDue, 180) / 18);
            case WATCHLIST -> Math.max(40, 58 - Math.min(maxDaysPastDue, 60) / 5);
            case NO_HIT -> 72;
            case CLEAR -> Math.max(68, 84 - Math.max(activeLoanCount - 1, 0) * 3);
        };
    }

    private CreditBureauLoanAccount toInternalLoanAccount(String identityNumber, LoanRecord loan) {
        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        LoanRepaymentSnapshot snapshot = contract != null
            ? repaymentScheduleService.snapshot(loan, contract, loan.customerId())
            : null;

        BigDecimal originalAmount = nonNegative(loan.approvedAmount() != null ? loan.approvedAmount() : loan.amount());
        BigDecimal outstandingBalance = snapshot != null
            ? nonNegative(snapshot.outstandingAmount())
            : originalAmount;
        BigDecimal monthlyPayment = contract != null && contract.monthlyPayment() != null
            ? nonNegative(contract.monthlyPayment())
            : nonNegative(loan.approvedMonthlyPayment());
        int daysPastDue = snapshot != null ? Math.toIntExact(Math.min(snapshot.overdueDays(), Integer.MAX_VALUE)) : 0;

        return new CreditBureauLoanAccount(
            null,
            identityNumber,
            INTERNAL_REPORTING_INSTITUTION,
            "APP-" + loan.id(),
            CreditLoanSourceType.INTERNAL_SYSTEM,
            buildInternalLoanCategory(loan),
            mapInternalStatus(loan, daysPastDue),
            originalAmount,
            outstandingBalance,
            monthlyPayment,
            Math.max(daysPastDue, 0),
            "Khoản vay nội bộ #" + loan.id(),
            Instant.now(),
            null
        );
    }

    private CreditLoanAccountStatus mapInternalStatus(LoanRecord loan, int daysPastDue) {
        if (loan.status() == LoanStatus.CLOSED) {
            return CreditLoanAccountStatus.CLOSED;
        }
        if (daysPastDue >= 90) {
            return CreditLoanAccountStatus.BAD_DEBT;
        }
        if (loan.status() == LoanStatus.OVERDUE || daysPastDue > 0) {
            return CreditLoanAccountStatus.OVERDUE;
        }
        return CreditLoanAccountStatus.CURRENT;
    }

    private String buildInternalLoanCategory(LoanRecord loan) {
        String loanType = switch (loan.loanType()) {
            case SECURED -> "Vay thế chấp";
            case UNSECURED -> "Vay tín chấp";
        };
        String purpose = switch (loan.purpose()) {
            case PERSONAL -> "tiêu dùng";
            case HOME -> "mua nhà";
            case EDUCATION -> "học tập";
            case BUSINESS -> "kinh doanh";
        };
        return loanType + " - " + purpose;
    }

    private boolean isActive(CreditLoanAccountStatus status) {
        return status == CreditLoanAccountStatus.CURRENT
            || status == CreditLoanAccountStatus.OVERDUE
            || status == CreditLoanAccountStatus.BAD_DEBT;
    }

    private CreditBureauRecordResponse toResponse(CreditBureauRecord record) {
        List<CreditBureauLoanAccountResponse> loanAccounts = creditBureauRepository.findLoanAccountsByIdentityNumber(record.identityNumber())
            .stream()
            .map(account -> new CreditBureauLoanAccountResponse(
                account.id(),
                account.reportingInstitution(),
                account.accountReference(),
                account.sourceType(),
                account.loanCategory(),
                account.accountStatus(),
                nonNegative(account.originalAmount()),
                nonNegative(account.outstandingBalance()),
                nonNegative(account.monthlyPayment()),
                valueOrZero(account.daysPastDue()),
                account.note(),
                account.reportedAt(),
                account.updatedAt()
            ))
            .toList();
        return new CreditBureauRecordResponse(
            record.identityNumber(),
            record.borrowerName(),
            record.bureauStatus(),
            record.creditScore(),
            record.activeLoanCount(),
            record.daysPastDue(),
            record.manualReviewRequired(),
            record.hardReject(),
            record.riskNote(),
            nonNegative(record.totalMonthlyObligation()),
            nonNegative(record.totalOutstandingBalance()),
            nonNegative(record.externalMonthlyObligation()),
            nonNegative(record.externalOutstandingBalance()),
            valueOrZero(record.reportingInstitutionCount()),
            record.consentGranted(),
            record.lastReportedAt(),
            loanAccounts,
            record.updatedAt()
        );
    }

    private String normalizeIdentityNumber(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD không được để trống");
        }
        String normalized = value.replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD không được để trống");
        }
        if (normalized.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD vượt quá độ dài cho phép");
        }
        return normalized;
    }

    private String normalizeNullableIdentityNumber(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeBorrowerName(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value.trim();
    }

    private String fallbackBorrowerName(String value, String identityNumber) {
        if (value == null || value.isBlank()) {
            return "Khách hàng " + identityNumber;
        }
        return value.trim();
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private record DerivedCreditMetrics(
        CreditBureauStatus status,
        Integer creditScore,
        Integer activeLoanCount,
        Integer maxDaysPastDue,
        BigDecimal totalMonthlyObligation,
        BigDecimal totalOutstandingBalance,
        BigDecimal externalMonthlyObligation,
        BigDecimal externalOutstandingBalance,
        Integer reportingInstitutionCount,
        boolean manualReviewRequired,
        boolean hardReject,
        Instant lastReportedAt
    ) {
    }

    private record SyncBorrowerContext(
        String identityNumber,
        String borrowerName,
        List<CreditBureauLoanAccount> internalAccounts
    ) {
    }
}
