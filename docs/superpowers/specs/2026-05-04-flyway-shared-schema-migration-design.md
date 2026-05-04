# Flyway adoption — shared schema migrations

**Date:** 2026-05-04
**Scope:** Backend (`backend/`)
**Status:** Approved design, ready for implementation plan

## Context

The application currently relies on:

- **Hibernate `ddl-auto=update`** to create and evolve tables from JPA entities.
- **`ApplicationSchemaMigrator`** (230 lines, runs at startup) for ad-hoc Oracle fixes Hibernate cannot do reliably: drop/recreate the `USERS` role check constraint to add `EMPLOYEE`, create `CLIENT_BOOKING_HISTORY` and `NOTIFICATIONS` tables and indexes, backfill defaults on `TENANTS` columns added later.
- **`TenantSchemaManager` (1227 lines) + `TenantSchemaMigrator`** for the multi-tenant side: each salon owns an Oracle schema with ~19 tables.

The shared-schema migrator is brittle: it grows every time a column or table is added, the SQL is embedded in Java, and there is no audit trail of what was applied where.

## Goal

Replace `ApplicationSchemaMigrator` with Flyway, owning migrations of the **shared schema only**. The multi-tenant side (`TenantSchemaManager`, `TenantSchemaMigrator`) is **out of scope** for this iteration — it will be addressed separately.

## Non-goals

- Migrating the per-tenant schemas to Flyway.
- Disabling Hibernate `ddl-auto`. It still creates new tables on the shared schema; Flyway only owns the corrective migrations that Hibernate cannot do.
- Producing a full DDL dump of the existing shared schema as a baseline.
- Running Flyway under H2 in tests.

## Approach

### Choices made during brainstorming

- **Scope:** option C — shared schema only.
- **Baseline:** option C — empty `V1__baseline.sql` + V2…V5 reproducing the current `ApplicationSchemaMigrator` operations.
- **Dialect:** option A — Oracle SQL only; Flyway disabled in tests (H2 keeps `ddl-auto=create`).

## Architecture

### Dependencies

Add to `backend/pom.xml`:

```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-oracle</artifactId>
</dependency>
```

Spring Boot manages the Flyway version via its BOM.

### Configuration

In `backend/src/main/resources/application.properties`:

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration/oracle
spring.flyway.schemas=${APP_USER:appuser}
```

In `backend/src/test/resources/application-test.properties` and `backend/src/test/resources/application-smoke-test.properties`:

```properties
spring.flyway.enabled=false
```

Hibernate `ddl-auto=create` (or `update`) keeps managing the H2 schema in tests.

### Startup ordering

Spring Boot runs Flyway during datasource initialization, **before** Hibernate `SchemaManagementTool` (when `ddl-auto=update`) and **before** any `ApplicationRunner`. Final order at boot:

1. Flyway applies V1…V5 against the shared schema.
2. Hibernate `ddl-auto=update` updates anything Flyway did not own (tables created later via JPA entities).
3. `ApplicationRunner` beans run: `DataInitializer`, `TenantSchemaMigrator`.

## Migrations

Five SQL files under `backend/src/main/resources/db/migration/oracle/`.

### `V1__baseline.sql`

Comment-only. With `baseline-on-migrate=true` and `baseline-version=1`, Flyway creates a baseline row in `flyway_schema_history` for an existing non-empty database **without executing migrations at or below the baseline version**. The actual applied migrations are V2–V5. On a brand-new database, V1 is also "applied" (its empty body runs successfully).

```sql
-- Baseline anchor for Flyway adoption (2026-05-04).
-- No DDL: existing shared schemas are baselined as-is.
```

### `V2__users_role_constraint_employee.sql`

Reproduces `migrateUsersRoleConstraint()` (lines 88–136 of `ApplicationSchemaMigrator`). PL/SQL block to:

1. Detect any existing `CHECK` constraint on `USERS` whose normalized condition starts with `ROLEIN(`.
2. If at least one of them already contains `'EMPLOYEE'`, no-op.
3. Otherwise, drop the stale ones and create `CK_USERS_ROLE` with `CHECK (ROLE IN ('USER', 'ADMIN', 'PRO', 'EMPLOYEE'))`.

PL/SQL is required (pure SQL cannot conditionally drop/create constraints).

### `V3__client_booking_history_table.sql`

Reproduces `ensureClientBookingHistoryTable()` (lines 138–170). Wraps DDL in a PL/SQL block that catches ORA-00955 (object already exists, error code 955) and ORA-01408 (index already exists, error code 1408), so the migration is safe on environments where `ApplicationSchemaMigrator` has already run.

Creates:

- Table `CLIENT_BOOKING_HISTORY` (12 columns).
- Index `IDX_CBH_USER_DATE` on `(USER_ID, APPOINTMENT_DATE)`.
- Unique index `UK_CBH_TENANT_BOOKING` on `(TENANT_SLUG, BOOKING_ID)`.

### `V4__notifications_table.sql`

Reproduces `ensureNotificationsTable()` (lines 172–197). Same idempotent PL/SQL pattern as V3.

Creates:

- Table `NOTIFICATIONS` (11 columns).
- Index `IDX_NOTIF_RECIPIENT` on `(RECIPIENT_ID, IS_READ, CREATED_AT DESC)`.

### `V5__tenant_defaults_backfill.sql`

Reproduces `backfillTenantDefaults()` (lines 59–75). Plain SQL — these `UPDATE` statements are naturally idempotent because each is guarded by `WHERE col IS NULL`.

```sql
UPDATE TENANTS SET CLOSED_ON_HOLIDAYS = 1     WHERE CLOSED_ON_HOLIDAYS IS NULL;
UPDATE TENANTS SET BUFFER_MINUTES = 0         WHERE BUFFER_MINUTES IS NULL;
UPDATE TENANTS SET MIN_ADVANCE_MINUTES = 120  WHERE MIN_ADVANCE_MINUTES IS NULL;
UPDATE TENANTS SET MAX_ADVANCE_DAYS = 90      WHERE MAX_ADVANCE_DAYS IS NULL;
UPDATE TENANTS SET MAX_CLIENT_HOURS_PER_DAY = 8 WHERE MAX_CLIENT_HOURS_PER_DAY IS NULL;
UPDATE TENANTS SET ANNUAL_LEAVE_DAYS = 25     WHERE ANNUAL_LEAVE_DAYS IS NULL;
UPDATE TENANTS SET EMPLOYEES_ENABLED = 0      WHERE EMPLOYEES_ENABLED IS NULL;
```

Flyway commits the transaction itself — no explicit `COMMIT` needed (and an explicit one would interfere with Flyway's own bookkeeping).

## Idempotence and safety

Two scenarios force defensive PL/SQL on top of Flyway's own once-only guarantee:

1. **Existing production database** where `ApplicationSchemaMigrator` already ran — the constraint, tables and indexes already exist.
2. **Fresh database** where Hibernate `ddl-auto` has not yet run — `USERS` and `TENANTS` do not exist yet (Flyway runs before Hibernate).

Each migration is written to handle both:

- **V2:** early `RETURN` if `USERS` is absent (fresh DB), then a no-op pass if any existing role-check constraint already includes `'EMPLOYEE'` (prod re-run), otherwise drop stale constraints and recreate.
- **V3, V4:** wrap each `CREATE TABLE` / `CREATE INDEX` in a per-statement `BEGIN ... EXCEPTION ... END` using `PRAGMA EXCEPTION_INIT` against named exceptions for ORA-00955 (object already exists) and ORA-01408 (index list already exists). Any other error is re-raised.
- **V5:** wrap each `UPDATE TENANTS` in a per-statement `BEGIN ... EXCEPTION ... END` using `PRAGMA EXCEPTION_INIT` for ORA-00942 (table missing) and ORA-00904 (column missing). The `WHERE col IS NULL` clause makes the update naturally idempotent on top.

The named-exception approach is deliberately narrower than `WHEN OTHERS` — only the specific Oracle codes we expect are swallowed; everything else surfaces as a Flyway failure.

## Code changes

### To delete

- `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java` — fully replaced by V2–V5.

### To keep unchanged

- `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java` — out of scope.
- `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaMigrator.java` — out of scope.
- `backend/src/main/java/com/prettyface/app/config/DataInitializer.java` — verify it does not depend on `ApplicationSchemaMigrator` (no `@DependsOn`, no constructor injection of the migrator). It is an `ApplicationRunner` and runs after Flyway naturally.

### Tests

- Run `mvn test`. Expectation: all green, no test referenced `ApplicationSchemaMigrator`.
- If a test does reference it (unlikely), delete the reference along with the class.

## Rollback / failure handling

- **If Flyway fails at boot**, the application will not start. Recovery: set `SPRING_FLYWAY_ENABLED=false` as an environment variable and restart. The app will boot using only Hibernate `ddl-auto`, exactly like before this change. This is the explicit safety net.
- `flyway.out-of-order=false` (default) — no out-of-order execution allowed.

## Validation plan (manual smoke test before merge)

1. **Fresh Oracle:** drop and recreate `APP_USER`. Run `mvn spring-boot:run`. Logs must show Flyway applying V1–V5. Verify:
   - `SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank` → 5 rows, all `success = 1`.
   - `USER_CONSTRAINTS` shows `CK_USERS_ROLE` containing `EMPLOYEE`.
   - Tables `CLIENT_BOOKING_HISTORY` and `NOTIFICATIONS` exist with their indexes.
2. **Existing Oracle (prod-like):** start the app against a database where `ApplicationSchemaMigrator` has already run. Expected: Flyway records a `BASELINE` row at V1 (no DDL executed for V1) and then applies V2–V5 — these run successfully thanks to the idempotence safeguards (§ Idempotence and safety). Verify `flyway_schema_history` has 5 rows (one baseline + four `SQL`), all `success = 1`, and no errors in logs.
3. **Tests:** `mvn test` → all green; H2 untouched by Flyway.

Until all three pass, the branch is not merge-ready.

## Out-of-scope follow-ups (not part of this spec)

- Multi-tenant Flyway (per-schema migrations triggered by `TenantProvisioningService` / `TenantSchemaMigrator`).
- Disabling Hibernate `ddl-auto` and dumping the full shared-schema DDL into a versioned baseline.
- Refactoring `TenantSchemaManager` (1227 lines) to extract DDL into resource files.
