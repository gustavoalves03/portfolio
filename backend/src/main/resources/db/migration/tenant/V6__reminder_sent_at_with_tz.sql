-- V6__reminder_sent_at_with_tz.sql
-- Fix V5: column was created as TIMESTAMP but Hibernate maps Instant via
-- TimestampUtcAsOffsetDateTimeJdbcType which requires TIMESTAMP WITH TIME ZONE.
-- Without this, SELECT * FROM care_bookings throws ORA-18716 on column extraction.
--
-- Oracle refuses MODIFY datatype on a column containing data (ORA-01439).
-- Workaround: drop + add. Safe here because the column was introduced in V5
-- and no booking has run through the scheduler yet (reminder_sent_at is set
-- only by BookingReminderScheduler which is brand new). In case some rows
-- have values, the scheduler will simply re-evaluate them at next tick.
ALTER TABLE care_bookings DROP COLUMN reminder_sent_at;
ALTER TABLE care_bookings ADD (reminder_sent_at TIMESTAMP WITH TIME ZONE);
