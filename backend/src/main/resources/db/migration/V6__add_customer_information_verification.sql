CREATE TABLE IF NOT EXISTS customer_information_verifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL UNIQUE,
    status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_information_verifications_customer
        FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_customer_information_verifications_staff
        FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

CREATE INDEX idx_customer_information_verifications_status
ON customer_information_verifications(status);
