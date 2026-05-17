-- V15__email_verif_token_expires_with_tz.sql
-- Fix V14: column was created as TIMESTAMP but Hibernate maps Instant via
-- TimestampUtcAsOffsetDateTimeJdbcType which requires TIMESTAMP WITH TIME ZONE.
-- Without this, SELECT * FROM users throws ORA-18716 on column extraction.
-- Same pattern as password_reset_token_expires_at (auto-created by ddl-auto).
--
-- Oracle refuses MODIFY datatype on a column containing data (ORA-01439).
-- Workaround: drop + add. Safe here because the column was introduced in V14
-- and no row has a non-null value yet (all signups so far happened after V14
-- but the column starts NULL until queueVerificationMail() sets it; in case
-- some rows have values, we accept the loss — these are unverified tokens
-- pending in-flight, the user can request a fresh one via /send-verification).
ALTER TABLE users DROP COLUMN email_verification_token_expires_at;
ALTER TABLE users ADD (email_verification_token_expires_at TIMESTAMP WITH TIME ZONE);
