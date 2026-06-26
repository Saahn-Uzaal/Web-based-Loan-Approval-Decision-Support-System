package com.loanapproval.dss.creditcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.contract.LoanContractService;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauLoanAccountRequest;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauRecordRequest;
import com.loanapproval.dss.loan.LoanRepository;
import com.loanapproval.dss.profile.CustomerProfileRepository;
import com.loanapproval.dss.repayment.RepaymentScheduleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CreditBureauManagementServiceTest {

    @Mock
    private CreditBureauRepository creditBureauRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanContractService loanContractService;
    @Mock
    private RepaymentScheduleService repaymentScheduleService;
    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @InjectMocks
    private CreditBureauManagementService creditBureauManagementService;

    @Test
    void listPagedShouldApplySafePagingBounds() {
        when(creditBureauRepository.count(null, null)).thenReturn(1L);
        when(creditBureauRepository.findPaged(null, null, 0, 100)).thenReturn(List.of(record("079094001234")));
        when(creditBureauRepository.findLoanAccountsByIdentityNumber("079094001234")).thenReturn(List.of());

        var page = creditBureauManagementService.listPaged(null, "   ", -3, 999);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        verify(creditBureauRepository).findPaged(null, null, 0, 100);
    }

    @Test
    void createShouldNormalizeIdentityNumberBeforeSaving() {
        when(creditBureauRepository.upsert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditBureauRepository.findLoanAccountsByIdentityNumber("079094001234")).thenReturn(List.of());

        creditBureauManagementService.create(new UpsertCreditBureauRecordRequest(
            "0790 9400 1234",
            "Nguyễn Minh An",
            true,
            false,
            "Có lịch sử nợ quá hạn",
            List.of(loanAccount(
                "Ngân hàng A",
                CreditLoanSourceType.PARTNER_NETWORK,
                CreditLoanAccountStatus.BAD_DEBT,
                45
            ))
        ));

        ArgumentCaptor<CreditBureauRecord> recordCaptor = ArgumentCaptor.forClass(CreditBureauRecord.class);
        verify(creditBureauRepository).upsert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().identityNumber()).isEqualTo("079094001234");
        assertThat(recordCaptor.getValue().bureauStatus()).isEqualTo(CreditBureauStatus.BAD_DEBT);
        assertThat(recordCaptor.getValue().activeLoanCount()).isEqualTo(1);
    }

    @Test
    void updateShouldRejectIdentityNumberChange() {
        assertThatThrownBy(() -> creditBureauManagementService.update(
            "079094001234",
            new UpsertCreditBureauRecordRequest(
                "079094009999",
                "Nguyễn Minh An",
                true,
                false,
                "Có lịch sử nợ quá hạn",
                List.of()
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private CreditBureauRecord record(String identityNumber) {
        return new CreditBureauRecord(
            identityNumber,
            "Nguyễn Minh An",
            CreditBureauStatus.BAD_DEBT,
            35,
            2,
            30,
            true,
            false,
            "Có lịch sử nợ quá hạn",
            java.math.BigDecimal.valueOf(7_500_000),
            java.math.BigDecimal.valueOf(65_000_000),
            java.math.BigDecimal.valueOf(7_500_000),
            java.math.BigDecimal.valueOf(65_000_000),
            1,
            true,
            Instant.now(),
            Instant.now()
        );
    }

    private UpsertCreditBureauLoanAccountRequest loanAccount(
        String institution,
        CreditLoanSourceType sourceType,
        CreditLoanAccountStatus status,
        int daysPastDue
    ) {
        return new UpsertCreditBureauLoanAccountRequest(
            institution,
            "HD-001",
            sourceType,
            "Vay tiêu dùng",
            status,
            java.math.BigDecimal.valueOf(80_000_000),
            java.math.BigDecimal.valueOf(65_000_000),
            java.math.BigDecimal.valueOf(7_500_000),
            daysPastDue,
            "Khoản vay thử nghiệm"
        );
    }
}
