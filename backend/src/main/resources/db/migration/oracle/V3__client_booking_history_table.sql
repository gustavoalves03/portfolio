-- V3: create CLIENT_BOOKING_HISTORY table + supporting indexes.
--
-- Reproduces ApplicationSchemaMigrator.ensureClientBookingHistoryTable(). Each
-- DDL is wrapped in a PL/SQL block that swallows ORA-00955 (-955: object
-- already exists) and ORA-01408 (-1408: index list already exists) so the
-- migration is safe on production databases where ApplicationSchemaMigrator
-- has already created these objects.
DECLARE
    e_object_exists EXCEPTION;
    e_index_exists  EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_object_exists, -955);
    PRAGMA EXCEPTION_INIT(e_index_exists, -1408);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE CLIENT_BOOKING_HISTORY (
                ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                USER_ID NUMBER(19) NOT NULL,
                TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                SALON_NAME VARCHAR2(255 CHAR) NOT NULL,
                BOOKING_ID NUMBER(19) NOT NULL,
                CARE_NAME VARCHAR2(255 CHAR) NOT NULL,
                CARE_PRICE NUMBER(10) NOT NULL,
                CARE_DURATION NUMBER(10) NOT NULL,
                APPOINTMENT_DATE DATE NOT NULL,
                APPOINTMENT_TIME TIMESTAMP NOT NULL,
                STATUS VARCHAR2(20 CHAR) NOT NULL,
                CREATED_AT TIMESTAMP NOT NULL
            )
        ]';
    EXCEPTION WHEN e_object_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_CBH_USER_DATE ON CLIENT_BOOKING_HISTORY (USER_ID, APPOINTMENT_DATE)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE UNIQUE INDEX UK_CBH_TENANT_BOOKING ON CLIENT_BOOKING_HISTORY (TENANT_SLUG, BOOKING_ID)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;
END;
/
