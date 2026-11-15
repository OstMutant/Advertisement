-- Schema copied from the project's changelogs (advertisement, taxon), plus variant B's local table.
CREATE TABLE advertisement (
  id BIGSERIAL PRIMARY KEY, title VARCHAR(255) NOT NULL, description VARCHAR(20000),
  ad_kind VARCHAR(20) NOT NULL DEFAULT 'OFFER', created_by BIGINT NOT NULL, updated_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ,
  deleted_by BIGINT, version BIGINT NOT NULL DEFAULT 0);
CREATE INDEX idx_advertisement_created_at ON advertisement(created_at);
CREATE INDEX idx_advertisement_created_by ON advertisement(created_by);
CREATE INDEX idx_advertisement_deleted_at ON advertisement(deleted_at);
CREATE TABLE taxon (id BIGSERIAL PRIMARY KEY, type VARCHAR(64) NOT NULL, code VARCHAR(64),
  deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE taxon_assignment (entity_type VARCHAR(64) NOT NULL, entity_id BIGINT NOT NULL,
  taxon_id BIGINT NOT NULL REFERENCES taxon(id), assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_taxon_assignment PRIMARY KEY (entity_type, entity_id, taxon_id));
CREATE INDEX idx_taxon_assignment_taxon_id ON taxon_assignment(taxon_id);

-- 200 categories (ids 1..200), 100 cities (ids 201..300)
INSERT INTO taxon(type) SELECT 'CATEGORY' FROM generate_series(1,200);
INSERT INTO taxon(type) SELECT 'CITY' FROM generate_series(1,100);

-- 1,000,000 advertisements over two years, 5% soft-deleted
INSERT INTO advertisement(title, description, ad_kind, created_by, created_at, updated_at, deleted_at)
SELECT 'Ad ' || g, repeat('x', 200), (ARRAY['OFFER','REQUEST','PRODUCT'])[1 + g % 3], 1 + (g % 20000),
       ts, ts, CASE WHEN random() < 0.05 THEN ts + interval '1 day' END
FROM (SELECT g, NOW() - (random() * interval '730 days') AS ts FROM generate_series(1,1000000) g) s;

-- skewed categories: 1..2 per ad (category 1 is the most popular); one city per ad, skewed
INSERT INTO taxon_assignment(entity_type, entity_id, taxon_id)
SELECT DISTINCT 'ADVERTISEMENT', a.id, 1 + floor(200 * power(random(), 3))::int
FROM advertisement a, generate_series(1, 1 + (a.id % 2)) k
ON CONFLICT DO NOTHING;
INSERT INTO taxon_assignment(entity_type, entity_id, taxon_id)
SELECT 'ADVERTISEMENT', a.id, 201 + floor(100 * power(random(), 2))::int FROM advertisement a;
-- 50,000 provider profiles share the table, as in the real schema
INSERT INTO taxon_assignment(entity_type, entity_id, taxon_id)
SELECT 'PROVIDER_PROFILE', g, 1 + floor(200 * power(random(), 3))::int FROM generate_series(1,50000) g;
INSERT INTO taxon_assignment(entity_type, entity_id, taxon_id)
SELECT 'PROVIDER_PROFILE', g, 201 + floor(100 * power(random(), 2))::int FROM generate_series(1,50000) g;

-- Variant B: the owning module's local association table (FK only to its own table)
CREATE TABLE advertisement_taxon (
  advertisement_id BIGINT NOT NULL REFERENCES advertisement(id) ON DELETE CASCADE,
  taxon_id BIGINT NOT NULL,
  PRIMARY KEY (advertisement_id, taxon_id));
INSERT INTO advertisement_taxon SELECT entity_id, taxon_id FROM taxon_assignment WHERE entity_type = 'ADVERTISEMENT';
CREATE INDEX idx_advertisement_taxon_taxon ON advertisement_taxon(taxon_id, advertisement_id);
VACUUM ANALYZE;
SELECT taxon_id, count(*) FROM taxon_assignment WHERE entity_type='ADVERTISEMENT' AND taxon_id IN (1,2,5,50,150,201,250) GROUP BY 1 ORDER BY 1;
SELECT count(*) AS assignments FROM taxon_assignment;
