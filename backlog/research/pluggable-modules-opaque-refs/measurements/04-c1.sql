\pset pager off
\timing on
\o /dev/null
SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{1}');
SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{1}');
\o
\timing off
\echo '--- before: plan of the taxon query (category 5)'
EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{5}');
CREATE INDEX idx_taxon_assignment_type_taxon_entity ON taxon_assignment(entity_type, taxon_id, entity_id);
VACUUM ANALYZE taxon_assignment;
\echo '--- after C1 index: category 5, 1, 150'
EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{5}');
EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{1}');
EXPLAIN (ANALYZE, BUFFERS) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{150}');
\timing on
\o /dev/null
SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{1}');
\o
