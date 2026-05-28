ALTER TABLE customer_profiles
    ADD COLUMN bank_account_number VARCHAR(40) NULL AFTER employment_start_date,
    ADD COLUMN bank_name VARCHAR(150) NULL AFTER bank_account_number;
