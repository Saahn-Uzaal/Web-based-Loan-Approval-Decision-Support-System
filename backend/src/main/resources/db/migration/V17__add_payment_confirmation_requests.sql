CREATE TABLE IF NOT EXISTS payment_confirmation_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    expected_amount_due DECIMAL(15,2) NOT NULL,
    expected_outstanding_amount DECIMAL(15,2) NOT NULL,
    expected_installment_number INT NOT NULL,
    expected_due_date DATE NOT NULL,
    proof_original_filename VARCHAR(255) NOT NULL,
    proof_storage_name VARCHAR(255) NOT NULL,
    proof_content_type VARCHAR(120) NOT NULL,
    proof_file_size BIGINT NOT NULL,
    customer_note VARCHAR(500),
    status ENUM('PENDING_REVIEW', 'CONFIRMED', 'REJECTED') NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    confirmed_amount DECIMAL(15,2) NULL,
    confirmed_paid_at TIMESTAMP NULL,
    bank_transaction_code VARCHAR(120) NULL,
    staff_note VARCHAR(500) NULL,
    rejection_reason VARCHAR(500) NULL,
    repayment_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_confirmations_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_payment_confirmations_customer
        FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_payment_confirmations_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT fk_payment_confirmations_repayment
        FOREIGN KEY (repayment_id) REFERENCES loan_repayments(id)
);

CREATE INDEX idx_payment_confirmations_customer_created
ON payment_confirmation_requests(customer_id, created_at);

CREATE INDEX idx_payment_confirmations_status_created
ON payment_confirmation_requests(status, created_at);

CREATE INDEX idx_payment_confirmations_loan_status
ON payment_confirmation_requests(loan_request_id, status);
