-- V16__mail_outbox_template_check_add_new_templates.sql
-- Hibernate auto-generated a CHECK constraint on mail_outbox.template at table
-- creation, listing only the enum values present at that time (RESET_PASSWORD,
-- BOOKING_CONFIRMED, BOOKING_RECEIVED_PRO, WELCOME_PRO, INVOICE_PAID,
-- INVOICE_PAYMENT_FAILED, TRIAL_ENDING).
--
-- Adding VERIFY_EMAIL and BOOKING_REMINDER_J1 to the MailTemplate enum (Phase 2
-- of email-verification feature) did NOT update the DB constraint, so any
-- INSERT with the new templates triggers ORA-02290 in environments where
-- spring.jpa.hibernate.ddl-auto != update (i.e. prod with validate/none).
--
-- Drop the auto-generated check (system name SYS_C008732 on prod) by looking
-- it up dynamically, then recreate an explicit one named CK_MAIL_OUTBOX_TEMPLATE
-- that includes every current MailTemplate enum value.

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

    IF v_cnt = 0 THEN
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
                'BOOKING_REMINDER_J1'
            ))
        ]';
    END IF;
END;
/
