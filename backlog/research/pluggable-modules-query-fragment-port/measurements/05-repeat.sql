-- Five rounds of every scenario (summary lines only); medians are computed by 06-median.py.
-- Usage: psql -v phase=<label> -f 05-repeat.sql
\pset pager off
\pset tuples_only on
\pset format unaligned
\o /dev/null
SELECT * FROM run_variants('warm', '{5}', NULL, 0);
SELECT * FROM run_variants('warm', '{150}', NULL, 0);
\o
SELECT :'phase', g, s.* FROM generate_series(1, 5) g, LATERAL (
  SELECT * FROM summary('S1 cat5 (31k)', '{5}', NULL, 0 * g)
  UNION ALL SELECT * FROM summary('S1a cat50 (6.3k)', '{50}', NULL, 0 * g)
  UNION ALL SELECT * FROM summary('S1b cat2 (66k)', '{2}', NULL, 0 * g)
  UNION ALL SELECT * FROM summary('S2 rare cat150 (3k)', '{150}', NULL, 0 * g)
  UNION ALL SELECT * FROM summary('S3 cat1 (241k)', '{1}', NULL, 0 * g)
  UNION ALL SELECT * FROM summary('S4 cat5 AND city201', '{5}', 201, 0 * g)
  UNION ALL SELECT * FROM summary('S5 cat5 OFFSET 1000', '{5}', NULL, 1000 + 0 * g)
  UNION ALL SELECT * FROM summary('S6 rare cat150 AND city250', '{150}', 250, 0 * g)
  UNION ALL SELECT * FROM summary('S7 three cats 5,50,150', '{5,50,150}', NULL, 0 * g)) s;
