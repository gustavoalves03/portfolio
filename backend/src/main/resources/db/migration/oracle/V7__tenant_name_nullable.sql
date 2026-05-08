-- V7: relax NOT NULL on TENANTS.name so the onboarding wizard can require an
-- explicit confirmation step. New tenants are provisioned with name=null until
-- the pro picks a salon name on the first wizard screen.
--
-- Tolerance for deployment topology, mirroring V5's defensive style:
--   * fresh databases — TENANTS may not exist yet (Flyway runs before
--     Hibernate ddl-auto). Swallow ORA-00942.
--   * already-nullable column — re-running this on a database where the
--     column was previously made nullable raises ORA-01451. Swallow it.
--   * missing column — guard against ORA-00904 even though it should not
--     happen for the canonical `name` column.
DECLARE
    e_table_missing       EXCEPTION;
    e_column_missing      EXCEPTION;
    e_already_nullable    EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_table_missing, -942);
    PRAGMA EXCEPTION_INIT(e_column_missing, -904);
    PRAGMA EXCEPTION_INIT(e_already_nullable, -1451);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE TENANTS MODIFY (name NULL)';
EXCEPTION
    WHEN e_table_missing THEN NULL;
    WHEN e_column_missing THEN NULL;
    WHEN e_already_nullable THEN NULL;
END;
/
