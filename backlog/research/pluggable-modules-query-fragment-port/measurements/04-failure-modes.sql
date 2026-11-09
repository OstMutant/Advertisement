-- Failure modes of a fragment spliced into another module's statement, on tiny temp tables.
-- Each block prints what PostgreSQL does; the expected outcome is in the \echo line above it.
\pset pager off
CREATE TEMP TABLE adv (id bigint PRIMARY KEY, created_at timestamptz DEFAULT now());
INSERT INTO adv(id) SELECT g FROM generate_series(1, 10) g;
CREATE TEMP TABLE tax_as (entity_type text, entity_id bigint, taxon_id bigint, PRIMARY KEY (entity_type, entity_id, taxon_id));
INSERT INTO tax_as SELECT 'ADVERTISEMENT', g, 5 FROM generate_series(1, 3) g;

\echo '--- 1a. fragment with an UNQUALIFIED consumer column "id": resolves outward to adv.id while tax_as has no id column (3 rows)'
SELECT count(*) FROM adv a WHERE EXISTS (SELECT 1 FROM tax_as x WHERE x.entity_type = 'ADVERTISEMENT' AND x.entity_id = id AND x.taxon_id = 5);
ALTER TABLE tax_as ADD COLUMN id bigserial;
\echo '--- 1b. same statement after the provider adds its own id column: "id" is now captured by the inner table, result changes silently'
SELECT count(*) FROM adv a WHERE EXISTS (SELECT 1 FROM tax_as x WHERE x.entity_type = 'ADVERTISEMENT' AND x.entity_id = id AND x.taxon_id = 5);

\echo '--- 2. consumer alias equals the provider internal alias: ta.id now means tax_as.id (inner scope wins), silently wrong'
SELECT count(*) FROM adv ta WHERE EXISTS (SELECT 1 FROM tax_as ta WHERE ta.entity_type = 'ADVERTISEMENT' AND ta.entity_id = ta.id AND ta.taxon_id = 5);
\echo '--- 2b. provider aliases start with an underscore, consumer aliases must start with a letter: no capture possible (3 rows)'
SELECT count(*) FROM adv ta WHERE EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_type = 'ADVERTISEMENT' AND _ta.entity_id = ta.id AND _ta.taxon_id = 5);

\echo '--- 3. fragment placed outside the scope of its alias (count wrapper): loud error, not a wrong answer'
SELECT count(*) FROM (SELECT a.id FROM adv a) sub WHERE EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id);
\echo '--- 3b. same wrapper with the column reference rebuilt for the wrapper alias: works (3 rows)'
SELECT count(*) FROM (SELECT a.id FROM adv a) sub WHERE EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_type = 'ADVERTISEMENT' AND _ta.entity_id = sub.id AND _ta.taxon_id = 5);

\echo '--- 4. a fragment containing OR appended without parentheses changes precedence: consumer filter id > 8 is lost (expect 0, gets 3)'
SELECT count(*) FROM adv a WHERE a.id > 8 AND EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id AND _ta.taxon_id = 5) OR a.id < 0
  OR EXISTS (SELECT 1 FROM tax_as _tb WHERE _tb.entity_id = a.id AND _tb.taxon_id = 5);
\echo '--- 4b. same fragment wrapped in parentheses by the consumer (expect 0)'
SELECT count(*) FROM adv a WHERE a.id > 8 AND (EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id AND _ta.taxon_id = 5) OR a.id < 0
  OR EXISTS (SELECT 1 FROM tax_as _tb WHERE _tb.entity_id = a.id AND _tb.taxon_id = 5));

\echo '--- 5. same fragment in both branches of a UNION, one alias per branch (expect 3)'
SELECT count(*) FROM (
  SELECT a.id FROM adv a WHERE a.id <= 5 AND EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id AND _ta.taxon_id = 5)
  UNION
  SELECT b.id FROM adv b WHERE b.id > 5 OR EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = b.id AND _ta.taxon_id = 5 AND false)) u
  WHERE u.id <= 3;

\echo '--- 6. composite key: the consumer row is itself an EntityRef (entity_type column + entity_id column), as in audit_log or attachment'
CREATE TEMP TABLE ev (entity_type text, entity_id bigint);
INSERT INTO ev VALUES ('ADVERTISEMENT', 1), ('ADVERTISEMENT', 9), ('PROVIDER_PROFILE', 1);
SELECT e.entity_type, e.entity_id FROM ev e WHERE EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_type = e.entity_type AND _ta.entity_id = e.entity_id AND _ta.taxon_id = 5);

\echo '--- 7. fragment in ORDER BY (the shape a promotion pin would need): valid SQL, rows with the taxon first'
SELECT a.id FROM adv a ORDER BY CASE WHEN EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id AND _ta.taxon_id = 5) THEN 0 ELSE 1 END, a.id DESC LIMIT 5;

\echo '--- 8. provider table missing (module removed but a stale fragment still sent): statement fails loudly'
DROP TABLE tax_as;
SELECT count(*) FROM adv a WHERE EXISTS (SELECT 1 FROM tax_as _ta WHERE _ta.entity_id = a.id);
