-- V4: create NOTIFICATIONS table + recipient index.
--
-- Reproduces ApplicationSchemaMigrator.ensureNotificationsTable(). Idempotent
-- via ORA-00955 / ORA-01408 catch — same pattern as V3.
DECLARE
    e_object_exists EXCEPTION;
    e_index_exists  EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_object_exists, -955);
    PRAGMA EXCEPTION_INIT(e_index_exists, -1408);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE NOTIFICATIONS (
                ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                RECIPIENT_ID NUMBER(19) NOT NULL,
                TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                TYPE VARCHAR2(50 CHAR) NOT NULL,
                CATEGORY VARCHAR2(30 CHAR) NOT NULL,
                TITLE VARCHAR2(255 CHAR) NOT NULL,
                MESSAGE VARCHAR2(500 CHAR) NOT NULL,
                REFERENCE_ID NUMBER(19) NOT NULL,
                REFERENCE_TYPE VARCHAR2(50 CHAR) NOT NULL,
                IS_READ NUMBER(1) DEFAULT 0 NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )
        ]';
    EXCEPTION WHEN e_object_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_NOTIF_RECIPIENT ON NOTIFICATIONS (RECIPIENT_ID, IS_READ, CREATED_AT DESC)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;
END;
/
