CREATE TABLE IF NOT EXISTS loan_payment_due_reminders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount_due DECIMAL(15,2) NOT NULL,
    reminder_sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_due_reminder UNIQUE (loan_request_id, installment_number, due_date),
    CONSTRAINT fk_payment_due_reminders_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_payment_due_reminders_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_payment_due_reminders_customer_due
    ON loan_payment_due_reminders(customer_id, due_date);
