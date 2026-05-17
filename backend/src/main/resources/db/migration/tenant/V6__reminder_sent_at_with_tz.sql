-- V6__reminder_sent_at_with_tz.sql
-- Fix V5: column was created as TIMESTAMP but Hibernate maps Instant via
-- TimestampUtcAsOffsetDateTimeJdbcType which requires TIMESTAMP WITH TIME ZONE.
-- Without this, SELECT * FROM care_bookings throws ORA-18716 on column extraction.
ALTER TABLE care_bookings MODIFY (reminder_sent_at TIMESTAMP WITH TIME ZONE);
