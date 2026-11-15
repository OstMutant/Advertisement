-- Runs variant A (ids resolved first, then bound as one bigint[] parameter, as JDBC does) and variant B (local table, one statement).
CREATE OR REPLACE FUNCTION measure(cat bigint[], city bigint, off int) RETURNS SETOF text LANGUAGE plpgsql AS $$
DECLARE ids bigint[]; city_ids bigint[]; t0 timestamptz; l text; sel text :=
  'SELECT a.id, a.title, a.description, a.ad_kind, a.created_at, a.updated_at, a.created_by, a.version FROM advertisement a ';
BEGIN
  t0 := clock_timestamp();
  SELECT coalesce(array_agg(DISTINCT entity_id), '{}') INTO ids FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = ANY(cat);
  RETURN NEXT format('A.1 taxon query (category): %s ids, %s ms', cardinality(ids), round(extract(epoch FROM clock_timestamp() - t0) * 1000, 1));
  IF city IS NOT NULL THEN
    t0 := clock_timestamp();
    SELECT coalesce(array_agg(DISTINCT entity_id), '{}') INTO city_ids FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT' AND taxon_id = city;
    RETURN NEXT format('A.1b taxon query (city): %s ids, %s ms', cardinality(city_ids), round(extract(epoch FROM clock_timestamp() - t0) * 1000, 1));
    t0 := clock_timestamp();
    SELECT coalesce(array_agg(x), '{}') INTO ids FROM (SELECT unnest(ids) INTERSECT SELECT unnest(city_ids)) s(x);
    RETURN NEXT format('A.1c intersection (Java retainAll in the real code; here SQL): %s ids, %s ms', cardinality(ids), round(extract(epoch FROM clock_timestamp() - t0) * 1000, 1));
  END IF;
  RETURN NEXT '--- A.2 page query, ANY($1) bound';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL AND a.id = ANY($1) ORDER BY a.created_at DESC, a.id DESC LIMIT 20 OFFSET $2' USING ids, off LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- A.3 count query, ANY($1) bound';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL AND a.id = ANY($1)' USING ids LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- B.2 page query, local advertisement_taxon, EXISTS';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || sel || 'WHERE a.deleted_at IS NULL'
      || ' AND EXISTS (SELECT 1 FROM advertisement_taxon t WHERE t.advertisement_id = a.id AND t.taxon_id = ANY($1))'
      || CASE WHEN city IS NOT NULL THEN ' AND EXISTS (SELECT 1 FROM advertisement_taxon c WHERE c.advertisement_id = a.id AND c.taxon_id = $3)' ELSE '' END
      || ' ORDER BY a.created_at DESC, a.id DESC LIMIT 20 OFFSET $2' USING cat, off, city LOOP RETURN NEXT l; END LOOP;
  RETURN NEXT '--- B.3 count query, local advertisement_taxon, EXISTS';
  FOR l IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL'
      || ' AND EXISTS (SELECT 1 FROM advertisement_taxon t WHERE t.advertisement_id = a.id AND t.taxon_id = ANY($1))'
      || CASE WHEN city IS NOT NULL THEN ' AND EXISTS (SELECT 1 FROM advertisement_taxon c WHERE c.advertisement_id = a.id AND c.taxon_id = $2)' ELSE '' END
      USING cat, city LOOP RETURN NEXT l; END LOOP;
END $$;
