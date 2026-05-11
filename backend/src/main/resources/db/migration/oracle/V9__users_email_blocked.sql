-- Track users whose email is blocked (hard bounce or spam complaint).
-- The MailWorker skips sending to blocked recipients.
ALTER TABLE USERS ADD EMAIL_BLOCKED NUMBER(1) DEFAULT 0 NOT NULL;
ALTER TABLE USERS ADD CONSTRAINT CK_USERS_EMAIL_BLOCKED CHECK (EMAIL_BLOCKED IN (0,1));
COMMENT ON COLUMN USERS.EMAIL_BLOCKED IS
  'Set to 1 on hard bounce or spam complaint via Postmark webhook. MailWorker skips sending to blocked addresses.';
