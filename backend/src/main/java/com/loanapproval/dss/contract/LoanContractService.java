package com.loanapproval.dss.contract;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.dto.LoanContractResponse;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanStatus;
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

    public LoanContractService(
        LoanContractRepository loanContractRepository,
        ComplianceAuditService complianceAuditService,
        @Value("${app.loan.default-annual-interest-rate:0.12}") BigDecimal defaultAnnualInterestRate
    ) {
        this.loanContractRepository = loanContractRepository;
        this.complianceAuditService = complianceAuditService;
        this.defaultAnnualInterestRate = defaultAnnualInterestRate;
    }

    @Transactional
    public LoanContract createIfMissingFromApprovedLoan(LoanRecord loan, Long actorUserId) {
        if (loan.status() != LoanStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan request is not approved");
        }

        return loanContractRepository.findByLoanRequestId(loan.id())
            .orElseGet(() -> createContract(loan, actorUserId));
    }

    public LoanContractResponse getMine(Long customerId, Long loanRequestId) {
        LoanContract contract = loanContractRepository.findByLoanRequestIdAndCustomerId(loanRequestId, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan contract not found"));
        return toResponse(contract);
    }

    public LoanContract findByLoanRequestId(Long loanRequestId) {
        return loanContractRepository.findByLoanRequestId(loanRequestId).orElse(null);
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
            contract.monthlyPayment(),
            contract.totalInterest(),
            contract.status(),
            contract.createdAt()
        );
    }

    private LoanContract createContract(LoanRecord loan, Long actorUserId) {
        BigDecimal annualRate = sanitizeAnnualRate(defaultAnnualInterestRate);
        BigDecimal monthlyPayment = calculateMonthlyPayment(loan.amount(), loan.termMonths(), annualRate);
        BigDecimal totalInterest = monthlyPayment
            .multiply(BigDecimal.valueOf(loan.termMonths()), MATH_CONTEXT)
            .subtract(loan.amount(), MATH_CONTEXT)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(loan.termMonths().longValue());

        LoanContract created = loanContractRepository.create(
            loan.id(),
            loan.customerId(),
            loan.amount(),
            annualRate,
            loan.termMonths(),
            startDate,
            endDate,
            monthlyPayment,
            totalInterest
        );

        complianceAuditService.log(
            loan.customerId(),
            loan.id(),
            actorUserId,
            "LOAN_CONTRACT_CREATED",
            ComplianceOutcome.INFO,
            String.format(
                "Contract created with annualRate=%s, termMonths=%d, monthlyPayment=%s",
                annualRate.toPlainString(),
                loan.termMonths(),
                monthlyPayment.toPlainString()
            )
        );

        return created;
    }

    private BigDecimal calculateMonthlyPayment(
        BigDecimal principalAmount,
        Integer termMonths,
        BigDecimal annualInterestRate
    ) {
        if (principalAmount == null || termMonths == null || termMonths <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan principal or term is invalid");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to calculate monthly payment");
        }

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sanitizeAnnualRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return annualRate.setScale(6, RoundingMode.HALF_UP);
    }
}
