ALTER TABLE customer_profiles
    ADD COLUMN date_of_birth DATE NULL AFTER phone,
    ADD COLUMN employment_start_date DATE NULL AFTER employment_status,
    ADD COLUMN credit_history_score INT NULL AFTER employment_start_date;

CREATE TABLE IF NOT EXISTS customer_debts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    debt_type VARCHAR(100) NOT NULL,
    monthly_payment DECIMAL(15,2) NOT NULL,
    remaining_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    lender_name VARCHAR(120),
    status ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_debts_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_customer_debts_customer_status
ON customer_debts(customer_id, status);

CREATE TABLE IF NOT EXISTS customer_verifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL UNIQUE,
    document_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    identity_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    income_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    kyc_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    aml_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    fraud_flag BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(500),
    verified_by BIGINT,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_verifications_customer
        FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_customer_verifications_staff
        FOREIGN KEY (verified_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS risk_assessments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL UNIQUE,
    credit_risk_score INT NOT NULL,
    fraud_risk_score INT NOT NULL,
    operational_risk_score INT NOT NULL,
    overall_risk_level ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    risk_reasons TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_assessments_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id)
);

CREATE TABLE IF NOT EXISTS loan_contracts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    principal_amount DECIMAL(15,2) NOT NULL,
    annual_interest_rate DECIMAL(8,6) NOT NULL,
    term_months INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    monthly_payment DECIMAL(15,2) NOT NULL,
    total_interest DECIMAL(15,2) NOT NULL,
    status ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_contracts_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_contracts_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS compliance_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT,
    loan_request_id BIGINT,
    actor_user_id BIGINT,
    action_type VARCHAR(80) NOT NULL,
    outcome ENUM('PASSED', 'FAILED', 'INFO') NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_compliance_audit_customer
        FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_compliance_audit_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_compliance_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_compliance_audit_customer_created
ON compliance_audit_logs(customer_id, created_at);

CREATE INDEX idx_compliance_audit_loan_created
ON compliance_audit_logs(loan_request_id, created_at);
