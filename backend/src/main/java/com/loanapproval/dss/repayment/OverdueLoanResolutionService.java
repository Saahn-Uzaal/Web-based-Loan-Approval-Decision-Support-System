package com.loanapproval.dss.repayment;

import com.loanapproval.dss.compliance.ComplianceAuditService;
import com.loanapproval.dss.compliance.ComplianceOutcome;
import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.contract.LoanContractRepository;
import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.contract.LoanContractStatus;
import com.loanapproval.dss.contract.LoanInstallment;
import com.loanapproval.dss.contract.LoanInstallmentRepository;
import com.loanapproval.dss.contract.LoanInstallmentService;
import com.loanapproval.dss.loan.LoanRecord;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.loan.LoanStatus;
import com.loanapproval.dss.notification.NotificationService;
import com.loanapproval.dss.staff.dto.ResolveOverdueLoanRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OverdueLoanResolutionService {

    private final LoanRepository loanRepository;
    private final LoanContractService loanContractService;
    private final LoanContractRepository loanContractRepository;
    private final LoanInstallmentService loanInstallmentService;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final LoanDelinquencyService loanDelinquencyService;
    private final ComplianceAuditService complianceAuditService;
    private final NotificationService notificationService;

    public OverdueLoanResolutionService(
            LoanRepository loanRepository,
            LoanContractService loanContractService,
            LoanContractRepository loanContractRepository,
            LoanInstallmentService loanInstallmentService,
            LoanInstallmentRepository loanInstallmentRepository,
            RepaymentScheduleService repaymentScheduleService,
            LoanDelinquencyService loanDelinquencyService,
            ComplianceAuditService complianceAuditService,
            NotificationService notificationService) {
        this.loanRepository = loanRepository;
        this.loanContractService = loanContractService;
        this.loanContractRepository = loanContractRepository;
        this.loanInstallmentService = loanInstallmentService;
        this.loanInstallmentRepository = loanInstallmentRepository;
        this.repaymentScheduleService = repaymentScheduleService;
        this.loanDelinquencyService = loanDelinquencyService;
        this.complianceAuditService = complianceAuditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public LoanRepaymentSnapshot resolve(
            Long staffUserId,
            LoanRecord loan,
            ResolveOverdueLoanRequest request) {
        if (loan == null || loan.status() != LoanStatus.OVERDUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ các khoản vay đang ở trạng thái quá hạn mới được cơ cấu hoặc miễn phí chậm trả.");
        }

        int extensionDays = request.extensionDays() != null ? request.extensionDays() : 0;
        BigDecimal waivedLateFeeAmount = moneyOrZero(request.waivedLateFeeAmount());
        String reason = request.reason().trim();
        if (extensionDays <= 0 && waivedLateFeeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập ít nhất một trong hai giá trị: số ngày gia hạn hoặc số tiền miễn phí chậm trả.");
        }

        LoanContract contract = loanContractService.findByLoanRequestId(loan.id());
        if (contract == null || contract.status() != LoanContractStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoản vay quá hạn phải có hợp đồng đang hiệu lực mới được xử lý cơ cấu nợ.");
        }

        LocalDate today = LocalDate.now();
        loanInstallmentService.rebuildLedger(contract, today);
        LoanInstallment overdueInstallment = loanInstallmentService.listByLoanRequestId(loan.id()).stream()
                .filter(installment -> installment.remainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không tìm thấy kỳ đang nợ để xử lý cơ cấu hoặc miễn phí."));
        if (overdueInstallment.dueDate() == null || !overdueInstallment.dueDate().isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khoản vay hiện không còn kỳ nào quá hạn để xử lý cơ cấu.");
        }

        BigDecimal currentLateFeeDue = overdueInstallment.remainingFee();
        if (waivedLateFeeAmount.compareTo(currentLateFeeDue) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền miễn phí chậm trả không được vượt phần phí trễ hạn còn lại của kỳ hiện tại.");
        }

        if (extensionDays > 0) {
            loanInstallmentRepository.shiftDueDatesForOpenInstallments(
                    loan.id(),
                    overdueInstallment.installmentNumber(),
                    extensionDays);
            loanContractRepository.shiftFinalScheduleDates(contract.id(), extensionDays);
        }
        if (waivedLateFeeAmount.compareTo(BigDecimal.ZERO) > 0) {
            loanInstallmentRepository.waiveScheduledFee(
                    loan.id(),
                    overdueInstallment.installmentNumber(),
                    waivedLateFeeAmount);
        }

        LoanContract refreshedContract = loanContractService.findByLoanRequestId(loan.id());
        loanInstallmentService.rebuildLedger(refreshedContract, today);
        loanDelinquencyService.assessLoan(loan.id(), today);

        LoanRecord refreshedLoan = loanRepository.findById(loan.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản vay sau khi xử lý"));
        LoanRepaymentSnapshot snapshot = repaymentScheduleService.snapshot(
                refreshedLoan,
                refreshedContract,
                refreshedLoan.customerId(),
                today);

        complianceAuditService.log(
                refreshedLoan.customerId(),
                refreshedLoan.id(),
                staffUserId,
                "OVERDUE_LOAN_RESOLUTION",
                ComplianceOutcome.INFO,
                String.format(
                        "extensionDays=%d, waivedLateFee=%s, reason=%s",
                        extensionDays,
                        waivedLateFeeAmount.toPlainString(),
                        reason));
        notificationService.notifyCustomerOverdueResolutionApplied(
                refreshedLoan.id(),
                refreshedLoan.customerId(),
                staffUserId,
                extensionDays > 0 ? extensionDays : null,
                waivedLateFeeAmount,
                refreshedLoan.status() == LoanStatus.OVERDUE,
                reason);

        return snapshot;
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền miễn phí chậm trả không được âm.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
