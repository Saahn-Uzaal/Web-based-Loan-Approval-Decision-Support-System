ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT NULL AFTER role,
    ADD COLUMN verification_email_sent_at TIMESTAMP NULL DEFAULT NULL AFTER email_verified_at;

UPDATE users
SET email_verified_at = COALESCE(email_verified_at, created_at)
WHERE email_verified_at IS NULL;
