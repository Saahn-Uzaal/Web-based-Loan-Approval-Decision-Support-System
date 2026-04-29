ALTER TABLE loan_contracts
    ADD COLUMN first_payment_date DATE NULL AFTER start_date,
    ADD COLUMN monthly_payment_day VARCHAR(30) NULL AFTER first_payment_date,
    ADD COLUMN final_payment_date DATE NULL AFTER monthly_payment_day;

UPDATE loan_contracts
SET first_payment_date = DATE_ADD(start_date, INTERVAL 1 MONTH),
    monthly_payment_day = CAST(DAY(DATE_ADD(start_date, INTERVAL 1 MONTH)) AS CHAR),
    final_payment_date = end_date
WHERE first_payment_date IS NULL;
