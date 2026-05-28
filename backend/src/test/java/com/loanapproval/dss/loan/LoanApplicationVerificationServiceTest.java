package com.loanapproval.dss.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.loanapproval.dss.verification.CustomerVerification;
import com.loanapproval.dss.verification.VerificationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoanApplicationVerificationServiceTest {

    private final LoanApplicationVerificationService service =
            new LoanApplicationVerificationService(null);

    @Test
    void shouldSkipFaceMatchForSecuredLoan() {
        CustomerVerification verification = verificationWithFaceMatchStatus(VerificationStatus.PENDING);

        assertThat(service.isFullyVerified(LoanType.SECURED, verification)).isTrue();
    }

    @Test
    void shouldRequireFaceMatchForUnsecuredLoan() {
        CustomerVerification verification = verificationWithFaceMatchStatus(VerificationStatus.PENDING);

        assertThat(service.isFullyVerified(LoanType.UNSECURED, verification)).isFalse();
    }

    private CustomerVerification verificationWithFaceMatchStatus(VerificationStatus faceMatchStatus) {
        Instant now = Instant.now();
        return new CustomerVerification(
                1L,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                faceMatchStatus,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                VerificationStatus.PASSED,
                false,
                null,
                8L,
                now,
                now,
                now);
    }
}
