-- V7: Multi-employee booking schema changes.
-- Step 1/3 of V7: add CANCELLATION_REASON column. Steps 2 and 3 (backfill + UK swap)
-- are appended in subsequent tasks of the same feature branch.
ALTER TABLE CARE_BOOKINGS ADD CANCELLATION_REASON VARCHAR2(64);
