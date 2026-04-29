package com.loanapproval.dss.contract;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.dto.LoanContractResponse;
import com.loanapproval.dss.loan.LoanEligibilityService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.loan.LoanType;
import com.loanapproval.dss.notification.NotificationService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanContractService {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    private final LoanContractRepository loanContractRepository;
    private final ComplianceAuditService complianceAuditService;
    private final BigDecimal defaultAnnualInterestRate;
    private final LoanEligibilityService loanEligibilityService;
    private final NotificationService notificationService;

    public LoanContractService(
            LoanContractRepository loanContractRepository,
            ComplianceAuditService complianceAuditService,
            @Value("${app.loan.default-annual-interest-rate:0.12}") BigDecimal defaultAnnualInterestRate,
            LoanEligibilityService loanEligibilityService,
            NotificationService notificationService) {
        this.loanContractRepository = loanContractRepository;
        this.complianceAuditService = complianceAuditService;
        this.defaultAnnualInterestRate = defaultAnnualInterestRate;
        this.loanEligibilityService = loanEligibilityService;
        this.notificationService = notificationService;
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(LoanRecord loan, Long actorUserId) {
        return createIfMissingFromApprovedLoan(loan, actorUserId, null);
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(
            LoanRecord loan,
            Long actorUserId,
            LoanContractScheduleTerms scheduleTerms) {
        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hồ sơ vay chưa ở trạng thái đã duyệt");
        }

        return loanContractRepository.findByLoanRequestId(loan.id())
                .orElseGet(() -> createContract(loan, actorUserId, scheduleTerms));
    }

    public LoanContractResponse getMine(Long customerId, Long loanRequestId) {
        LoanContract contract = loanContractRepository.findByLoanRequestIdAndCustomerId(loanRequestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng vay"));
        return toResponse(contract);
    }

    public LoanContract findByLoanRequestId(Long loanRequestId) {
        return loanContractRepository.findByLoanRequestId(loanRequestId).orElse(null);
    }

    @Transactional
    public void closeContract(Long contractId) {
        loanContractRepository.updateStatus(contractId, LoanContractStatus.CLOSED);
    }

    public BigDecimal calculateProjectedMonthlyPayment(BigDecimal principalAmount, Integer termMonths) {
        return calculateMonthlyPayment(principalAmount, termMonths, defaultAnnualInterestRate);
    }

    public LoanContractResponse toResponse(LoanContract contract) {
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
                contract.createdAt());
    }

    private LoanContract createContract(LoanRecord loan, Long actorUserId, LoanContractScheduleTerms scheduleTerms) {
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
                totalInterest);

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
        return defaultAnnualInterestRate;
    }
}

