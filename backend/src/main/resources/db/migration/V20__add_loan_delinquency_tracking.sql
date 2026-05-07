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
        'OVERDUE',
        'CLOSED',
        'REJECTED',
        'WITHDRAWN'
    ) NOT NULL DEFAULT 'PENDING';

CREATE TABLE IF NOT EXISTS loan_delinquencies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount_due DECIMAL(15,2) NOT NULL,
    current_amount_due DECIMAL(15,2) NOT NULL,
    days_past_due INT NOT NULL DEFAULT 0,
    highest_milestone INT NOT NULL DEFAULT 0,
    total_rating_delta INT NOT NULL DEFAULT 0,
    status ENUM('OPEN', 'CURED') NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_assessed_at TIMESTAMP NULL,
    cured_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_loan_delinquency_installment UNIQUE (loan_request_id, installment_number, due_date),
    CONSTRAINT fk_loan_delinquencies_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_delinquencies_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_loan_delinquencies_status_due
    ON loan_delinquencies(status, due_date);
