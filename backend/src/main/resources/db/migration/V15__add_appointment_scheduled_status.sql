ALTER TABLE loan_requests
    MODIFY COLUMN status ENUM(
        'PENDING',
        'APPOINTMENT_SCHEDULED',
        'APPROVED',
        'CONTRACTED',
        'DISBURSED',
        'ACTIVE',
        'CLOSED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING';
