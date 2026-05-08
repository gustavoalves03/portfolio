-- V7__tenant_name_nullable.sql
-- Allow new tenants to be provisioned with name=null so the onboarding
-- wizard can require an explicit confirmation step.
ALTER TABLE TENANTS MODIFY (name NULL);
