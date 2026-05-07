ALTER TABLE loan_requests
    MODIFY COLUMN status ENUM(
        'DRAFT',
        'PENDING',
        'NEEDS_MORE_INFO',
        'APPOINTMENT_SCHEDULED',
        'APPROVED',
        'CONTRACTED',
        'DISBURSED',
        'ACTIVE',
        'CLOSED',
        'REJECTED',
        'WITHDRAWN'
    ) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN accepted_at TIMESTAMP NULL AFTER decision_policy_version,
    ADD COLUMN accepted_terms_version VARCHAR(40) NULL AFTER accepted_at;

ALTER TABLE decision_audits
    MODIFY COLUMN action ENUM('APPROVE', 'REJECT', 'REQUEST_MORE_INFO') NOT NULL;

ALTER TABLE loan_appointments
    MODIFY COLUMN status ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') NOT NULL DEFAULT 'SCHEDULED';

CREATE TABLE IF NOT EXISTS loan_application_snapshots (
    loan_request_id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    full_name VARCHAR(150),
    phone VARCHAR(30),
    date_of_birth DATE NULL,
    declared_monthly_income DECIMAL(15,2),
    verified_monthly_income DECIMAL(15,2),
    debt_to_income_ratio DECIMAL(5,2),
    employment_status VARCHAR(100),
    employment_start_date DATE NULL,
    credit_history_score INT NULL,
    payment_rating INT,
    active_monthly_debt DECIMAL(15,2) NOT NULL DEFAULT 0,
    active_debt_count INT NOT NULL DEFAULT 0,
    information_verification_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    document_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    identity_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    face_match_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    income_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    kyc_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    aml_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    fraud_flag BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_application_snapshots_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_application_snapshots_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);
