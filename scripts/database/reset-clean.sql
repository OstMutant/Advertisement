-- Full reset without seed data. Used by e2e tests that bootstrap their own users via sign-up.
TRUNCATE TABLE taxon_assignment, taxon_translation, taxon,
               attachment_snapshot, attachment, audit_log, advertisement, user_information
    RESTART IDENTITY CASCADE;
