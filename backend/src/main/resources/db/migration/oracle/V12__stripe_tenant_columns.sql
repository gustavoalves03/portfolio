-- Stripe subscription columns on TENANTS (shared app schema).
--
-- Adds 7 columns to support the subscription flow introduced by Stripe PR1.
-- Tenants existing before this migration are grandfathered to VITRINE_FREE /
-- VITRINE / FREE via the column DEFAULT values below.
--
-- The matching JPA fields live on Tenant.java. Hibernate ddl-auto=update on
-- legacy environments would also add them from the entity, but Flyway is the
-- source of truth in shared/prod environments.
--
-- Idempotent guard: on a fresh database, TENANTS does not exist yet (Hibernate
-- creates it after Flyway runs). Skip silently — ddl-auto then adds these
-- columns from the entity definition on the same boot. Each ALTER / CREATE
-- INDEX is also guarded so a partial re-run does not fail.
DECLARE
    v_tenants_exists NUMBER;
    v_col_exists     NUMBER;
    v_idx_exists     NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tenants_exists FROM user_tables WHERE table_name = 'TENANTS';
    IF v_tenants_exists = 0 THEN
        RETURN;
    END IF;

    FOR r IN (
        SELECT 'STRIPE_CUSTOMER_ID'     AS col, 'VARCHAR2(255 CHAR)' AS typ, NULL AS dflt, 'Y' AS nullable FROM dual UNION ALL
        SELECT 'STRIPE_SUBSCRIPTION_ID' AS col, 'VARCHAR2(255 CHAR)' AS typ, NULL AS dflt, 'Y' AS nullable FROM dual UNION ALL
        SELECT 'SUBSCRIPTION_STATUS'    AS col, 'VARCHAR2(32 CHAR)'  AS typ, '''VITRINE_FREE''' AS dflt, 'N' AS nullable FROM dual UNION ALL
        SELECT 'SUBSCRIPTION_TIER'      AS col, 'VARCHAR2(32 CHAR)'  AS typ, '''VITRINE''' AS dflt, 'N' AS nullable FROM dual UNION ALL
        SELECT 'SUBSCRIPTION_BILLING'   AS col, 'VARCHAR2(16 CHAR)'  AS typ, '''FREE''' AS dflt, 'N' AS nullable FROM dual UNION ALL
        SELECT 'CURRENT_PERIOD_END'     AS col, 'TIMESTAMP'          AS typ, NULL AS dflt, 'Y' AS nullable FROM dual UNION ALL
        SELECT 'TRIAL_END'              AS col, 'TIMESTAMP'          AS typ, NULL AS dflt, 'Y' AS nullable FROM dual
    ) LOOP
        SELECT COUNT(*) INTO v_col_exists
          FROM user_tab_columns
         WHERE table_name = 'TENANTS' AND column_name = r.col;
        IF v_col_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE TENANTS ADD ' || r.col || ' ' || r.typ ||
                CASE WHEN r.dflt IS NOT NULL THEN ' DEFAULT ' || r.dflt ELSE '' END ||
                CASE WHEN r.nullable = 'N' THEN ' NOT NULL' ELSE '' END;
        END IF;
    END LOOP;

    -- Indexes (guarded individually)
    FOR r IN (
        SELECT 'UK_TENANTS_STRIPE_CUSTOMER'     AS idx, 'CREATE UNIQUE INDEX UK_TENANTS_STRIPE_CUSTOMER ON TENANTS (STRIPE_CUSTOMER_ID)' AS ddl FROM dual UNION ALL
        SELECT 'UK_TENANTS_STRIPE_SUBSCRIPTION' AS idx, 'CREATE UNIQUE INDEX UK_TENANTS_STRIPE_SUBSCRIPTION ON TENANTS (STRIPE_SUBSCRIPTION_ID)' AS ddl FROM dual UNION ALL
        SELECT 'IX_TENANTS_SUB_STATUS'          AS idx, 'CREATE INDEX IX_TENANTS_SUB_STATUS ON TENANTS (SUBSCRIPTION_STATUS)' AS ddl FROM dual
    ) LOOP
        SELECT COUNT(*) INTO v_idx_exists
          FROM user_indexes
         WHERE index_name = r.idx;
        IF v_idx_exists = 0 THEN
            EXECUTE IMMEDIATE r.ddl;
        END IF;
    END LOOP;
END;
/
