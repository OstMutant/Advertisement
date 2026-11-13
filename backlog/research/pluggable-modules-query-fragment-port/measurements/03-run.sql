-- Runs every scenario three times (warm cache); the third run is the one kept in the result files.
-- Usage: psql -v phase=<label> -f 03-run.sql ; the phase label names the index state.
\pset pager off
\pset tuples_only on
SELECT taxon_id, count(*) FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id IN (1,2,5,50,150,201,250) GROUP BY 1 ORDER BY 1;
\o /dev/null
SELECT * FROM run_variants('warm', '{5}', NULL, 0);
SELECT * FROM run_variants('warm', '{1}', NULL, 0);
SELECT * FROM run_variants('warm', '{150}', NULL, 0);
SELECT * FROM run_variants('warm', '{5}', NULL, 0);
SELECT * FROM run_variants('warm', '{150}', NULL, 0);
\o
\echo ===== SUMMARY :phase
SELECT * FROM summary('S1 cat5 (31k)', '{5}', NULL, 0);
SELECT * FROM summary('S1a cat50 (6.3k)', '{50}', NULL, 0);
SELECT * FROM summary('S1b cat2 (66k)', '{2}', NULL, 0);
SELECT * FROM summary('S2 rare cat150 (2.9k)', '{150}', NULL, 0);
SELECT * FROM summary('S3 cat1 (242k)', '{1}', NULL, 0);
SELECT * FROM summary('S4 cat5 AND city201', '{5}', 201, 0);
SELECT * FROM summary('S5 cat5 OFFSET 1000', '{5}', NULL, 1000);
SELECT * FROM summary('S6 rare cat150 AND city250', '{150}', 250, 0);
SELECT * FROM summary('S7 three cats 5,50,150', '{5,50,150}', NULL, 0);
\echo ===== FULL PLANS :phase
SELECT * FROM run_variants('S1 cat5 (31k)', '{5}', NULL, 0);
SELECT * FROM run_variants('S2 rare cat150 (2.9k)', '{150}', NULL, 0);
SELECT * FROM run_variants('S3 cat1 (242k)', '{1}', NULL, 0);
SELECT * FROM run_variants('S4 cat5 AND city201', '{5}', 201, 0);
SELECT * FROM run_variants('S6 rare cat150 AND city250', '{150}', 250, 0);
