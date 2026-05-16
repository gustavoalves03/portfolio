-- V10: introduce USER_ROLE_ASSIGNMENTS junction, backfill PRO + ADMIN, drop USERS.ROLE.
--
-- EMPLOYEEs are NOT backfilled here because EMPLOYEES live in tenant schemas;
-- the Java EmployeeRoleBackfillRunner takes care of them at boot (Task 9).
--
-- Idempotent guard: on a fresh database, USERS / TENANTS do not exist yet
-- (Hibernate creates them after Flyway runs). Skip silently — ddl-auto then
-- creates USER_ROLE_ASSIGNMENTS from the entity definition on the same boot.
-- The backfill is harmless then because there are no pre-existing USERS rows.
DECLARE
    v_users_exists    NUMBER;
    v_tenants_exists  NUMBER;
    v_role_exists     NUMBER;
    v_table_exists    NUMBER;
    v_ck_role_exists  NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_users_exists FROM user_tables WHERE table_name = 'USERS';
    SELECT COUNT(*) INTO v_tenants_exists FROM user_tables WHERE table_name = 'TENANTS';
    IF v_users_exists = 0 OR v_tenants_exists = 0 THEN
        RETURN;
    END IF;

    -- 1. Table
    SELECT COUNT(*) INTO v_table_exists FROM user_tables WHERE table_name = 'USER_ROLE_ASSIGNMENTS';
    IF v_table_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE TABLE USER_ROLE_ASSIGNMENTS (' ||
            '    ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,' ||
            '    USER_ID    NUMBER(19) NOT NULL,' ||
            '    ROLE       VARCHAR2(32 CHAR) NOT NULL,' ||
            '    SCOPE_TYPE VARCHAR2(16 CHAR) NOT NULL,' ||
            '    SCOPE_ID   NUMBER(19),' ||
            '    CREATED_AT TIMESTAMP NOT NULL,' ||
            '    CONSTRAINT FK_URA_USER FOREIGN KEY (USER_ID) REFERENCES USERS(ID),' ||
            '    CONSTRAINT UK_USER_ROLE_SCOPE UNIQUE (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID),' ||
            '    CONSTRAINT CK_URA_SCOPE_TYPE CHECK (SCOPE_TYPE IN (''GLOBAL'', ''TENANT'')),' ||
            '    CONSTRAINT CK_URA_SCOPE_MATCH CHECK (' ||
            '        (SCOPE_TYPE = ''TENANT'' AND SCOPE_ID IS NOT NULL) OR' ||
            '        (SCOPE_TYPE = ''GLOBAL'' AND SCOPE_ID IS NULL)' ||
            '    )' ||
            ')';
        EXECUTE IMMEDIATE 'CREATE INDEX IX_URA_USER ON USER_ROLE_ASSIGNMENTS (USER_ID)';
        EXECUTE IMMEDIATE 'CREATE INDEX IX_URA_SCOPE ON USER_ROLE_ASSIGNMENTS (SCOPE_TYPE, SCOPE_ID)';
    END IF;

    -- The USERS.ROLE column is only present on pre-V10 databases. If it has
    -- already been dropped (re-run / fresh DB after ddl-auto creates the new
    -- entity shape), skip the backfill + DROP COLUMN.
    SELECT COUNT(*) INTO v_role_exists
      FROM user_tab_columns
     WHERE table_name = 'USERS' AND column_name = 'ROLE';
    IF v_role_exists = 0 THEN
        RETURN;
    END IF;

    -- 2. Backfill PRO@TENANT
    EXECUTE IMMEDIATE
        'INSERT INTO USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT) ' ||
        'SELECT u.ID, ''PRO'', ''TENANT'', t.ID, CURRENT_TIMESTAMP ' ||
        'FROM USERS u JOIN TENANTS t ON t.OWNER_ID = u.ID WHERE u.ROLE = ''PRO''';

    -- 3. Backfill ADMIN@GLOBAL
    EXECUTE IMMEDIATE
        'INSERT INTO USER_ROLE_ASSIGNMENTS (USER_ID, ROLE, SCOPE_TYPE, SCOPE_ID, CREATED_AT) ' ||
        'SELECT ID, ''ADMIN'', ''GLOBAL'', NULL, CURRENT_TIMESTAMP ' ||
        'FROM USERS WHERE ROLE = ''ADMIN''';

    -- 4. Drop CK_USERS_ROLE before dropping the column.
    SELECT COUNT(*) INTO v_ck_role_exists
      FROM user_constraints
     WHERE table_name = 'USERS' AND constraint_name = 'CK_USERS_ROLE';
    IF v_ck_role_exists > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP CONSTRAINT CK_USERS_ROLE';
    END IF;

    -- 5. Drop the USERS.ROLE column
    EXECUTE IMMEDIATE 'ALTER TABLE USERS SET UNUSED COLUMN ROLE';
    EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP UNUSED COLUMNS';
END;
/
