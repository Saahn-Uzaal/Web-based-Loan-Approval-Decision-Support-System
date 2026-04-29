UPDATE loan_requests
SET status = 'PENDING'
WHERE status = 'WAITING_SUPERVISOR';

UPDATE dss_results
SET recommendation = 'APPROVE_RECOMMENDED'
WHERE recommendation = 'ESCALATE_RECOMMENDED';

DELETE FROM decision_audits
WHERE action = 'ESCALATE';

ALTER TABLE loan_requests
    MODIFY COLUMN status ENUM(
        'PENDING',
        'APPROVED',
        'CONTRACTED',
        'DISBURSED',
        'ACTIVE',
        'CLOSED',
        'REJECTED'
    ) NOT NULL DEFAULT 'PENDING';

ALTER TABLE dss_results
    MODIFY COLUMN recommendation ENUM(
        'APPROVE_RECOMMENDED',
        'REJECT_RECOMMENDED'
    ) NOT NULL;

ALTER TABLE decision_audits
    MODIFY COLUMN action ENUM(
        'APPROVE',
        'REJECT'
    ) NOT NULL;
