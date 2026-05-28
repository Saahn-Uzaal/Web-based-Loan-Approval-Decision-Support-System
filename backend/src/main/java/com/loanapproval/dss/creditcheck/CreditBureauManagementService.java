package com.loanapproval.dss.creditcheck;

import com.loanapproval.dss.creditcheck.dto.CreditBureauRecordResponse;
import com.loanapproval.dss.creditcheck.dto.UpsertCreditBureauRecordRequest;
import com.loanapproval.dss.shared.PageResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditBureauManagementService {

    private final CreditBureauRepository creditBureauRepository;

    public CreditBureauManagementService(CreditBureauRepository creditBureauRepository) {
        this.creditBureauRepository = creditBureauRepository;
    }

    public PageResponse<CreditBureauRecordResponse> listPaged(
        CreditBureauStatus status,
        String query,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedQuery = normalizeQuery(query);
        long total = creditBureauRepository.count(status, normalizedQuery);
        List<CreditBureauRecordResponse> content = creditBureauRepository
            .findPaged(status, normalizedQuery, safePage * safeSize, safeSize)
            .stream()
            .map(this::toResponse)
            .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public CreditBureauRecordResponse create(UpsertCreditBureauRecordRequest request) {
        return save(null, request);
    }

    public CreditBureauRecordResponse update(String currentIdentityNumber, UpsertCreditBureauRecordRequest request) {
        return save(currentIdentityNumber, request);
    }

    public void delete(String identityNumber) {
        String normalizedIdentityNumber = normalizeIdentityNumber(identityNumber);
        if (creditBureauRepository.deleteByIdentityNumber(normalizedIdentityNumber) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu nợ xấu cần xóa");
        }
    }

    private CreditBureauRecordResponse save(String currentIdentityNumber, UpsertCreditBureauRecordRequest request) {
        String normalizedIdentityNumber = normalizeIdentityNumber(request.identityNumber());
        if (currentIdentityNumber != null) {
            String normalizedCurrentIdentityNumber = normalizeIdentityNumber(currentIdentityNumber);
            if (!normalizedCurrentIdentityNumber.equals(normalizedIdentityNumber)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể thay đổi CCCD của bản ghi tín dụng hiện có"
                );
            }
        }

        CreditBureauRecord saved = creditBureauRepository.upsert(new CreditBureauRecord(
            normalizedIdentityNumber,
            normalizeRequiredText(request.borrowerName(), "Tên người vay không được để trống"),
            request.bureauStatus(),
            request.creditScore(),
            request.activeLoanCount(),
            request.daysPastDue(),
            Boolean.TRUE.equals(request.manualReviewRequired()),
            Boolean.TRUE.equals(request.hardReject()),
            normalizeQuery(request.riskNote()),
            null
        ));
        return toResponse(saved);
    }

    private CreditBureauRecordResponse toResponse(CreditBureauRecord record) {
        return new CreditBureauRecordResponse(
            record.identityNumber(),
            record.borrowerName(),
            record.bureauStatus(),
            record.creditScore(),
            record.activeLoanCount(),
            record.daysPastDue(),
            record.manualReviewRequired(),
            record.hardReject(),
            record.riskNote(),
            record.updatedAt()
        );
    }

    private String normalizeIdentityNumber(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD không được để trống");
        }
        String normalized = value.replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD không được để trống");
        }
        if (normalized.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CCCD vượt quá độ dài cho phép");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value.trim();
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
