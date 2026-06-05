-- Resets all application data while preserving schema and Liquibase metadata.
-- Seed users are re-inserted so that Playwright smoke tests can run immediately.
-- Password for all users: "password"

TRUNCATE TABLE taxon_assignment, taxon_translation, taxon,
               attachment_snapshot, attachment, audit_log, advertisement, user_information
    RESTART IDENTITY CASCADE;

INSERT INTO user_information (name, email, password_hash, role, created_at, updated_at, locale)
VALUES
    ('User 1', 'user1@example.com', '$2b$10$PcXAK.QKVMkaD4ferCdPqOpM7RPk.Y5EJ9.eKPZM45833Viqxp2Mq', 'USER',      NOW(), NOW(), 'uk'),
    ('User 2', 'user2@example.com', '$2b$10$PcXAK.QKVMkaD4ferCdPqOpM7RPk.Y5EJ9.eKPZM45833Viqxp2Mq', 'MODERATOR', NOW(), NOW(), 'uk'),
    ('User 3', 'user3@example.com', '$2b$10$PcXAK.QKVMkaD4ferCdPqOpM7RPk.Y5EJ9.eKPZM45833Viqxp2Mq', 'ADMIN',     NOW(), NOW(), 'uk');

INSERT INTO advertisement (title, description, created_by_user_id, created_at, updated_at)
SELECT 'Seed Ad ' || gs, 'Seed advertisement ' || gs, 1, NOW(), NOW()
FROM generate_series(1, 3) gs;
