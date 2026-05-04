# Flyway Shared Schema Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `ApplicationSchemaMigrator` with Flyway-managed migrations of the shared Oracle schema, while keeping multi-tenant migrations and Hibernate `ddl-auto` untouched.

**Architecture:** Add `flyway-core` + `flyway-database-oracle` to the backend, configure Spring Boot Flyway with `baseline-on-migrate=true` and `baseline-version=1`, write five migrations (`V1__baseline.sql` through `V5__tenant_defaults_backfill.sql`) reproducing the existing ad-hoc operations idempotently, delete `ApplicationSchemaMigrator`. Disable Flyway in the test classpath so H2 tests keep relying on Hibernate `ddl-auto=create-drop`.

**Tech Stack:** Spring Boot 3.5.4, Flyway (managed by Spring Boot BOM), Oracle Free 23ai (prod/dev), H2 in-memory (tests), Maven, JUnit 5.

**Reference spec:** `docs/superpowers/specs/2026-05-04-flyway-shared-schema-migration-design.md`

---

## File Structure

### Files to create

- `backend/src/main/resources/db/migration/oracle/V1__baseline.sql` — comment-only baseline anchor.
- `backend/src/main/resources/db/migration/oracle/V2__users_role_constraint_employee.sql` — adds `EMPLOYEE` to `CK_USERS_ROLE`.
- `backend/src/main/resources/db/migration/oracle/V3__client_booking_history_table.sql` — creates `CLIENT_BOOKING_HISTORY` + indexes.
- `backend/src/main/resources/db/migration/oracle/V4__notifications_table.sql` — creates `NOTIFICATIONS` + index.
- `backend/src/main/resources/db/migration/oracle/V5__tenant_defaults_backfill.sql` — backfills 7 nullable defaults on `TENANTS`.

### Files to modify

- `backend/pom.xml` — add `flyway-core` and `flyway-database-oracle` dependencies.
- `backend/src/main/resources/application.properties` — add Flyway config block.
- `backend/src/test/resources/application.properties` — disable Flyway for tests (covers all test profiles since this is the base test config).

### Files to delete

- `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java` — replaced by V2–V5.

### Files to leave alone

- `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaManager.java` — out of scope.
- `backend/src/main/java/com/prettyface/app/multitenancy/TenantSchemaMigrator.java` — out of scope.
- `backend/src/main/java/com/prettyface/app/config/DataInitializer.java` — verified not to depend on `ApplicationSchemaMigrator`.

---

## Task 1: Add Flyway dependencies to backend pom

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Open `backend/pom.xml` and find the `<dependencies>` block.** Add the following two dependencies anywhere inside `<dependencies>` (next to other Spring Boot starters is fine):

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

No version is specified — the Spring Boot BOM (inherited from `spring-boot-starter-parent`) pins a compatible Flyway version.

- [ ] **Step 2: Resolve dependencies and confirm the JARs are available.**

Run:

```bash
cd backend && mvn -o dependency:resolve -q 2>&1 | tail -20 || mvn dependency:resolve -q 2>&1 | tail -20
```

Expected: command exits 0. If offline mode fails, the second command (online) downloads them.

- [ ] **Step 3: Confirm Flyway is resolved with a sane version.**

Run:

```bash
cd backend && mvn -q dependency:list 2>/dev/null | grep -E "flyway-(core|database-oracle)"
```

Expected: two lines like `org.flywaydb:flyway-core:jar:10.x.x:compile` and `org.flywaydb:flyway-database-oracle:jar:10.x.x:compile`.

- [ ] **Step 4: Commit.**

```bash
git add backend/pom.xml
git commit -m "build(backend): add Flyway core + Oracle support"
```

---

## Task 2: Disable Flyway in the test classpath

**Files:**
- Modify: `backend/src/test/resources/application.properties`

This is the base config inherited by all test profiles (`test`, `smoke-test`, default). Disabling here means tests keep using Hibernate `ddl-auto=create-drop` on H2 with no Flyway interference.

- [ ] **Step 1: Append the Flyway disable line to the test base config.**

Open `backend/src/test/resources/application.properties` and append at the end of the file:

```properties

# Flyway is disabled across all test profiles. Tests run on H2 where the
# Oracle-only V*.sql migrations would not parse. Hibernate ddl-auto=create-drop
# (set per profile) keeps managing the H2 schema.
spring.flyway.enabled=false
```

- [ ] **Step 2: Sanity check — confirm the line is present.**

Run:

```bash
grep "spring.flyway.enabled=false" backend/src/test/resources/application.properties
```

Expected: prints exactly one line with `spring.flyway.enabled=false`.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/test/resources/application.properties
git commit -m "test(backend): disable Flyway across test profiles"
```

---

## Task 3: Configure Flyway in main application properties

**Files:**
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: Append a Flyway config block.**

Open `backend/src/main/resources/application.properties`. Append at the end:

```properties

# Flyway — manages the shared Oracle schema only.
# Tenant schemas are still provisioned by TenantSchemaManager (out of scope).
# Hibernate ddl-auto=update keeps creating new shared tables; Flyway only
# owns corrective migrations that ddl-auto cannot do reliably.
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration/oracle
spring.flyway.schemas=${APP_USER:appuser}
```

- [ ] **Step 2: Sanity check — verify the keys.**

Run:

```bash
grep -E "^spring\.flyway\." backend/src/main/resources/application.properties
```

Expected: 5 lines (`enabled`, `baseline-on-migrate`, `baseline-version`, `locations`, `schemas`).

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/application.properties
git commit -m "config(backend): enable Flyway on shared schema in main profile"
```

---

## Task 4: Create the V1 baseline migration

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V1__baseline.sql`

- [ ] **Step 1: Create the migrations directory and the V1 file.**

Run:

```bash
mkdir -p backend/src/main/resources/db/migration/oracle
```

Create `backend/src/main/resources/db/migration/oracle/V1__baseline.sql` with this exact content:

```sql
-- Baseline anchor for Flyway adoption (2026-05-04).
--
-- This file is intentionally a no-op. With
-- spring.flyway.baseline-on-migrate=true and spring.flyway.baseline-version=1,
-- Flyway will:
--   - on an existing non-empty database, create a BASELINE row at V1 in
--     flyway_schema_history WITHOUT executing this SQL, then apply V2+;
--   - on a brand-new (empty) database, run this SELECT (a no-op) and apply V2+.
--
-- All real DDL/DML lives in V2..V5, which reproduce ApplicationSchemaMigrator.
SELECT 1 FROM DUAL;
```

> The `SELECT 1 FROM DUAL;` is a deliberate no-op so Flyway has at least one parseable statement to record on a fresh database. It runs only on fresh DBs (on existing DBs, V1 is recorded as `BASELINE` without executing).

- [ ] **Step 2: Sanity check — file exists and is non-empty.**

Run:

```bash
ls -la backend/src/main/resources/db/migration/oracle/V1__baseline.sql
```

Expected: file present, non-zero size.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/db/migration/oracle/V1__baseline.sql
git commit -m "db(backend): add Flyway V1 baseline anchor"
```

---

## Task 5: Create V2 — USERS role constraint with EMPLOYEE

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V2__users_role_constraint_employee.sql`

This reproduces `ApplicationSchemaMigrator.migrateUsersRoleConstraint()` (lines 88–136). On a fresh DB, Hibernate has just created `USERS` with whatever check constraint the entity has; on a prod DB, the constraint may already include `EMPLOYEE` from a previous run of `ApplicationSchemaMigrator`. The script must be safe in both cases.

- [ ] **Step 1: Create the file with the PL/SQL block.**

Create `backend/src/main/resources/db/migration/oracle/V2__users_role_constraint_employee.sql`:

```sql
-- V2: ensure USERS role check constraint allows 'EMPLOYEE'.
--
-- Reproduces ApplicationSchemaMigrator.migrateUsersRoleConstraint(). The
-- existing constraint may be present (named anything, may already include
-- EMPLOYEE on prod databases where ApplicationSchemaMigrator ran). We:
--   1. Scan all CHECK constraints on USERS whose normalized condition starts
--      with ROLEIN(.
--   2. If at least one already contains 'EMPLOYEE', no-op.
--   3. Otherwise drop the stale ones and recreate CK_USERS_ROLE with the full
--      role list.
DECLARE
    v_already_ok    NUMBER := 0;
    v_normalized    VARCHAR2(4000);
BEGIN
    -- Pass 1: do any of the existing role-check constraints already include EMPLOYEE?
    FOR r IN (
        SELECT constraint_name, search_condition_vc
          FROM user_constraints
         WHERE table_name = 'USERS'
           AND constraint_type = 'C'
           AND search_condition_vc IS NOT NULL
    ) LOOP
        v_normalized := UPPER(REPLACE(REPLACE(r.search_condition_vc, '"', ''), ' ', ''));
        IF v_normalized LIKE 'ROLEIN(%' AND INSTR(v_normalized, '''EMPLOYEE''') > 0 THEN
            v_already_ok := 1;
            EXIT;
        END IF;
    END LOOP;

    IF v_already_ok = 1 THEN
        RETURN;
    END IF;

    -- Pass 2: drop every stale role-check constraint, then recreate the canonical one.
    FOR r IN (
        SELECT constraint_name, search_condition_vc
          FROM user_constraints
         WHERE table_name = 'USERS'
           AND constraint_type = 'C'
           AND search_condition_vc IS NOT NULL
    ) LOOP
        v_normalized := UPPER(REPLACE(REPLACE(r.search_condition_vc, '"', ''), ' ', ''));
        IF v_normalized LIKE 'ROLEIN(%' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE USERS DROP CONSTRAINT "' || r.constraint_name || '"';
        END IF;
    END LOOP;

    EXECUTE IMMEDIATE
        'ALTER TABLE USERS ADD CONSTRAINT CK_USERS_ROLE ' ||
        'CHECK (ROLE IN (''USER'', ''ADMIN'', ''PRO'', ''EMPLOYEE''))';
END;
/
```

> The trailing `/` is the SQL\*Plus statement terminator that Flyway recognizes for PL/SQL blocks. Do not omit it.

- [ ] **Step 2: Sanity check — file exists.**

```bash
test -s backend/src/main/resources/db/migration/oracle/V2__users_role_constraint_employee.sql && echo OK
```

Expected: prints `OK`.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/db/migration/oracle/V2__users_role_constraint_employee.sql
git commit -m "db(backend): V2 — USERS role constraint allowing EMPLOYEE"
```

---

## Task 6: Create V3 — CLIENT_BOOKING_HISTORY table and indexes

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V3__client_booking_history_table.sql`

Reproduces `ApplicationSchemaMigrator.ensureClientBookingHistoryTable()` (lines 138–170). Each `EXECUTE IMMEDIATE` is wrapped to swallow ORA-00955 (table/object exists) and ORA-01408 (index exists), making the script safe on prod where these objects already exist.

- [ ] **Step 1: Create the file.**

Create `backend/src/main/resources/db/migration/oracle/V3__client_booking_history_table.sql`:

```sql
-- V3: create CLIENT_BOOKING_HISTORY table + supporting indexes.
--
-- Reproduces ApplicationSchemaMigrator.ensureClientBookingHistoryTable(). Each
-- DDL is wrapped in a PL/SQL block that swallows ORA-00955 (-955: object
-- already exists) and ORA-01408 (-1408: index list already exists) so the
-- migration is safe on production databases where ApplicationSchemaMigrator
-- has already created these objects.
DECLARE
    e_object_exists EXCEPTION;
    e_index_exists  EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_object_exists, -955);
    PRAGMA EXCEPTION_INIT(e_index_exists, -1408);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE CLIENT_BOOKING_HISTORY (
                ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                USER_ID NUMBER(19) NOT NULL,
                TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                SALON_NAME VARCHAR2(255 CHAR) NOT NULL,
                BOOKING_ID NUMBER(19) NOT NULL,
                CARE_NAME VARCHAR2(255 CHAR) NOT NULL,
                CARE_PRICE NUMBER(10) NOT NULL,
                CARE_DURATION NUMBER(10) NOT NULL,
                APPOINTMENT_DATE DATE NOT NULL,
                APPOINTMENT_TIME TIMESTAMP NOT NULL,
                STATUS VARCHAR2(20 CHAR) NOT NULL,
                CREATED_AT TIMESTAMP NOT NULL
            )
        ]';
    EXCEPTION WHEN e_object_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_CBH_USER_DATE ON CLIENT_BOOKING_HISTORY (USER_ID, APPOINTMENT_DATE)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE UNIQUE INDEX UK_CBH_TENANT_BOOKING ON CLIENT_BOOKING_HISTORY (TENANT_SLUG, BOOKING_ID)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;
END;
/
```

- [ ] **Step 2: Sanity check.**

```bash
test -s backend/src/main/resources/db/migration/oracle/V3__client_booking_history_table.sql && echo OK
```

Expected: prints `OK`.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/db/migration/oracle/V3__client_booking_history_table.sql
git commit -m "db(backend): V3 — CLIENT_BOOKING_HISTORY table and indexes"
```

---

## Task 7: Create V4 — NOTIFICATIONS table and index

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V4__notifications_table.sql`

Same idempotent pattern as V3.

- [ ] **Step 1: Create the file.**

Create `backend/src/main/resources/db/migration/oracle/V4__notifications_table.sql`:

```sql
-- V4: create NOTIFICATIONS table + recipient index.
--
-- Reproduces ApplicationSchemaMigrator.ensureNotificationsTable(). Idempotent
-- via ORA-00955 / ORA-01408 catch — same pattern as V3.
DECLARE
    e_object_exists EXCEPTION;
    e_index_exists  EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_object_exists, -955);
    PRAGMA EXCEPTION_INIT(e_index_exists, -1408);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE NOTIFICATIONS (
                ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                RECIPIENT_ID NUMBER(19) NOT NULL,
                TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                TYPE VARCHAR2(50 CHAR) NOT NULL,
                CATEGORY VARCHAR2(30 CHAR) NOT NULL,
                TITLE VARCHAR2(255 CHAR) NOT NULL,
                MESSAGE VARCHAR2(500 CHAR) NOT NULL,
                REFERENCE_ID NUMBER(19) NOT NULL,
                REFERENCE_TYPE VARCHAR2(50 CHAR) NOT NULL,
                IS_READ NUMBER(1) DEFAULT 0 NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            )
        ]';
    EXCEPTION WHEN e_object_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_NOTIF_RECIPIENT ON NOTIFICATIONS (RECIPIENT_ID, IS_READ, CREATED_AT DESC)';
    EXCEPTION
        WHEN e_object_exists THEN NULL;
        WHEN e_index_exists THEN NULL;
    END;
END;
/
```

- [ ] **Step 2: Sanity check.**

```bash
test -s backend/src/main/resources/db/migration/oracle/V4__notifications_table.sql && echo OK
```

Expected: prints `OK`.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/db/migration/oracle/V4__notifications_table.sql
git commit -m "db(backend): V4 — NOTIFICATIONS table and recipient index"
```

---

## Task 8: Create V5 — TENANTS defaults backfill

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V5__tenant_defaults_backfill.sql`

Reproduces `ApplicationSchemaMigrator.backfillTenantDefaults()` (lines 59–75). Each `UPDATE … WHERE col IS NULL` is naturally idempotent. No `COMMIT;` — Flyway handles the transaction.

- [ ] **Step 1: Create the file.**

Create `backend/src/main/resources/db/migration/oracle/V5__tenant_defaults_backfill.sql`:

```sql
-- V5: backfill nullable defaults on TENANTS rows that pre-date their columns.
--
-- Reproduces ApplicationSchemaMigrator.backfillTenantDefaults(). Each statement
-- is naturally idempotent thanks to "WHERE col IS NULL". Flyway manages the
-- transaction — do not COMMIT here.
UPDATE TENANTS SET CLOSED_ON_HOLIDAYS = 1       WHERE CLOSED_ON_HOLIDAYS IS NULL;
UPDATE TENANTS SET BUFFER_MINUTES = 0           WHERE BUFFER_MINUTES IS NULL;
UPDATE TENANTS SET MIN_ADVANCE_MINUTES = 120    WHERE MIN_ADVANCE_MINUTES IS NULL;
UPDATE TENANTS SET MAX_ADVANCE_DAYS = 90        WHERE MAX_ADVANCE_DAYS IS NULL;
UPDATE TENANTS SET MAX_CLIENT_HOURS_PER_DAY = 8 WHERE MAX_CLIENT_HOURS_PER_DAY IS NULL;
UPDATE TENANTS SET ANNUAL_LEAVE_DAYS = 25       WHERE ANNUAL_LEAVE_DAYS IS NULL;
UPDATE TENANTS SET EMPLOYEES_ENABLED = 0        WHERE EMPLOYEES_ENABLED IS NULL;
```

- [ ] **Step 2: Sanity check.**

```bash
test -s backend/src/main/resources/db/migration/oracle/V5__tenant_defaults_backfill.sql && echo OK
```

Expected: prints `OK`.

- [ ] **Step 3: Commit.**

```bash
git add backend/src/main/resources/db/migration/oracle/V5__tenant_defaults_backfill.sql
git commit -m "db(backend): V5 — backfill TENANTS defaults"
```

---

## Task 9: Delete `ApplicationSchemaMigrator`

**Files:**
- Delete: `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java`

- [ ] **Step 1: Verify nothing references it before deletion.**

Run:

```bash
grep -rn "ApplicationSchemaMigrator" backend/src --include='*.java'
```

Expected: only the file's own definition lines (constructor, class declaration, logger). No `import` of the class from another file. If anything else references it, **stop and report** — that callsite must be cleaned up first.

- [ ] **Step 2: Delete the file.**

Run:

```bash
git rm backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java
```

- [ ] **Step 3: Verify the project still compiles.**

Run:

```bash
cd backend && mvn -q compile
```

Expected: `BUILD SUCCESS`. If it fails on a missing symbol referencing `ApplicationSchemaMigrator`, restore the file (`git checkout HEAD -- backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java`), fix that callsite, then retry.

- [ ] **Step 4: Commit.**

```bash
git commit -m "refactor(backend): remove ApplicationSchemaMigrator (replaced by Flyway V2-V5)"
```

---

## Task 10: Run the test suite to confirm no regression

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite.**

Run:

```bash
cd backend && mvn test
```

Expected: `BUILD SUCCESS`, all tests pass. The H2 schema is created by Hibernate `ddl-auto=create-drop`; Flyway is disabled in `src/test/resources/application.properties` so V*.sql files (Oracle-only syntax) are never parsed.

If a test fails:
- Read the error. If it complains about `ApplicationSchemaMigrator`, you missed a reference in Task 9 — find it (`grep -rn ApplicationSchemaMigrator backend/src/test`) and either delete the test or remove the dead import.
- If a test fails for unrelated reasons, capture the failure, **stop the plan**, and report — do not press on.

- [ ] **Step 2: No commit (no code changed in this task).**

---

## Task 11: Manual smoke test on a fresh Oracle

**Files:** none (manual verification of migration on a real Oracle DB)

This is a manual checkpoint. The user runs it, not the agent — but the agent's job is to make the verification commands easy to copy-paste.

- [ ] **Step 1: Start a clean Oracle Free instance.**

If you already have the Docker Oracle running locally, drop and recreate the app user. Otherwise:

```bash
# Replace credentials with your local Oracle setup.
docker exec -it <oracle-container> sqlplus system/<oracle-pass>@FREEPDB1 <<'SQL'
DROP USER appuser CASCADE;
CREATE USER appuser IDENTIFIED BY ChangeMe_App#2025
  DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO appuser;
SQL
```

- [ ] **Step 2: Boot the app.**

```bash
cd backend && mvn -q spring-boot:run
```

Expected logs (in order):
1. `o.f.c.internal.license.VersionPrinter : Flyway Community Edition 10.x by Redgate`
2. `o.f.c.i.database.base.BaseDatabaseType  : Database: jdbc:oracle:thin:@localhost:1521/FREEPDB1 (Oracle 23.x)`
3. `o.f.core.internal.command.DbValidate    : Successfully validated 5 migrations`
4. `o.f.c.i.s.JdbcTableSchemaHistory         : Creating Schema History table "APPUSER"."flyway_schema_history" ...`
5. Lines like `Migrating schema "APPUSER" to version "1 - baseline" ... "2 - users role constraint employee" ... "3 - client booking history table" ... "4 - notifications table" ... "5 - tenant defaults backfill"`
6. `o.f.core.internal.command.DbMigrate      : Successfully applied 5 migrations to schema "APPUSER"`
7. Hibernate then runs `update` (creates the rest of the JPA entities' tables).
8. App starts on port 8080.

Stop the app once the startup log is clean (Ctrl+C).

- [ ] **Step 3: Verify the migration history.**

```bash
docker exec -it <oracle-container> sqlplus appuser/ChangeMe_App#2025@FREEPDB1 <<'SQL'
SET LINESIZE 200
COLUMN description FORMAT A40
SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
SQL
```

Expected output: 5 rows (versions 1–5), all `success = 1`. `type` for V1 is `SQL`, V2 is `SQL` (PL/SQL is delivered through the SQL parser), V3–V5 also `SQL`.

- [ ] **Step 4: Verify the actual schema effects.**

```bash
docker exec -it <oracle-container> sqlplus appuser/ChangeMe_App#2025@FREEPDB1 <<'SQL'
SET LINESIZE 200
SELECT constraint_name, search_condition_vc
  FROM user_constraints
 WHERE table_name = 'USERS' AND constraint_type = 'C';

SELECT table_name FROM user_tables
 WHERE table_name IN ('CLIENT_BOOKING_HISTORY', 'NOTIFICATIONS');

SELECT index_name, table_name FROM user_indexes
 WHERE index_name IN (
    'IDX_CBH_USER_DATE',
    'UK_CBH_TENANT_BOOKING',
    'IDX_NOTIF_RECIPIENT'
 );
SQL
```

Expected:
- `CK_USERS_ROLE` constraint with `ROLE IN ('USER', 'ADMIN', 'PRO', 'EMPLOYEE')`.
- Both `CLIENT_BOOKING_HISTORY` and `NOTIFICATIONS` listed.
- All three indexes listed.

- [ ] **Step 5: No commit (manual verification only). If any check fails, stop, capture the issue, report.**

---

## Task 12: Manual smoke test on prod-like Oracle (existing schema)

**Files:** none (manual verification of idempotence on a database where `ApplicationSchemaMigrator` already ran)

This is the critical idempotence check. We simulate a database that already has the constraint, the two tables, and the backfilled defaults.

- [ ] **Step 1: Prepare a "prod-like" database state.**

Reset Oracle as in Task 11 Step 1, then run the SQL below to materialize the state that `ApplicationSchemaMigrator` would have produced (we do NOT boot the old app — we apply the SQL directly so we don't have to checkout previous commits).

The SQL below is the in-database equivalent of `ApplicationSchemaMigrator`'s effects, plus the minimum scaffolding from Hibernate — running it puts us in a state where V2–V5 must run idempotently.

```bash
docker exec -it <oracle-container> sqlplus appuser/ChangeMe_App#2025@FREEPDB1 <<'SQL'
-- Minimal USERS table with the old (no-EMPLOYEE) constraint.
CREATE TABLE USERS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMAIL VARCHAR2(255 CHAR),
    ROLE VARCHAR2(20 CHAR) NOT NULL,
    CONSTRAINT CK_USERS_ROLE_OLD CHECK (ROLE IN ('USER', 'ADMIN', 'PRO'))
);

-- Minimal TENANTS table with the columns V5 backfills.
CREATE TABLE TENANTS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    SLUG VARCHAR2(100 CHAR) NOT NULL,
    CLOSED_ON_HOLIDAYS NUMBER(1),
    BUFFER_MINUTES NUMBER(10),
    MIN_ADVANCE_MINUTES NUMBER(10),
    MAX_ADVANCE_DAYS NUMBER(10),
    MAX_CLIENT_HOURS_PER_DAY NUMBER(10),
    ANNUAL_LEAVE_DAYS NUMBER(10),
    EMPLOYEES_ENABLED NUMBER(1)
);
INSERT INTO TENANTS (SLUG) VALUES ('legacy-tenant-with-null-defaults');

-- Tables/indexes that V3 and V4 expect to find already there.
CREATE TABLE CLIENT_BOOKING_HISTORY (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USER_ID NUMBER(19) NOT NULL,
    TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
    SALON_NAME VARCHAR2(255 CHAR) NOT NULL,
    BOOKING_ID NUMBER(19) NOT NULL,
    CARE_NAME VARCHAR2(255 CHAR) NOT NULL,
    CARE_PRICE NUMBER(10) NOT NULL,
    CARE_DURATION NUMBER(10) NOT NULL,
    APPOINTMENT_DATE DATE NOT NULL,
    APPOINTMENT_TIME TIMESTAMP NOT NULL,
    STATUS VARCHAR2(20 CHAR) NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL
);
CREATE INDEX IDX_CBH_USER_DATE ON CLIENT_BOOKING_HISTORY (USER_ID, APPOINTMENT_DATE);
CREATE UNIQUE INDEX UK_CBH_TENANT_BOOKING ON CLIENT_BOOKING_HISTORY (TENANT_SLUG, BOOKING_ID);

CREATE TABLE NOTIFICATIONS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    RECIPIENT_ID NUMBER(19) NOT NULL,
    TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
    TYPE VARCHAR2(50 CHAR) NOT NULL,
    CATEGORY VARCHAR2(30 CHAR) NOT NULL,
    TITLE VARCHAR2(255 CHAR) NOT NULL,
    MESSAGE VARCHAR2(500 CHAR) NOT NULL,
    REFERENCE_ID NUMBER(19) NOT NULL,
    REFERENCE_TYPE VARCHAR2(50 CHAR) NOT NULL,
    IS_READ NUMBER(1) DEFAULT 0 NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IDX_NOTIF_RECIPIENT ON NOTIFICATIONS (RECIPIENT_ID, IS_READ, CREATED_AT DESC);
COMMIT;
SQL
```

The schema is now in the state `ApplicationSchemaMigrator` used to produce, **except** the USERS constraint deliberately still excludes `EMPLOYEE` (so V2 has work to do, exercising the "drop stale + recreate" branch).

- [ ] **Step 2: Boot the new code.**

```bash
cd backend && mvn -q spring-boot:run
```

Expected logs:
- Flyway sees an existing non-empty schema, no `flyway_schema_history` table → it baselines at V1: `Successfully baselined schema with version: 1`.
- Then it applies V2–V5 in order. Each migration completes successfully **because of the idempotent guards** (constraint already has EMPLOYEE → V2 no-ops; tables/indexes already exist → V3/V4 catch ORA-955/-1408; backfill `WHERE IS NULL` → V5 updates 0 rows).
- Log line: `Successfully applied 4 migrations to schema "APPUSER"` (4 — V1 was the baseline, not "applied").

- [ ] **Step 3: Verify the history.**

```bash
docker exec -it <oracle-container> sqlplus appuser/ChangeMe_App#2025@FREEPDB1 <<'SQL'
SET LINESIZE 200
COLUMN description FORMAT A40
SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
SQL
```

Expected: 5 rows. `type` for V1 = `BASELINE`. Types for V2–V5 = `SQL`. All `success = 1`.

- [ ] **Step 4: Verify schema is unchanged from prod state.**

Run the same `SELECT` queries from Task 11 step 4. Expected: identical results — the constraints, tables and indexes are still there, untouched.

- [ ] **Step 5: No commit. If anything fails, capture and report.**

---

## Task 13: Final cross-check and PR-ready commit summary

**Files:** none (review only)

- [ ] **Step 1: Confirm the commit history is clean.**

Run:

```bash
git log --oneline origin/main..HEAD | head -20
```

Expected: a sequence of focused commits (one per Task 1–9). No "wip" or "ok:" commits.

- [ ] **Step 2: Confirm no leftover references to `ApplicationSchemaMigrator`.**

```bash
grep -rn "ApplicationSchemaMigrator" backend docs 2>/dev/null
```

Expected: only mentions in `docs/superpowers/specs/` and `docs/superpowers/plans/` referencing it as the old code being replaced. No matches in `backend/src/`.

- [ ] **Step 3: Confirm tests pass once more.**

```bash
cd backend && mvn -q test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: No commit needed. The implementation work is done.**

> **Post-merge note (out of plan scope):** the auto-memory entry `project_pending_flyway.md` should be updated to reflect that the shared-schema migration is complete and the remaining pending item is the multi-tenant migration. This is the assistant's job to do when the change has actually shipped, not part of this plan.
