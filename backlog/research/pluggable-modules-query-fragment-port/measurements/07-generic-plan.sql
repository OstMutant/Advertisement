-- What a JDBC server-side prepared statement can end up with after five executions: one generic plan
-- for every parameter value. Forced here so the worst case is visible; covering index state as left by 05.
\pset pager off
PREPARE f_page(bigint[]) AS SELECT a.id, a.title, a.description, a.ad_kind, a.created_at, a.updated_at, a.created_by, a.version
  FROM advertisement a WHERE a.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM taxon_assignment _ta WHERE _ta.entity_type = 'ADVERTISEMENT' AND _ta.entity_id = a.id AND _ta.taxon_id = ANY($1))
  ORDER BY a.created_at DESC, a.id DESC LIMIT 20 OFFSET 0;
PREPARE f_count(bigint[]) AS SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM taxon_assignment _ta WHERE _ta.entity_type = 'ADVERTISEMENT' AND _ta.entity_id = a.id AND _ta.taxon_id = ANY($1));
PREPARE a_page(bigint[]) AS SELECT a.id, a.title, a.description, a.ad_kind, a.created_at, a.updated_at, a.created_by, a.version
  FROM advertisement a WHERE a.deleted_at IS NULL AND a.id = ANY($1) ORDER BY a.created_at DESC, a.id DESC LIMIT 20 OFFSET 0;
PREPARE a_count(bigint[]) AS SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL AND a.id = ANY($1);
CREATE TEMP TABLE ids AS SELECT taxon_id, array_agg(DISTINCT entity_id) AS ids FROM taxon_assignment
  WHERE entity_type = 'ADVERTISEMENT' AND taxon_id IN (1, 5, 150) GROUP BY taxon_id;

SET plan_cache_mode = force_generic_plan;
\echo '===== GENERIC plans'
\echo '--- F.page rare 150'
EXPLAIN (ANALYZE) EXECUTE f_page('{150}');
\echo '--- F.page big 1'
EXPLAIN (ANALYZE) EXECUTE f_page('{1}');
\echo '--- F.count rare 150'
EXPLAIN (ANALYZE) EXECUTE f_count('{150}');
\echo '--- F.count mid 5'
EXPLAIN (ANALYZE) EXECUTE f_count('{5}');
\echo '--- F.count big 1'
EXPLAIN (ANALYZE) EXECUTE f_count('{1}');
SELECT ids AS i150 FROM ids WHERE taxon_id = 150 \gset
SELECT ids AS i5 FROM ids WHERE taxon_id = 5 \gset
SELECT ids AS i1 FROM ids WHERE taxon_id = 1 \gset
\echo '--- A.page rare 150 (ids bound)'
EXPLAIN (ANALYZE) EXECUTE a_page(:'i150');
\echo '--- A.page big 1 (ids bound)'
EXPLAIN (ANALYZE) EXECUTE a_page(:'i1');
\echo '--- A.count mid 5 (ids bound)'
EXPLAIN (ANALYZE) EXECUTE a_count(:'i5');
\echo '--- A.count big 1 (ids bound)'
EXPLAIN (ANALYZE) EXECUTE a_count(:'i1');
