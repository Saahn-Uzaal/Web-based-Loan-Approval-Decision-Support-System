ALTER TABLE loan_repayments
    MODIFY COLUMN payment_status ENUM('EARLY', 'ON_TIME', 'LATE') NOT NULL;
