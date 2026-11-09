-- Variant G (fragment, array shape): a.id = ANY(ARRAY(subquery)) -- the id set is built once inside PostgreSQL (InitPlan), never in Java.
-- Variant F (fragment): the consumer's page and count with the Taxon-built EXISTS on taxon_assignment.
-- Variants A (ids first, bound as one bigint[]) and B (local advertisement_taxon) re-run in the same database,
-- same session, so every number in the report comes from one server state. Values are bound with USING
-- (typed parameters, as JDBC binds them), never inlined into the SQL text.
CREATE OR REPLACE FUNCTION run_variants(label text, cat bigint[], city bigint, off int) RETURNS SETOF text LANGUAGE plpgsql AS $$
DECLARE
  sel text := 'SELECT a.id, a.title, a.description, a.ad_kind, a.created_at, a.updated_at, a.created_by, a.version FROM advertisement a ';
  page text := ' ORDER BY a.created_at DESC, a.id DESC LIMIT 20 OFFSET $2';
  frag text := ' AND EXISTS (SELECT 1 FROM taxon_assignment ta WHERE ta.entity_type = ''ADVERTISEMENT'' AND ta.entity_id = a.id AND ta.taxon_id = ANY($1))'
       || CASE WHEN city IS NOT NULL THEN ' AND EXISTS (SELECT 1 FROM taxon_assignment tc WHERE tc.entity_type = ''ADVERTISEMENT'' AND tc.entity_id = a.id AND tc.taxon_id = $3)' ELSE '' END;
  arr  text := ' AND a.id = ANY (ARRAY(SELECT ta.entity_id FROM taxon_assignment ta WHERE ta.entity_type = ''ADVERTISEMENT'' AND ta.taxon_id = ANY($1)))'
       || CASE WHEN city IS NOT NULL THEN ' AND a.id = ANY (ARRAY(SELECT tc.entity_id FROM taxon_assignment tc WHERE tc.entity_type = ''ADVERTISEMENT'' AND tc.taxon_id = $3))' ELSE '' END;
  loc  text := ' AND EXISTS (SELECT 1 FROM advertisement_taxon t WHERE t.advertisement_id = a.id AND t.taxon_id = ANY($1))'
       || CASE WHEN city IS NOT NULL THEN ' AND EXISTS (SELECT 1 FROM advertisement_taxon c WHERE c.advertisement_id = a.id AND c.taxon_id = $3)' ELSE '' END;
  ids bigint[]; city_ids bigint[]; t0 timestamptz; l text;
BEGIN
  RETURN NEXT '##### ' || label;
  t0 := clock_timestamp();
  SELECT coalesce(array_agg(DISTINCT entity_id), '{}') INTO ids FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY(cat);
  IF city IS NOT NULL THEN
    SELECT coalesce(array_agg(DISTINCT entity_id), '{}') INTO city_ids FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = city;
    SELECT coalesce(array_agg(x), '{}') INTO ids FROM (SELECT unnest(ids) INTERSECT SELECT unnest(city_ids)) s(x);
  END IF;
  RETURN NEXT format('A.lookup: %s ids, %s ms', cardinality(ids), round(extract(epoch FROM clock_timestamp() - t0) * 1000, 1));
  RETURN NEXT '--- A.page';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL AND a.id = ANY($1)' || page USING ids, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- A.count';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL AND a.id = ANY($1)' USING ids, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- F.page';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL' || frag || page USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- F.count';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL' || frag USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  -- G with a city filter builds a second, large array (city 201: ~99k ids) searched linearly per row; it ran for minutes, so it is skipped here and measured once with a timeout in 08-g-city.sql.
  IF city IS NULL THEN
  RETURN NEXT '--- G.page';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL' || arr || page USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- G.count';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL' || arr USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  END IF;
  RETURN NEXT '--- B.page';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL' || loc || page USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- B.count';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL' || loc USING cat, off, city LOOP RETURN NEXT l; END LOOP;
END $$;

-- One line per measured statement: label, planning ms, execution ms (parsed from the plan text).
CREATE OR REPLACE FUNCTION summary(label text, cat bigint[], city bigint, off int) RETURNS TABLE(step text, info text) LANGUAGE plpgsql AS $$
DECLARE l text; cur text := '';
BEGIN
  FOR l IN SELECT * FROM run_variants(label, cat, city, off) LOOP
    IF l LIKE 'A.lookup%' OR l LIKE '#####%' THEN step := l; info := ''; RETURN NEXT;
    ELSIF l LIKE '--- %' THEN cur := substr(l, 5);
    ELSIF l LIKE 'Planning Time%' THEN step := cur; info := l;
    ELSIF l LIKE 'Execution Time%' THEN info := info || '  ' || l; RETURN NEXT;
    END IF;
  END LOOP;
END $$;
