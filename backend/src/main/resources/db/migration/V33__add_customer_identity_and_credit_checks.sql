ALTER TABLE customer_profiles
    ADD COLUMN identity_number VARCHAR(20) NULL AFTER phone,
    ADD COLUMN identity_card_front_original_filename VARCHAR(255) NULL AFTER payslip_uploaded_at,
    ADD COLUMN identity_card_front_storage_name VARCHAR(255) NULL AFTER identity_card_front_original_filename,
    ADD COLUMN identity_card_front_content_type VARCHAR(120) NULL AFTER identity_card_front_storage_name,
    ADD COLUMN identity_card_front_file_size BIGINT NULL AFTER identity_card_front_content_type,
    ADD COLUMN identity_card_front_uploaded_at TIMESTAMP NULL AFTER identity_card_front_file_size,
    ADD COLUMN identity_card_back_original_filename VARCHAR(255) NULL AFTER identity_card_front_uploaded_at,
    ADD COLUMN identity_card_back_storage_name VARCHAR(255) NULL AFTER identity_card_back_original_filename,
    ADD COLUMN identity_card_back_content_type VARCHAR(120) NULL AFTER identity_card_back_storage_name,
    ADD COLUMN identity_card_back_file_size BIGINT NULL AFTER identity_card_back_content_type,
    ADD COLUMN identity_card_back_uploaded_at TIMESTAMP NULL AFTER identity_card_back_file_size;

CREATE UNIQUE INDEX uk_customer_profiles_identity_number
    ON customer_profiles (identity_number);

CREATE TABLE IF NOT EXISTS credit_bureau_records (
    identity_number VARCHAR(20) PRIMARY KEY,
    borrower_name VARCHAR(150) NULL,
    bureau_status ENUM('NO_HIT', 'CLEAR', 'WATCHLIST', 'BAD_DEBT', 'FRAUD_SUSPECT') NOT NULL DEFAULT 'CLEAR',
    credit_score INT NOT NULL,
    active_loan_count INT NOT NULL DEFAULT 0,
    days_past_due INT NOT NULL DEFAULT 0,
    manual_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    hard_reject BOOLEAN NOT NULL DEFAULT FALSE,
    risk_note VARCHAR(500) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_credit_checks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    identity_number VARCHAR(20) NOT NULL,
    matched_record BOOLEAN NOT NULL DEFAULT FALSE,
    bureau_status ENUM('NO_HIT', 'CLEAR', 'WATCHLIST', 'BAD_DEBT', 'FRAUD_SUSPECT') NOT NULL DEFAULT 'NO_HIT',
    credit_score INT NULL,
    active_loan_count INT NOT NULL DEFAULT 0,
    days_past_due INT NOT NULL DEFAULT 0,
    manual_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    hard_reject BOOLEAN NOT NULL DEFAULT FALSE,
    risk_note VARCHAR(500) NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'INTERNAL_BUREAU',
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_credit_checks_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_customer_credit_checks_customer_checked_at
    ON customer_credit_checks (customer_id, checked_at DESC, id DESC);
