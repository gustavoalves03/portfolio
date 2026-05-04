-- V2: ensure USERS role check constraint allows 'EMPLOYEE'.
--
-- Reproduces ApplicationSchemaMigrator.migrateUsersRoleConstraint(). The
-- existing constraint may be present (named anything, may already include
-- EMPLOYEE on prod databases where ApplicationSchemaMigrator ran). We:
--   1. Scan all CHECK constraints on USERS whose normalized condition starts
--      with ROLEIN(.
--   2. If at least one already contains 'EMPLOYEE', no-op.
--   3. Otherwise drop the stale ones and recreate CK_USERS_ROLE with the full
--      role list.
DECLARE
    v_already_ok    NUMBER := 0;
    v_normalized    VARCHAR2(4000);
    v_users_exists  NUMBER;
BEGIN
    -- On a fresh database, USERS does not exist yet (Hibernate creates it after
    -- Flyway runs). Skip silently — there is no constraint to migrate.
    SELECT COUNT(*) INTO v_users_exists
      FROM user_tables
     WHERE table_name = 'USERS';
    IF v_users_exists = 0 THEN
        RETURN;
    END IF;

    -- Pass 1: do any of the existing role-check constraints already include EMPLOYEE?
    FOR r IN (
        SELECT constraint_name, search_condition_vc
          FROM user_constraints
         WHERE table_name = 'USERS'
           AND constraint_type = 'C'
           AND search_condition_vc IS NOT NULL
    ) LOOP
        v_normalized := UPPER(REPLACE(REPLACE(r.search_condition_vc, '"', ''), ' ', ''));
        IF v_normalized LIKE 'ROLEIN(%' AND INSTR(v_normalized, '''EMPLOYEE''') > 0 THEN
            v_already_ok := 1;
            EXIT;
        END IF;
    END LOOP;

    IF v_already_ok = 1 THEN
        RETURN;
    END IF;

    -- Pass 2: drop every stale role-check constraint, then recreate the canonical one.
    FOR r IN (
        SELECT constraint_name, search_condition_vc
          FROM user_constraints
         WHERE table_name = 'USERS'
           AND constraint_type = 'C'
           AND search_condition_vc IS NOT NULL
    ) LOOP
        v_normalized := UPPER(REPLACE(REPLACE(r.search_condition_vc, '"', ''), ' ', ''));
        IF v_normalized LIKE 'ROLEIN(%' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP CONSTRAINT "' || r.constraint_name || '"';
        END IF;
    END LOOP;

    EXECUTE IMMEDIATE
        'ALTER TABLE USERS ADD CONSTRAINT CK_USERS_ROLE ' ||
        'CHECK (ROLE IN (''USER'', ''ADMIN'', ''PRO'', ''EMPLOYEE''))';
END;
/
