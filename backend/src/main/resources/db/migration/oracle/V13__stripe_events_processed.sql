-- Idempotency table for Stripe webhook events.
--
-- Stripe retries webhooks on 5xx/timeout. We store every event_id received
-- with its arrival timestamp; subsequent receipts of the same id throw on
-- the primary key unique constraint and the handler skips silently.
--
-- Idempotent guard: if Hibernate ddl-auto already created the table from
-- the entity definition (fresh DB path), skip the CREATE TABLE.
DECLARE
    v_table_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists
      FROM user_tables WHERE table_name = 'STRIPE_EVENTS_PROCESSED';
    IF v_table_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE TABLE STRIPE_EVENTS_PROCESSED (' ||
            '    EVENT_ID      VARCHAR2(255 CHAR) NOT NULL,' ||
            '    EVENT_TYPE    VARCHAR2(64 CHAR) NOT NULL,' ||
            '    PROCESSED_AT  TIMESTAMP NOT NULL,' ||
            '    CONSTRAINT PK_STRIPE_EVENTS_PROCESSED PRIMARY KEY (EVENT_ID)' ||
            ')';
    END IF;
END;
/
