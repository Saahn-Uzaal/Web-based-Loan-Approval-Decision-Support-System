ALTER TABLE loan_requests
    ADD COLUMN loan_type ENUM('SECURED', 'UNSECURED') NOT NULL DEFAULT 'UNSECURED' AFTER customer_id,
    ADD COLUMN collateral_type ENUM('VEHICLE_REGISTRATION') NULL AFTER purpose,
    ADD COLUMN eligible_limit DECIMAL(15,2) NULL AFTER final_reason,
    ADD COLUMN intake_note VARCHAR(500) NULL AFTER eligible_limit;

CREATE TABLE IF NOT EXISTS loan_request_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    document_type ENUM(
        'VEHICLE_REGISTRATION',
        'LICENSE_PLATE_IMAGE',
        'ID_CARD_FRONT',
        'ID_CARD_BACK',
        'FACE_CAPTURE'
    ) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_request_documents_loan
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id)
);

CREATE UNIQUE INDEX idx_loan_request_documents_loan_type
ON loan_request_documents(loan_request_id, document_type);
