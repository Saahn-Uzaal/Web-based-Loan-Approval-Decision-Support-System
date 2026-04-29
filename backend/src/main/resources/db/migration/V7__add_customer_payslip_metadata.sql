ALTER TABLE customer_profiles
    ADD COLUMN payslip_original_filename VARCHAR(255) NULL AFTER credit_history_score,
    ADD COLUMN payslip_storage_name VARCHAR(255) NULL AFTER payslip_original_filename,
    ADD COLUMN payslip_content_type VARCHAR(120) NULL AFTER payslip_storage_name,
    ADD COLUMN payslip_file_size BIGINT NULL AFTER payslip_content_type,
    ADD COLUMN payslip_uploaded_at TIMESTAMP NULL AFTER payslip_file_size;
