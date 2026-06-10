ALTER TABLE loan_requests
    ADD COLUMN review_deadline_at TIMESTAMP NULL AFTER additional_info_request_count,
    ADD COLUMN contract_acceptance_deadline_at TIMESTAMP NULL AFTER review_deadline_at;

CREATE INDEX idx_loan_requests_review_deadline
    ON loan_requests(status, review_deadline_at);

CREATE INDEX idx_loan_requests_contract_acceptance_deadline
    ON loan_requests(status, contract_acceptance_deadline_at);
