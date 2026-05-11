-- Seed the "pro-self" employee for legacy tenants that were provisioned
-- before TenantProvisioningService started creating one automatically.
--
-- Idempotent on both INSERTs:
--   1. EMPLOYEES insert fires only if no row with user_id = owner_id exists.
--   2. EMPLOYEE_CARES insert fires only for cares (SERVICES rows) that
--      currently have zero employees.
--
-- Placeholders resolved by TenantFlywayService:
--   ${tenantSchema} -> e.g. TENANT_SOPHIE
--   ${tenantSlug}   -> e.g. sophie  (kebab-case lowercase)
--   ${appSchema}    -> shared app schema, e.g. APPUSER
--
-- Cross-schema access uses the synonyms USERS and TENANTS created at the V1 baseline.
-- The Care table is named SERVICES in the schema (legacy naming preserved from V1).

-- 1. Create the pro-self Employee from the tenant's owner User if missing.
INSERT INTO "${tenantSchema}".EMPLOYEES (USER_ID, NAME, EMAIL, PHONE, ACTIVE, CREATED_AT)
SELECT u.ID, u.NAME, u.EMAIL, NULL, 1, CURRENT_TIMESTAMP
FROM "${tenantSchema}".USERS u
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
WHERE t.SLUG = '${tenantSlug}'
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEES e WHERE e.USER_ID = u.ID
  );

-- 2. Link the (now-existing) pro-self employee to every orphan service
--    (= row in SERVICES with no row in EMPLOYEE_CARES).
INSERT INTO "${tenantSchema}".EMPLOYEE_CARES (EMPLOYEE_ID, CARE_ID)
SELECT e.ID, c.ID
FROM "${tenantSchema}".EMPLOYEES e
JOIN "${tenantSchema}".USERS u ON u.ID = e.USER_ID
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
CROSS JOIN "${tenantSchema}".SERVICES c
WHERE t.SLUG = '${tenantSlug}'
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEE_CARES ec WHERE ec.CARE_ID = c.ID
  );
