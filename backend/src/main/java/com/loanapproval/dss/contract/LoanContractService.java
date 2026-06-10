package com.loanapproval.dss.contract;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.dto.LoanContractInstallmentResponse;
import com.loanapproval.dss.contract.dto.LoanContractResponse;
import com.loanapproval.dss.loan.LoanEligibilityService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanContractService {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    private final LoanContractRepository loanContractRepository;
    private final LoanRepository loanRepository;
    private final ComplianceAuditService complianceAuditService;
    private final LoanEligibilityService loanEligibilityService;
    private final NotificationService notificationService;
    private final LoanInstallmentService loanInstallmentService;

    public LoanContractService(
            LoanContractRepository loanContractRepository,
            LoanRepository loanRepository,
            ComplianceAuditService complianceAuditService,
            LoanEligibilityService loanEligibilityService,
            NotificationService notificationService,
            LoanInstallmentService loanInstallmentService) {
        this.loanContractRepository = loanContractRepository;
        this.loanRepository = loanRepository;
        this.complianceAuditService = complianceAuditService;
        this.loanEligibilityService = loanEligibilityService;
        this.notificationService = notificationService;
        this.loanInstallmentService = loanInstallmentService;
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(LoanRecord loan, Long actorUserId) {
        return createIfMissingFromApprovedLoan(
                loan,
                actorUserId,
                null,
                LoanContractStatus.PENDING_ACCEPTANCE);
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(
            LoanRecord loan,
            Long actorUserId,
            LoanContractScheduleTerms scheduleTerms) {
        return createIfMissingFromApprovedLoan(
                loan,
                actorUserId,
                scheduleTerms,
                LoanContractStatus.ACTIVE);
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(
            LoanRecord loan,
            Long actorUserId,
            LoanContractScheduleTerms scheduleTerms,
            LoanContractStatus desiredStatus) {
        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hồ sơ vay chưa ở trạng thái đã duyệt");
        }

        LoanContract existing = loanContractRepository.findByLoanRequestId(loan.id()).orElse(null);
        if (existing != null) {
            if (existing.status() == LoanContractStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hợp đồng vay đã bị hủy");
            }
            if (desiredStatus == LoanContractStatus.ACTIVE && existing.status() != LoanContractStatus.ACTIVE) {
                loanContractRepository.updateStatus(existing.id(), LoanContractStatus.ACTIVE);
                LoanContract updated = loanContractRepository.findById(existing.id()).orElse(existing);
                loanInstallmentService.ensureSchedule(updated);
                return updated;
            }
            loanInstallmentService.ensureSchedule(existing);
            return existing;
        }
        return createContract(loan, actorUserId, scheduleTerms, desiredStatus);
    }

    public LoanContractResponse getMine(Long customerId, Long loanRequestId) {
        LoanContract contract = getOrCreateContractForCustomer(customerId, loanRequestId);
        return toResponse(contract);
    }

    @Transactional
    public LoanContract activateForCustomer(Long customerId, Long loanRequestId) {
        LoanContract contract = getOrCreateContractForCustomer(customerId, loanRequestId);
        if (contract.status() == LoanContractStatus.ACTIVE) {
            loanInstallmentService.ensureSchedule(contract);
            return contract;
        }
        loanContractRepository.updateStatus(contract.id(), LoanContractStatus.ACTIVE);
        LoanContract updated = loanContractRepository.findById(contract.id()).orElse(contract);
        loanInstallmentService.ensureSchedule(updated);
        return updated;
    }

    public LoanContract findByLoanRequestId(Long loanRequestId) {
        LoanContract contract = loanContractRepository.findByLoanRequestId(loanRequestId).orElse(null);
        loanInstallmentService.ensureSchedule(contract);
        return contract;
    }

    @Transactional
    public void closeContract(Long contractId) {
        loanContractRepository.updateStatus(contractId, LoanContractStatus.CLOSED);
    }

    @Transactional
    public void cancelPendingAcceptance(Long loanRequestId) {
        LoanContract contract = loanContractRepository.findByLoanRequestId(loanRequestId).orElse(null);
        if (contract != null && contract.status() == LoanContractStatus.PENDING_ACCEPTANCE) {
            loanContractRepository.updateStatus(contract.id(), LoanContractStatus.CANCELLED);
        }
    }

    public BigDecimal calculateProjectedMonthlyPayment(BigDecimal principalAmount, Integer termMonths) {
        return calculateProjectedMonthlyPayment(LoanType.UNSECURED, principalAmount, termMonths);
    }

    public BigDecimal calculateProjectedMonthlyPayment(
            LoanType loanType,
            BigDecimal principalAmount,
            Integer termMonths) {
        return calculateMonthlyPayment(principalAmount, termMonths, defaultAnnualRate(loanType));
    }

    public LoanContractResponse toResponse(LoanContract contract) {
        loanInstallmentService.ensureSchedule(contract);
        List<LoanContractInstallmentResponse> installments = loanInstallmentService.listByContractId(contract.id())
                .stream()
                .map(installment -> new LoanContractInstallmentResponse(
                        installment.installmentNumber(),
                        installment.dueDate(),
                        installment.openingPrincipal(),
                        installment.scheduledPrincipal(),
                        installment.scheduledInterest(),
                        installment.scheduledFee(),
                        installment.scheduledAmount(),
                        installment.paidFee(),
                        installment.paidAmount(),
                        installment.remainingFee(),
                        installment.remainingAmount(),
                        installment.status(),
                        installment.lastPaidAt()))
                .toList();
        return new LoanContractResponse(
                contract.id(),
                contract.loanRequestId(),
                contract.principalAmount(),
                contract.annualInterestRate(),
                contract.termMonths(),
                contract.startDate(),
                contract.endDate(),
                contract.firstPaymentDate(),
                contract.monthlyPaymentDay(),
                contract.finalPaymentDate(),
                contract.monthlyPayment(),
                contract.totalInterest(),
                contract.status(),
                contract.createdAt(),
                installments);
    }

    private LoanContract getOrCreateContractForCustomer(Long customerId, Long loanRequestId) {
        LoanRecord loan = loanRepository.findOwnedById(loanRequestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng vay"));
        LoanContract existing = loanContractRepository.findByLoanRequestIdAndCustomerId(loanRequestId, customerId)
                .orElse(null);
        if (existing != null) {
            if (existing.status() == LoanContractStatus.CANCELLED || !canCustomerAccessContract(loan.status())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng vay");
            }
            return existing;
        }

        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng vay");
        }
        return createIfMissingFromApprovedLoan(loan, customerId);
    }

    private boolean canCustomerAccessContract(LoanStatus loanStatus) {
        return loanStatus == LoanStatus.APPROVED
                || loanStatus == LoanStatus.CONTRACTED
                || loanStatus == LoanStatus.ACTIVE
                || loanStatus == LoanStatus.OVERDUE
                || loanStatus == LoanStatus.CLOSED;
    }

    private LoanContract createContract(
            LoanRecord loan,
            Long actorUserId,
            LoanContractScheduleTerms scheduleTerms,
            LoanContractStatus contractStatus) {
        BigDecimal principalAmount = loan.approvedAmount() != null ? loan.approvedAmount() : loan.amount();
        Integer termMonths = loan.approvedTermMonths() != null ? loan.approvedTermMonths() : loan.termMonths();
        BigDecimal annualRate = sanitizeAnnualRate(resolveAnnualRate(loan, scheduleTerms));
        BigDecimal monthlyPayment = resolveMonthlyPayment(loan, scheduleTerms, principalAmount, termMonths, annualRate);
        BigDecimal totalInterest = monthlyPayment
                .multiply(BigDecimal.valueOf(termMonths), MATH_CONTEXT)
                .subtract(principalAmount, MATH_CONTEXT)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate startDate = scheduleTerms != null && scheduleTerms.startDate() != null
                ? scheduleTerms.startDate()
                : LocalDate.now();
        LocalDate firstPaymentDate = scheduleTerms != null && scheduleTerms.firstPaymentDate() != null
                ? scheduleTerms.firstPaymentDate()
                : startDate.plusMonths(1);
        LocalDate finalPaymentDate = scheduleTerms != null && scheduleTerms.finalPaymentDate() != null
                ? scheduleTerms.finalPaymentDate()
                : firstPaymentDate.plusMonths(Math.max(termMonths.longValue() - 1, 0));
        String monthlyPaymentDay = scheduleTerms != null && scheduleTerms.monthlyPaymentDay() != null
                ? scheduleTerms.monthlyPaymentDay().trim()
                : String.valueOf(firstPaymentDate.getDayOfMonth());
        LocalDate endDate = finalPaymentDate;

        LoanContract created = loanContractRepository.create(
                loan.id(),
                loan.customerId(),
                principalAmount,
                annualRate,
                termMonths,
                startDate,
                endDate,
                firstPaymentDate,
                monthlyPaymentDay,
                finalPaymentDate,
                monthlyPayment,
                totalInterest,
                contractStatus);
        loanInstallmentService.ensureSchedule(created);

        complianceAuditService.log(
                loan.customerId(),
                loan.id(),
                actorUserId,
                "LOAN_CONTRACT_CREATED",
                ComplianceOutcome.INFO,
                String.format(
                        "Contract created with annualRate=%s, termMonths=%d, monthlyPayment=%s, firstPaymentDate=%s, finalPaymentDate=%s",
                        annualRate.toPlainString(),
                        termMonths,
                        monthlyPayment.toPlainString(),
                        firstPaymentDate,
                        finalPaymentDate));
        notificationService.notifyCustomerContractCreated(
                loan.id(),
                loan.customerId(),
                actorUserId,
                loan.loanType());

        return created;
    }

    private BigDecimal resolveAnnualRate(LoanRecord loan, LoanContractScheduleTerms scheduleTerms) {
        if (scheduleTerms != null && scheduleTerms.annualInterestRate() != null) {
            return scheduleTerms.annualInterestRate();
        }
        if (loan.approvedAnnualRate() != null) {
            return loan.approvedAnnualRate();
        }
        return defaultAnnualRate(loan.loanType());
    }

    private BigDecimal resolveMonthlyPayment(
            LoanRecord loan,
            LoanContractScheduleTerms scheduleTerms,
            BigDecimal principalAmount,
            Integer termMonths,
            BigDecimal annualRate) {
        if (scheduleTerms != null && scheduleTerms.monthlyPayment() != null) {
            return scheduleTerms.monthlyPayment().setScale(2, RoundingMode.HALF_UP);
        }
        if (loan.approvedMonthlyPayment() != null) {
            return loan.approvedMonthlyPayment().setScale(2, RoundingMode.HALF_UP);
        }
        return calculateMonthlyPayment(principalAmount, termMonths, annualRate);
    }

    private BigDecimal calculateMonthlyPayment(
            BigDecimal principalAmount,
            Integer termMonths,
            BigDecimal annualInterestRate) {
        if (principalAmount == null || termMonths == null || termMonths <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền gốc hoặc kỳ hạn vay không hợp lệ");
        }

        BigDecimal principal = principalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyRate = annualInterestRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusRPowerN = BigDecimal.ONE.add(monthlyRate, MATH_CONTEXT).pow(termMonths, MATH_CONTEXT);
        BigDecimal numerator = principal.multiply(monthlyRate, MATH_CONTEXT).multiply(onePlusRPowerN, MATH_CONTEXT);
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE, MATH_CONTEXT);

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tính khoản thanh toán hằng tháng");
        }

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sanitizeAnnualRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return annualRate.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAnnualRate(LoanType loanType) {
        if (loanType != null) {
            return loanEligibilityService.defaultAnnualRate(loanType);
        }
        return loanEligibilityService.defaultAnnualRate(LoanType.UNSECURED);
    }
}

