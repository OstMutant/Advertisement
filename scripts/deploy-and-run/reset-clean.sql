-- Full reset without seed data. Used by e2e tests that bootstrap their own users via sign-up.
DO $$
DECLARE tables text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ') INTO tables
    FROM pg_tables
    WHERE schemaname = 'public' AND tablename NOT IN ('databasechangelog', 'databasechangeloglock');
    EXECUTE 'TRUNCATE TABLE ' || tables || ' RESTART IDENTITY CASCADE';
END $$;
