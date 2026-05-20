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
