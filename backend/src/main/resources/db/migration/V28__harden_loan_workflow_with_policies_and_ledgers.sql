UPDATE loan_requests
SET status = 'ACTIVE'
WHERE status = 'DISBURSED';

ALTER TABLE loan_requests
    MODIFY COLUMN status ENUM(
        'DRAFT',
        'PENDING',
        'NEEDS_MORE_INFO',
        'APPOINTMENT_SCHEDULED',
        'APPROVED',
        'CONTRACTED',
        'ACTIVE',
        'OVERDUE',
        'CLOSED',
        'REJECTED',
        'WITHDRAWN'
    ) NOT NULL DEFAULT 'PENDING';

ALTER TABLE customer_debts
    MODIFY COLUMN status ENUM(
        'ACTIVE',
        'CLOSED',
        'PENDING_VERIFICATION',
        'VERIFIED',
        'REJECTED',
        'REMOVED_BY_CUSTOMER'
    ) NOT NULL DEFAULT 'PENDING_VERIFICATION';

UPDATE customer_debts
SET status = CASE
    WHEN status = 'ACTIVE' THEN 'VERIFIED'
    ELSE status
END;

ALTER TABLE customer_debts
    MODIFY COLUMN status ENUM(
        'PENDING_VERIFICATION',
        'VERIFIED',
        'REJECTED',
        'CLOSED',
        'REMOVED_BY_CUSTOMER'
    ) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    ADD COLUMN reviewed_by BIGINT NULL AFTER status,
    ADD COLUMN reviewed_at TIMESTAMP NULL AFTER reviewed_by,
    ADD COLUMN verification_note VARCHAR(500) NULL AFTER reviewed_at,
    ADD CONSTRAINT fk_customer_debts_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(id);

CREATE TABLE IF NOT EXISTS credit_policies (
    version VARCHAR(80) PRIMARY KEY,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    policy_payload JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO credit_policies(version, is_active, policy_payload)
VALUES (
    'VND_RETAIL_LOAN_POLICY_2026_05',
    TRUE,
    JSON_OBJECT(
        'version', 'VND_RETAIL_LOAN_POLICY_2026_05',
        'unsecuredAnnualRate', 0.12,
        'securedAnnualRate', 0.105,
        'unsecuredIncomeMultiple', 10,
        'securedVehicleLtv', 0.70,
        'maxDsr', 0.50,
        'unsecuredProductCap', 300000000,
        'securedProductCap', 1500000000,
        'riskAdjustmentA', 1.0,
        'riskAdjustmentB', 0.85,
        'riskAdjustmentC', 0.65,
        'riskAdjustmentD', 0.0,
        'dssWeightDti', 0.23,
        'dssWeightIncome', 0.18,
        'dssWeightCreditHistory', 0.15,
        'dssWeightBurden', 0.12,
        'dssWeightEmployment', 0.12,
        'dssWeightAge', 0.07,
        'dssWeightCollateral', 0.07,
        'dssWeightPurpose', 0.04,
        'dssWeightVerification', 0.02,
        'dssScoreMin', 300,
        'dssScoreMax', 850,
        'dssScoreMultiplier', 5.5,
        'dssRankAThreshold', 780,
        'dssRankBThreshold', 700,
        'dssRankCThreshold', 620,
        'dtiLowThreshold', 35.0,
        'dtiHighDowngradeThreshold', 55.0,
        'dtiModerateDowngradeThreshold', 45.0,
        'dtiExtremeThreshold', 75.0,
        'dtiRejectThreshold', 60.0
    )
)
ON DUPLICATE KEY UPDATE
    is_active = VALUES(is_active),
    policy_payload = VALUES(policy_payload),
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS loan_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NOT NULL,
    change_reason VARCHAR(500) NULL,
    changed_by_user_id BIGINT NULL,
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_status_history_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_status_history_changed_by
        FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_loan_status_history_loan_created
    ON loan_status_history(loan_request_id, created_at);

CREATE TABLE IF NOT EXISTS loan_installments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_contract_id BIGINT NOT NULL,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    opening_principal DECIMAL(15,2) NOT NULL,
    scheduled_principal DECIMAL(15,2) NOT NULL,
    scheduled_interest DECIMAL(15,2) NOT NULL,
    scheduled_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    scheduled_amount DECIMAL(15,2) NOT NULL,
    paid_principal DECIMAL(15,2) NOT NULL DEFAULT 0,
    paid_interest DECIMAL(15,2) NOT NULL DEFAULT 0,
    paid_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    last_paid_at TIMESTAMP NULL,
    status ENUM('PENDING', 'PARTIALLY_PAID', 'PAID') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_loan_installments_contract_number
        UNIQUE (loan_contract_id, installment_number),
    CONSTRAINT fk_loan_installments_contract
        FOREIGN KEY (loan_contract_id) REFERENCES loan_contracts(id),
    CONSTRAINT fk_loan_installments_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_installments_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_loan_installments_loan_status_due
    ON loan_installments(loan_request_id, status, due_date);

ALTER TABLE payment_confirmation_requests
    ADD COLUMN idempotency_key VARCHAR(120) NULL AFTER customer_note;

CREATE UNIQUE INDEX uq_payment_confirmations_customer_idempotency
    ON payment_confirmation_requests(customer_id, idempotency_key);
