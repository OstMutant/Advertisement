package org.ost.taxon.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.taxon.entities.TaxonTranslation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class TaxonTranslationRepository {

    private static final RowMapper<TaxonTranslation> ROW_MAPPER = (rs, _) ->
            TaxonTranslation.builder()
                            .taxonId(rs.getObject("taxon_id", Long.class))
                            .locale(rs.getString("locale"))
                            .name(rs.getString("name"))
                            .description(rs.getString("description"))
                            .build();

    private final JdbcClient jdbcClient;

    public void saveAll(@NonNull Long taxonId, @NonNull Collection<TaxonTranslation> translations) {
        for (TaxonTranslation t : translations) {
            jdbcClient.sql("""
                            INSERT INTO taxon_translation (taxon_id, locale, name, description)
                            VALUES (:taxonId, :locale, :name, :description)
                            ON CONFLICT (taxon_id, locale) DO UPDATE
                                SET name = EXCLUDED.name, description = EXCLUDED.description
                            """)
                      .paramSource(new MapSqlParameterSource()
                              .addValue("taxonId",     taxonId)
                              .addValue("locale",      t.getLocale())
                              .addValue("name",        t.getName())
                              .addValue("description", t.getDescription()))
                      .update();
        }
    }

    public List<TaxonTranslation> findAllByTaxonId(@NonNull Long taxonId) {
        return jdbcClient.sql("""
                        SELECT taxon_id, locale, name, description
                        FROM taxon_translation
                        WHERE taxon_id = :taxonId
                        """)
                         .paramSource(new MapSqlParameterSource("taxonId", taxonId))
                         .query(ROW_MAPPER)
                         .list();
    }

    public List<TaxonTranslation> findAllByTaxonIds(@NonNull Collection<Long> taxonIds) {
        return jdbcClient.sql("""
                        SELECT taxon_id, locale, name, description
                        FROM taxon_translation
                        WHERE taxon_id = ANY(:taxonIds)
                        """)
                         .paramSource(new MapSqlParameterSource("taxonIds", taxonIds.toArray(new Long[0])))
                         .query(ROW_MAPPER)
                         .list();
    }

    public void deleteAllByTaxonId(@NonNull Long taxonId) {
        jdbcClient.sql("DELETE FROM taxon_translation WHERE taxon_id = :taxonId")
                  .paramSource(new MapSqlParameterSource("taxonId", taxonId))
                  .update();
    }
}
