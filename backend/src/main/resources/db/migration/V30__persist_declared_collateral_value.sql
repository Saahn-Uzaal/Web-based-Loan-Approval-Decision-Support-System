ALTER TABLE loan_requests
    ADD COLUMN collateral_value DECIMAL(15,2) NULL AFTER collateral_type;
