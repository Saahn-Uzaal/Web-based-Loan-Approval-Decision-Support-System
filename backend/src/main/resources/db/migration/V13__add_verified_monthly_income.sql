ALTER TABLE customer_profiles
    ADD COLUMN verified_monthly_income DECIMAL(15,2) NULL AFTER monthly_income;
