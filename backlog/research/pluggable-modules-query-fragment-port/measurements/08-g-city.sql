-- G (array shape) with category 5 AND city 201, once, with a 60 s timeout.
\pset pager off
\timing on
SET statement_timeout = '60s';
SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL
  AND a.id = ANY (ARRAY(SELECT ta.entity_id FROM taxon_assignment ta WHERE ta.entity_type = 'ADVERTISEMENT' AND ta.taxon_id = ANY('{5}')))
  AND a.id = ANY (ARRAY(SELECT tc.entity_id FROM taxon_assignment tc WHERE tc.entity_type = 'ADVERTISEMENT' AND tc.taxon_id = 201));
