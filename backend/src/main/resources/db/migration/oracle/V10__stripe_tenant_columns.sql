-- Stripe subscription columns on TENANTS (shared app schema).
--
-- Adds 7 columns to support the subscription flow introduced by Stripe PR1.
-- Tenants existing before this migration are grandfathered to VITRINE_FREE /
-- VITRINE / FREE via the column DEFAULT values below.
--
-- The matching JPA fields live on Tenant.java. Hibernate ddl-auto=update on
-- legacy environments would also add them from the entity, but Flyway is the
-- source of truth in shared/prod environments.
--
-- Note: the oracle/ migration directory targets the shared app schema; the
-- JDBC user is already connected to that schema, so identifiers are not
-- qualified (mirroring V8/V9 style).

ALTER TABLE TENANTS ADD (
    STRIPE_CUSTOMER_ID       VARCHAR2(255 CHAR),
    STRIPE_SUBSCRIPTION_ID   VARCHAR2(255 CHAR),
    SUBSCRIPTION_STATUS      VARCHAR2(32 CHAR) DEFAULT 'VITRINE_FREE' NOT NULL,
    SUBSCRIPTION_TIER        VARCHAR2(32 CHAR) DEFAULT 'VITRINE' NOT NULL,
    SUBSCRIPTION_BILLING     VARCHAR2(16 CHAR) DEFAULT 'FREE' NOT NULL,
    CURRENT_PERIOD_END       TIMESTAMP,
    TRIAL_END                TIMESTAMP
);

CREATE UNIQUE INDEX UK_TENANTS_STRIPE_CUSTOMER
    ON TENANTS (STRIPE_CUSTOMER_ID);
CREATE UNIQUE INDEX UK_TENANTS_STRIPE_SUBSCRIPTION
    ON TENANTS (STRIPE_SUBSCRIPTION_ID);
CREATE INDEX IX_TENANTS_SUB_STATUS
    ON TENANTS (SUBSCRIPTION_STATUS);
