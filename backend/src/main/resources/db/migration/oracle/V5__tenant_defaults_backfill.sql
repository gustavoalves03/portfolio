-- V5: backfill nullable defaults on TENANTS rows that pre-date their columns.
--
-- Reproduces ApplicationSchemaMigrator.backfillTenantDefaults(). Each UPDATE
-- is naturally idempotent ("WHERE col IS NULL"). Flyway manages the
-- transaction — do not COMMIT here.
--
-- Tolerance for fresh databases: Flyway runs BEFORE Hibernate ddl-auto, so on
-- a brand-new schema TENANTS or one of its columns may not exist yet. Each
-- UPDATE is wrapped in its own block that swallows ORA-00942 (table missing)
-- and ORA-00904 (column missing) — same per-statement best-effort behavior the
-- original Java code had via executeBackfill's try/catch. Any other SQL error
-- is re-raised so genuine bugs are not silently masked.
DECLARE
    e_table_missing  EXCEPTION;
    e_column_missing EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_table_missing, -942);
    PRAGMA EXCEPTION_INIT(e_column_missing, -904);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET CLOSED_ON_HOLIDAYS = 1 WHERE CLOSED_ON_HOLIDAYS IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET BUFFER_MINUTES = 0 WHERE BUFFER_MINUTES IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET MIN_ADVANCE_MINUTES = 120 WHERE MIN_ADVANCE_MINUTES IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET MAX_ADVANCE_DAYS = 90 WHERE MAX_ADVANCE_DAYS IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET MAX_CLIENT_HOURS_PER_DAY = 8 WHERE MAX_CLIENT_HOURS_PER_DAY IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET ANNUAL_LEAVE_DAYS = 25 WHERE ANNUAL_LEAVE_DAYS IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'UPDATE TENANTS SET EMPLOYEES_ENABLED = 0 WHERE EMPLOYEES_ENABLED IS NULL';
    EXCEPTION
        WHEN e_table_missing THEN NULL;
        WHEN e_column_missing THEN NULL;
    END;
END;
/
