-- V17__mail_outbox_template_check_add_booking_rescheduled.sql
-- Adds BOOKING_RESCHEDULED to the MailTemplate enum (client email sent when a
-- booking's date/time is changed). Without updating CK_MAIL_OUTBOX_TEMPLATE the
-- INSERT would fail with ORA-02290 in environments where ddl-auto != update
-- (i.e. prod with validate/none). Same drop+recreate pattern as V16.

DECLARE
    v_cnt NUMBER;
BEGIN
    FOR rec IN (
        SELECT constraint_name
        FROM user_constraints
        WHERE table_name = 'MAIL_OUTBOX'
          AND constraint_type = 'C'
          AND search_condition_vc LIKE '%template in%'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE mail_outbox DROP CONSTRAINT "' || rec.constraint_name || '"';
    END LOOP;

    SELECT COUNT(*) INTO v_cnt
    FROM user_constraints
    WHERE table_name = 'MAIL_OUTBOX' AND constraint_name = 'CK_MAIL_OUTBOX_TEMPLATE';

    IF v_cnt > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE mail_outbox DROP CONSTRAINT CK_MAIL_OUTBOX_TEMPLATE';
    END IF;

    EXECUTE IMMEDIATE q'[
        ALTER TABLE mail_outbox ADD CONSTRAINT CK_MAIL_OUTBOX_TEMPLATE
        CHECK (template IN (
            'RESET_PASSWORD',
            'BOOKING_CONFIRMED',
            'BOOKING_RECEIVED_PRO',
            'WELCOME_PRO',
            'INVOICE_PAID',
            'INVOICE_PAYMENT_FAILED',
            'TRIAL_ENDING',
            'VERIFY_EMAIL',
            'BOOKING_REMINDER_J1',
            'BOOKING_RESCHEDULED'
        ))
    ]';
END;
/
