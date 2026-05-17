-- V15__email_verif_token_expires_with_tz.sql
-- Fix V14: column was created as TIMESTAMP but Hibernate maps Instant via
-- TimestampUtcAsOffsetDateTimeJdbcType which requires TIMESTAMP WITH TIME ZONE.
-- Without this, SELECT * FROM users throws ORA-18716 on column extraction.
-- Same pattern as password_reset_token_expires_at (auto-created by ddl-auto).
ALTER TABLE users MODIFY (email_verification_token_expires_at TIMESTAMP WITH TIME ZONE);
