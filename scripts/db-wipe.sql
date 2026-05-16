-- ============================================================================
-- WIPE TOTAL DEV DB — LuxPretty
-- ============================================================================
-- Drops every TENANT_* schema and clears the application schema (LUXPRETTY_APP
-- or whichever value APP_USER points to). Run as SYSTEM.
--
-- Usage (from host shell with docker-compose oracle-db running):
--   docker exec -i oracle-db sqlplus -S system/<ORACLE_PASSWORD>@FREEPDB1 < scripts/db-wipe.sql
--
-- After this script, restart the backend with
-- spring.jpa.hibernate.ddl-auto=create so Hibernate regenerates the public
-- schema from the JPA entities. Then flip back to update.
-- ============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0

-- 1. Drop every TENANT_* schema (each tenant = one Oracle user/schema)
DECLARE
    v_count INTEGER := 0;
BEGIN
    FOR rec IN (
        SELECT username FROM all_users WHERE username LIKE 'TENANT\_%' ESCAPE '\'
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP USER "' || rec.username || '" CASCADE';
            v_count := v_count + 1;
            DBMS_OUTPUT.PUT_LINE('Dropped tenant schema: ' || rec.username);
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('Failed to drop ' || rec.username || ': ' || SQLERRM);
        END;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Total tenant schemas dropped: ' || v_count);
END;
/

-- 2. Drop every object owned by the app user (keeps the user itself so Spring
--    can reconnect, but wipes all tables/indexes/sequences/views).
--    Adjust 'APPUSER' below if your APP_USER env var is different.
DECLARE
    v_app_user VARCHAR2(128) := UPPER('appuser'); -- ← change to your APP_USER
    v_count INTEGER := 0;
BEGIN
    FOR rec IN (
        SELECT object_name, object_type
        FROM all_objects
        WHERE owner = v_app_user
          AND object_type IN ('TABLE','VIEW','SEQUENCE','SYNONYM','PROCEDURE','FUNCTION','PACKAGE','TRIGGER')
    ) LOOP
        BEGIN
            IF rec.object_type = 'TABLE' THEN
                EXECUTE IMMEDIATE 'DROP TABLE "' || v_app_user || '"."' || rec.object_name || '" CASCADE CONSTRAINTS PURGE';
            ELSE
                EXECUTE IMMEDIATE 'DROP ' || rec.object_type || ' "' || v_app_user || '"."' || rec.object_name || '"';
            END IF;
            v_count := v_count + 1;
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('Failed to drop ' || rec.object_type || ' ' || rec.object_name || ': ' || SQLERRM);
        END;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Total app objects dropped: ' || v_count);

    -- Purge recyclebin too so PURGE didn't already handle leftover stuff
    EXECUTE IMMEDIATE 'PURGE DBA_RECYCLEBIN';
    DBMS_OUTPUT.PUT_LINE('Recycle bin purged.');
END;
/

EXIT;
