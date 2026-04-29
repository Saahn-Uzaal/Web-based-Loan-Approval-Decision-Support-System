ALTER TABLE secured_loan_procedures
    ADD COLUMN mortgagee_name VARCHAR(150) NULL AFTER staff_user_id,
    ADD COLUMN mortgagee_address VARCHAR(255) NULL AFTER mortgagee_name,
    ADD COLUMN mortgagee_business_code VARCHAR(100) NULL AFTER mortgagee_address,
    ADD COLUMN mortgagee_phone VARCHAR(50) NULL AFTER mortgagee_business_code;
