-- V7: Multi-employee booking schema changes.
-- Step 1/3 of V7: add CANCELLATION_REASON column. Steps 2 and 3 (backfill + UK swap)
-- are appended in subsequent tasks of the same feature branch.
ALTER TABLE CARE_BOOKINGS ADD CANCELLATION_REASON VARCHAR2(64);

-- Step 2/3 of V7: backfill NULL EMPLOYEE_ID on active bookings.
-- The "default self-employee" backfill (tenant V4) ensures every tenant has
-- ≥ 1 active employee, so the subquery returns a non-null id for normal tenants.
UPDATE CARE_BOOKINGS
   SET EMPLOYEE_ID = (
     SELECT MIN(e.ID) FROM EMPLOYEES e WHERE e.ACTIVE = 1
   )
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');

-- Safety net: tenant with zero active employees -> cancel any remaining NULL rows.
UPDATE CARE_BOOKINGS
   SET STATUS = 'CANCELLED', CANCELLATION_REASON = 'LEGACY_NO_EMPLOYEE'
 WHERE EMPLOYEE_ID IS NULL
   AND STATUS IN ('PENDING','CONFIRMED');

-- Step 3/3 of V7: replace global slot uniqueness with per-employee uniqueness on active rows.

-- Drop the old global slot uniqueness (constraint dropped only after the backfill above
-- has populated EMPLOYEE_ID on every active booking).
ALTER TABLE CARE_BOOKINGS DROP CONSTRAINT UK_BOOKING_SLOT;

-- Per-employee uniqueness on active statuses (Oracle function-based unique index).
-- The CASE expressions return NULL for CANCELLED rows; Oracle excludes NULLs from
-- unique indexes, so cancelled rows do NOT block re-booking of the same slot.
CREATE UNIQUE INDEX UK_BOOKING_SLOT_EMPLOYEE ON CARE_BOOKINGS (
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_DATE END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN APPOINTMENT_TIME END,
  CASE WHEN STATUS IN ('PENDING','CONFIRMED') THEN EMPLOYEE_ID END
);

-- Lookup performance for per-employee queries
CREATE INDEX IDX_BOOKING_DATE_EMPLOYEE ON CARE_BOOKINGS (APPOINTMENT_DATE, EMPLOYEE_ID);
