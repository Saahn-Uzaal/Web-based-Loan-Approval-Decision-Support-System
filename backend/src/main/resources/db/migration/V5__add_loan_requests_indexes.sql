CREATE INDEX idx_loan_requests_customer_id
ON loan_requests(customer_id);

CREATE INDEX idx_loan_requests_status
ON loan_requests(status);

CREATE INDEX idx_loan_requests_customer_status
ON loan_requests(customer_id, status);

CREATE INDEX idx_loan_repayments_loan_customer
ON loan_repayments(loan_request_id, customer_id);

