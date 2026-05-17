-- V14__user_email_verification_token.sql
ALTER TABLE users ADD (
    email_verification_token VARCHAR2(36),
    email_verification_token_expires_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_users_email_verif_token ON users(email_verification_token);

-- Backfill : tous les users existants sont considérés vérifiés (zéro friction).
-- La feature s'applique aux nouveaux signups post-deploy.
UPDATE users SET email_verified = 1 WHERE email_verified = 0;
COMMIT;
