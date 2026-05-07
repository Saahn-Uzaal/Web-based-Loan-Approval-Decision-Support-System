package com.loanapproval.dss.repayment;

import com.loanapproval.dss.contract.LoanContract;
import com.loanapproval.dss.loan.LoanRecord;

public record LoanDelinquencyCandidate(
        LoanRecord loan,
        LoanContract contract) {
}
