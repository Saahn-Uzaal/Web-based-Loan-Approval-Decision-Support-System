ALTER TABLE loan_requests
    ADD COLUMN additional_info_request_note VARCHAR(500) NULL AFTER intake_note,
    ADD COLUMN additional_info_last_requested_at TIMESTAMP NULL AFTER additional_info_request_note,
    ADD COLUMN additional_info_request_deadline TIMESTAMP NULL AFTER additional_info_last_requested_at,
    ADD COLUMN additional_info_request_count INT NOT NULL DEFAULT 0 AFTER additional_info_request_deadline;

CREATE INDEX idx_loan_requests_additional_info_deadline
    ON loan_requests(status, additional_info_request_deadline);
