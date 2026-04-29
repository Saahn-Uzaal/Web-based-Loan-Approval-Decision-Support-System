ALTER TABLE loan_requests
    MODIFY COLUMN status ENUM(
        'PENDING',
        'WAITING_SUPERVISOR',
        'APPROVED',
        'CONTRACTED',
        'DISBURSED',
        'ACTIVE',
        'CLOSED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN approved_amount DECIMAL(15,2) NULL AFTER eligible_limit,
    ADD COLUMN approved_term_months INT NULL AFTER approved_amount,
    ADD COLUMN approved_annual_rate DECIMAL(8,6) NULL AFTER approved_term_months,
    ADD COLUMN approved_monthly_payment DECIMAL(15,2) NULL AFTER approved_annual_rate,
    ADD COLUMN decision_policy_version VARCHAR(40) NULL AFTER approved_monthly_payment;

ALTER TABLE customer_verifications
    ADD COLUMN face_match_status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING'
    AFTER identity_status;
