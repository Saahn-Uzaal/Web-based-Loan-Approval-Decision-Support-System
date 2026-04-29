CREATE TABLE IF NOT EXISTS loan_appointments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    note VARCHAR(500),
    status ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_appointments_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_appointments_customer
        FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_loan_appointments_staff
        FOREIGN KEY (staff_id) REFERENCES users(id)
);

ALTER TABLE loan_appointments
    ADD COLUMN location VARCHAR(255) NULL AFTER scheduled_at;

CREATE TABLE IF NOT EXISTS secured_loan_procedures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL UNIQUE,
    staff_user_id BIGINT NOT NULL,
    collateral_owner_name VARCHAR(150),
    collateral_identifier VARCHAR(100),
    registration_number VARCHAR(100),
    appraisal_value DECIMAL(15,2),
    appraisal_report_code VARCHAR(100),
    notarization_code VARCHAR(100),
    lien_registration_code VARCHAR(100),
    insurance_policy_number VARCHAR(100),
    documents_checked BOOLEAN NOT NULL DEFAULT FALSE,
    asset_inspected BOOLEAN NOT NULL DEFAULT FALSE,
    valuation_approved BOOLEAN NOT NULL DEFAULT FALSE,
    contract_signed BOOLEAN NOT NULL DEFAULT FALSE,
    collateral_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    disbursement_ready BOOLEAN NOT NULL DEFAULT FALSE,
    status ENUM('DRAFT', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'DRAFT',
    note VARCHAR(1000),
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_secured_procedures_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_secured_procedures_staff
        FOREIGN KEY (staff_user_id) REFERENCES users(id)
);

CREATE INDEX idx_secured_procedures_status_updated
ON secured_loan_procedures(status, updated_at);
