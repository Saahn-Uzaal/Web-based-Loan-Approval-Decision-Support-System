UPDATE loan_requests
SET status = 'ACTIVE'
WHERE status = 'DISBURSED';

ALTER TABLE loan_requests
    ADD COLUMN assigned_staff_user_id BIGINT NULL AFTER customer_id,
    ADD COLUMN assigned_at TIMESTAMP NULL AFTER assigned_staff_user_id,
    ADD CONSTRAINT fk_loan_requests_assigned_staff
        FOREIGN KEY (assigned_staff_user_id) REFERENCES users(id);

CREATE INDEX idx_loan_requests_assigned_staff_status
    ON loan_requests(assigned_staff_user_id, status);
