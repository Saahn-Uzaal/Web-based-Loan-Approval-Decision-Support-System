ALTER TABLE loan_application_snapshots
    ADD COLUMN verification_note VARCHAR(500) NULL AFTER fraud_flag,
    ADD COLUMN verified_by BIGINT NULL AFTER verification_note,
    ADD COLUMN verified_at TIMESTAMP NULL AFTER verified_by,
    ADD CONSTRAINT fk_loan_application_snapshots_verified_by
        FOREIGN KEY (verified_by) REFERENCES users(id);

ALTER TABLE loan_installments
    ADD COLUMN waived_interest DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER scheduled_interest,
    MODIFY COLUMN status ENUM('PENDING', 'PARTIALLY_PAID', 'OVERDUE', 'PAID') NOT NULL DEFAULT 'PENDING';

UPDATE loan_installments
SET status = CASE
    WHEN paid_amount >= (scheduled_principal + scheduled_interest + scheduled_fee - waived_interest) THEN 'PAID'
    WHEN due_date < CURRENT_DATE
        AND (scheduled_principal + scheduled_interest + scheduled_fee - waived_interest - paid_amount) > 0 THEN 'OVERDUE'
    WHEN paid_amount > 0 THEN 'PARTIALLY_PAID'
    ELSE 'PENDING'
END;

ALTER TABLE loan_delinquencies
    ADD COLUMN total_fee_assessed DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER total_rating_delta;
