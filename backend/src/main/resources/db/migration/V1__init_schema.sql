CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('CUSTOMER', 'STAFF') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    monthly_income DECIMAL(15,2),
    debt_to_income_ratio DECIMAL(5,2),
    employment_status VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS loan_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    term_months INT NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    status ENUM('PENDING', 'WAITING_SUPERVISOR', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    final_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_requests_customer
        FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS dss_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL UNIQUE,
    credit_score INT NOT NULL,
    risk_rank ENUM('A', 'B', 'C', 'D') NOT NULL,
    customer_segment ENUM(
        'LOW_RISK_HIGH_VALUE',
        'LOW_RISK_LOW_VALUE',
        'HIGH_RISK_HIGH_VALUE',
        'HIGH_RISK_LOW_VALUE'
    ) NOT NULL,
    recommendation ENUM(
        'APPROVE_RECOMMENDED',
        'REJECT_RECOMMENDED',
        'ESCALATE_RECOMMENDED'
    ) NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dss_results_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id)
);

CREATE TABLE IF NOT EXISTS decision_audits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    loan_request_id BIGINT NOT NULL,
    staff_user_id BIGINT NOT NULL,
    action ENUM('APPROVE', 'REJECT', 'ESCALATE') NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_decision_audits_loan_request
        FOREIGN KEY (loan_request_id) REFERENCES loan_requests(id),
    CONSTRAINT fk_decision_audits_staff
        FOREIGN KEY (staff_user_id) REFERENCES users(id)
);
