package com.loanapproval.dss.loan;

import com.loanapproval.dss.dss.DecisionEngineService;
import com.loanapproval.dss.dss.DecisionInput;
import com.loanapproval.dss.dss.DssRecommendation;
import com.loanapproval.dss.dss.DssResult;
import com.loanapproval.dss.dss.DssResultRepository;
import com.loanapproval.dss.loan.dto.CreateLoanRequest;
import com.loanapproval.dss.loan.dto.LoanDetailResponse;
import com.loanapproval.dss.loan.dto.LoanSummaryResponse;
import com.loanapproval.dss.profile.CustomerProfile;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerLoanService {

    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final DecisionEngineService decisionEngineService;
    private final DssResultRepository dssResultRepository;

    public CustomerLoanService(
        LoanRepository loanRepository,
        CustomerProfileRepository customerProfileRepository,
        DecisionEngineService decisionEngineService,
        DssResultRepository dssResultRepository
    ) {
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.decisionEngineService = decisionEngineService;
        this.dssResultRepository = dssResultRepository;
    }

    @Transactional
    public LoanDetailResponse create(Long customerId, CreateLoanRequest request) {
        LoanRecord loan = loanRepository.create(
            customerId,
            request.amount(),
            request.termMonths(),
            request.purpose()
        );

        CustomerProfile profile = customerProfileRepository.findByUserId(customerId).orElse(null);
        DecisionInput decisionInput = new DecisionInput(
            customerId,
            profile != null ? profile.monthlyIncome() : null,
            profile != null ? profile.debtToIncomeRatio() : null,
            profile != null ? profile.employmentStatus() : null,
            request.amount(),
            request.termMonths(),
            request.purpose()
        );

        DssResult dssResult = decisionEngineService.evaluate(decisionInput);
        dssResultRepository.upsert(loan.id(), dssResult);

        if (dssResult.recommendation() == DssRecommendation.ESCALATE_RECOMMENDED) {
            loanRepository.updateStatus(loan.id(), LoanStatus.WAITING_SUPERVISOR);
            loan = loanRepository.findOwnedById(loan.id(), customerId).orElse(loan);
        }
        return toDetailResponse(loan);
    }

    public List<LoanSummaryResponse> listMine(Long customerId) {
        return loanRepository.findByCustomerId(customerId).stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    public LoanDetailResponse getMineById(Long customerId, Long id) {
        LoanRecord loan = loanRepository.findOwnedById(id, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan request not found"));
        return toDetailResponse(loan);
    }

    private LoanSummaryResponse toSummaryResponse(LoanRecord loan) {
        return new LoanSummaryResponse(
            loan.id(),
            loan.amount(),
            loan.termMonths(),
            loan.purpose(),
            loan.status(),
            loan.finalReason(),
            loan.createdAt()
        );
    }

    private LoanDetailResponse toDetailResponse(LoanRecord loan) {
        return new LoanDetailResponse(
            loan.id(),
            loan.customerId(),
            loan.amount(),
            loan.termMonths(),
            loan.purpose(),
            loan.status(),
            loan.finalReason(),
            loan.createdAt(),
            loan.updatedAt()
        );
    }
}
