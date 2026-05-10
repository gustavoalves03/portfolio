-- Per-tenant booking policy. Singleton row inserted with safe defaults.
-- See docs/superpowers/specs/2026-05-11-booking-policy-guards-design.md.

CREATE TABLE "${tenantSchema}".BOOKING_POLICY (
    ID                                       NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    MAX_BOOKINGS_PER_DAY_PER_CLIENT          NUMBER(2) NOT NULL,
    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT     NUMBER(2) NOT NULL,
    UPDATED_AT                               TIMESTAMP NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON "${tenantSchema}".BOOKING_POLICY TO "${appSchema}";

INSERT INTO "${tenantSchema}".BOOKING_POLICY (
    MAX_BOOKINGS_PER_DAY_PER_CLIENT,
    MAX_BOOKINGS_PER_WEEK_FOR_NEW_CLIENT,
    UPDATED_AT
) VALUES (1, 1, CURRENT_TIMESTAMP);
