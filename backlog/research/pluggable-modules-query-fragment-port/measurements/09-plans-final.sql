-- Full plans in the final state (covering index, created_at in insertion order) for the key scenarios.
\pset pager off
\pset tuples_only on
SELECT * FROM run_variants('S2 rare cat150 (3k)', '{150}', NULL, 0);
SELECT * FROM run_variants('S3 cat1 (241k)', '{1}', NULL, 0);
SELECT * FROM run_variants('S4 cat5 AND city201', '{5}', 201, 0);
