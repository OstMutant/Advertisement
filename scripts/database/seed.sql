-- Dev test users password: "password"
INSERT INTO user_information (name, email, password_hash, role, created_at, updated_at, locale)
SELECT
  'User ' || i,
  'user' || i || '@example.com',
  '$2b$10$PcXAK.QKVMkaD4ferCdPqOpM7RPk.Y5EJ9.eKPZM45833Viqxp2Mq',
  CASE WHEN i % 3 = 0 THEN 'ADMIN' WHEN i % 3 = 1 THEN 'USER' ELSE 'MODERATOR' END,
  NOW(), NOW(), 'uk'
FROM generate_series(1, 50) AS s(i)
ON CONFLICT (email) DO NOTHING;

INSERT INTO advertisement (title, description, created_at, updated_at, created_by_user_id, last_modified_by_user_id)
SELECT
  'Test Advertisement ' || ROW_NUMBER() OVER (ORDER BY u.id),
  'This is a sample description for advertisement #' || ROW_NUMBER() OVER (ORDER BY u.id),
  NOW(), NOW(), u.id, u.id
FROM user_information u
WHERE NOT EXISTS (SELECT 1 FROM advertisement LIMIT 1);
