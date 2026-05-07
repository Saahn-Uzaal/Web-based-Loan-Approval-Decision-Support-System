-- Add disabled_at column for soft-delete support on users table.
-- When set, the user account is considered deactivated.
-- Financial records (loans, contracts, repayments, audits) are preserved.

ALTER TABLE users ADD COLUMN disabled_at TIMESTAMP NULL DEFAULT NULL;
CREATE INDEX idx_users_disabled_at ON users (disabled_at);
