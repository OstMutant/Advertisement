INSERT INTO user_information (name, email, password_hash, role, created_at, updated_at, locale)
SELECT
  'User ' || i,
  'user' || i || '@example.com',
  'hashed_password_' || i,
  CASE WHEN i % 3 = 0 THEN 'ADMIN' WHEN i % 3 = 1 THEN 'USER' ELSE 'MODERATOR' END,
  NOW(), NOW(), 'uk'
FROM generate_series(1, 50) AS s(i);

INSERT INTO advertisement (title, description, created_at, updated_at, created_by_user_id, last_modified_by_user_id)
SELECT
  'Test Advertisement ' || i,
  'This is a sample description for advertisement #' || i,
  NOW(), NOW(), uid.id, uid.id
FROM generate_series(1, 50) AS s(i),
     (SELECT id FROM user_information ORDER BY RANDOM() LIMIT 1) AS uid;
