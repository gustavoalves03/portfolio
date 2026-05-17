-- V5__booking_reminder_sent_at.sql
ALTER TABLE care_bookings ADD (reminder_sent_at TIMESTAMP);

CREATE INDEX ix_care_bookings_reminder
    ON care_bookings(appointment_date, appointment_time, reminder_sent_at);
