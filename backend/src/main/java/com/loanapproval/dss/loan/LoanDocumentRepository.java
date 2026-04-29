package com.loanapproval.dss.loan;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class LoanDocumentRepository {

    private static final RowMapper<LoanDocumentRecord> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new LoanDocumentRecord(
            rs.getLong("id"),
            rs.getLong("loan_request_id"),
            LoanDocumentType.valueOf(rs.getString("document_type")),
            rs.getString("original_file_name"),
            rs.getString("storage_name"),
            rs.getString("content_type"),
            (Long) rs.getObject("file_size"),
            toInstant(rs.getTimestamp("uploaded_at")));

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertDocument;

    public LoanDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertDocument = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("loan_request_documents")
                .usingColumns(
                        "loan_request_id",
                        "document_type",
                        "original_file_name",
                        "storage_name",
                        "content_type",
                        "file_size",
                        "uploaded_at")
                .usingGeneratedKeyColumns("id");
    }

    public LoanDocumentRecord create(
            Long loanRequestId,
            LoanDocumentType documentType,
            LoanDocumentStorageService.StoredLoanDocument document) {
        Map<String, Object> values = new HashMap<>();
        values.put("loan_request_id", loanRequestId);
        values.put("document_type", documentType.name());
        values.put("original_file_name", document.originalFileName());
        values.put("storage_name", document.storageName());
        values.put("content_type", document.contentType());
        values.put("file_size", document.fileSize());
        values.put("uploaded_at", Timestamp.from(document.uploadedAt()));

        Number id = insertDocument.executeAndReturnKey(values);
        return findById(id.longValue())
                .orElseThrow(() -> new IllegalStateException("Created loan document was not found"));
    }

    public List<LoanDocumentRecord> findByLoanRequestId(Long loanRequestId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            loan_request_id,
                            document_type,
                            original_file_name,
                            storage_name,
                            content_type,
                            file_size,
                            uploaded_at
                        FROM loan_request_documents
                        WHERE loan_request_id = ?
                        ORDER BY id ASC
                        """,
                DOCUMENT_ROW_MAPPER,
                loanRequestId);
    }

    public Optional<LoanDocumentRecord> findByLoanRequestIdAndType(
            Long loanRequestId,
            LoanDocumentType documentType) {
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            loan_request_id,
                            document_type,
                            original_file_name,
                            storage_name,
                            content_type,
                            file_size,
                            uploaded_at
                        FROM loan_request_documents
                        WHERE loan_request_id = ? AND document_type = ?
                        """,
                DOCUMENT_ROW_MAPPER,
                loanRequestId,
                documentType.name()).stream().findFirst();
    }

    private Optional<LoanDocumentRecord> findById(Long id) {
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            loan_request_id,
                            document_type,
                            original_file_name,
                            storage_name,
                            content_type,
                            file_size,
                            uploaded_at
                        FROM loan_request_documents
                        WHERE id = ?
                        """,
                DOCUMENT_ROW_MAPPER,
                id).stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
