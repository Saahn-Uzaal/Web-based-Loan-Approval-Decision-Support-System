package com.loanapproval.dss.creditcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauRecordRequest;
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

    @InjectMocks
    private CreditBureauManagementService creditBureauManagementService;

    @Test
    void listPagedShouldApplySafePagingBounds() {
        when(creditBureauRepository.count(null, null)).thenReturn(1L);
        when(creditBureauRepository.findPaged(null, null, 0, 100)).thenReturn(List.of(record("079094001234")));

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

        creditBureauManagementService.create(new UpsertCreditBureauRecordRequest(
            "0790 9400 1234",
            "Nguyễn Minh An",
            CreditBureauStatus.BAD_DEBT,
            35,
            3,
            45,
            true,
            false,
            "Có lịch sử nợ quá hạn"
        ));

        ArgumentCaptor<CreditBureauRecord> recordCaptor = ArgumentCaptor.forClass(CreditBureauRecord.class);
        verify(creditBureauRepository).upsert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().identityNumber()).isEqualTo("079094001234");
    }

    @Test
    void updateShouldRejectIdentityNumberChange() {
        assertThatThrownBy(() -> creditBureauManagementService.update(
            "079094001234",
            new UpsertCreditBureauRecordRequest(
                "079094009999",
                "Nguyễn Minh An",
                CreditBureauStatus.BAD_DEBT,
                35,
                3,
                45,
                true,
                false,
                "Có lịch sử nợ quá hạn"
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
            Instant.now()
        );
    }
}
