ALTER TABLE customer_profiles
ADD COLUMN payment_rating INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS loan_repayments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    amount_due DECIMAL(15,2) NOT NULL,
    amount_paid DECIMAL(15,2) NOT NULL,
    due_date DATE NOT NULL,
    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_status ENUM('ON_TIME', 'LATE') NOT NULL,
    rating_delta INT NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_repayments_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_loan_repayments_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_loan_repayments_customer_created
ON loan_repayments(customer_id, created_at);
