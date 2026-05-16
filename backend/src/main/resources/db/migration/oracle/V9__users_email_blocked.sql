-- Track users whose email is blocked (hard bounce or spam complaint).
-- The MailWorker skips sending to blocked recipients.
--
-- Idempotent guard: on a fresh database, USERS does not exist yet (Hibernate
-- creates it after Flyway runs). Skip silently — ddl-auto then adds the
-- EMAIL_BLOCKED column from the entity definition on the same boot.
DECLARE
    v_users_exists      NUMBER;
    v_column_exists     NUMBER;
    v_constraint_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_users_exists
      FROM user_tables WHERE table_name = 'USERS';
    IF v_users_exists = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'USERS' AND column_name = 'EMAIL_BLOCKED';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USERS ADD EMAIL_BLOCKED NUMBER(1) DEFAULT 0 NOT NULL';
    END IF;

    SELECT COUNT(*) INTO v_constraint_exists
      FROM user_constraints
     WHERE table_name = 'USERS' AND constraint_name = 'CK_USERS_EMAIL_BLOCKED';
    IF v_constraint_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE USERS ADD CONSTRAINT CK_USERS_EMAIL_BLOCKED ' ||
            'CHECK (EMAIL_BLOCKED IN (0,1))';
    END IF;

    EXECUTE IMMEDIATE
        q'[COMMENT ON COLUMN USERS.EMAIL_BLOCKED IS 'Set to 1 on hard bounce or spam complaint via Postmark webhook. MailWorker skips sending to blocked addresses.']';
END;
/
