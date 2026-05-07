ALTER TABLE dss_results
    MODIFY COLUMN recommendation ENUM(
        'APPROVE_RECOMMENDED',
        'MANUAL_REVIEW_RECOMMENDED',
        'REJECT_RECOMMENDED'
    ) NOT NULL;

UPDATE dss_results dr
INNER JOIN loan_requests lr ON lr.id = dr.loan_request_id
SET dr.recommendation = 'MANUAL_REVIEW_RECOMMENDED'
WHERE lr.loan_type = 'UNSECURED'
  AND dr.recommendation = 'APPROVE_RECOMMENDED';
