\pset pager off
\echo '--- countByTaxonIds (usage counts) with the existing taxon_id index only'
DROP INDEX idx_taxon_assignment_type_taxon_entity;
EXPLAIN (ANALYZE) SELECT taxon_id, COUNT(*) FROM taxon_assignment WHERE taxon_id = ANY(ARRAY(SELECT generate_series(1,200)::bigint)) GROUP BY taxon_id;
CREATE INDEX idx_ta_taxon_type_entity ON taxon_assignment(taxon_id, entity_type, entity_id);
DROP INDEX idx_taxon_assignment_taxon_id;
VACUUM ANALYZE taxon_assignment;
\echo '--- C1b: (taxon_id, entity_type, entity_id) replaces the taxon_id index'
EXPLAIN (ANALYZE) SELECT taxon_id, COUNT(*) FROM taxon_assignment WHERE taxon_id = ANY(ARRAY(SELECT generate_series(1,200)::bigint)) GROUP BY taxon_id;
EXPLAIN (ANALYZE) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{5}');
EXPLAIN (ANALYZE) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{150}');
EXPLAIN (ANALYZE) SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY('{5,50,150}');
