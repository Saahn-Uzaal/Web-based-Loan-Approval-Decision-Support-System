ALTER TABLE credit_bureau_records
    ADD COLUMN total_monthly_obligation DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER active_loan_count,
    ADD COLUMN total_outstanding_balance DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER total_monthly_obligation,
    ADD COLUMN external_monthly_obligation DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER total_outstanding_balance,
    ADD COLUMN external_outstanding_balance DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER external_monthly_obligation,
    ADD COLUMN reporting_institution_count INT NOT NULL DEFAULT 0 AFTER external_outstanding_balance,
    ADD COLUMN consent_granted BOOLEAN NOT NULL DEFAULT FALSE AFTER reporting_institution_count,
    ADD COLUMN last_reported_at TIMESTAMP NULL AFTER consent_granted;

CREATE TABLE IF NOT EXISTS credit_bureau_loan_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    identity_number VARCHAR(20) NOT NULL,
    reporting_institution VARCHAR(150) NOT NULL,
    account_reference VARCHAR(100) NULL,
    source_type ENUM('INTERNAL_SYSTEM', 'PARTNER_NETWORK', 'CUSTOMER_DECLARED') NOT NULL DEFAULT 'CUSTOMER_DECLARED',
    loan_category VARCHAR(80) NULL,
    account_status ENUM('CURRENT', 'OVERDUE', 'BAD_DEBT', 'CLOSED') NOT NULL DEFAULT 'CURRENT',
    original_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    outstanding_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    monthly_payment DECIMAL(15,2) NOT NULL DEFAULT 0,
    days_past_due INT NOT NULL DEFAULT 0,
    note VARCHAR(300) NULL,
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_bureau_loan_accounts_identity
        FOREIGN KEY (identity_number) REFERENCES credit_bureau_records(identity_number)
        ON DELETE CASCADE
);

CREATE INDEX idx_credit_bureau_loan_accounts_identity
    ON credit_bureau_loan_accounts(identity_number, account_status, source_type);

ALTER TABLE customer_credit_checks
    ADD COLUMN total_monthly_obligation DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER active_loan_count,
    ADD COLUMN total_outstanding_balance DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER total_monthly_obligation,
    ADD COLUMN external_monthly_obligation DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER total_outstanding_balance,
    ADD COLUMN external_outstanding_balance DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER external_monthly_obligation,
    ADD COLUMN reporting_institution_count INT NOT NULL DEFAULT 0 AFTER external_outstanding_balance;
