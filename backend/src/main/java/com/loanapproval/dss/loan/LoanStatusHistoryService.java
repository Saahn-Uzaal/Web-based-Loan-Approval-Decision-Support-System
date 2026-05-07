package com.loanapproval.dss.loan;

import org.springframework.stereotype.Service;

@Service
public class LoanStatusHistoryService {

    private final LoanStatusHistoryRepository loanStatusHistoryRepository;

    public LoanStatusHistoryService(LoanStatusHistoryRepository loanStatusHistoryRepository) {
        this.loanStatusHistoryRepository = loanStatusHistoryRepository;
    }

    public void recordTransition(
            LoanRecord currentLoan,
            LoanStatus toStatus,
            Long changedByUserId,
            String source,
            String changeReason) {
        if (currentLoan == null || toStatus == null || currentLoan.status() == toStatus) {
            return;
        }
        loanStatusHistoryRepository.create(
                currentLoan.id(),
                currentLoan.status(),
                toStatus,
                changeReason,
                changedByUserId,
                source);
    }

    public void recordCreation(
            LoanRecord createdLoan,
            Long changedByUserId,
            String source,
            String changeReason) {
        if (createdLoan == null || createdLoan.status() == null) {
            return;
        }
        loanStatusHistoryRepository.create(
                createdLoan.id(),
                null,
                createdLoan.status(),
                changeReason,
                changedByUserId,
                source);
    }
}
