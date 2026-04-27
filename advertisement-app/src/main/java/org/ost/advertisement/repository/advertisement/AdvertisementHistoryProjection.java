package org.ost.advertisement.repository.advertisement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementHistoryDto;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.model.ChangeEntry;
import org.ost.sqlengine.projection.SqlFieldDefinition;
import org.ost.sqlengine.projection.SqlFixedProjection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

@Component
public class AdvertisementHistoryProjection extends SqlFixedProjection<AdvertisementHistoryDto> {

    private static final String QUERY = """
            SELECT s.id, s.version, s.action_type, s.title, s.description,
                   s.changes_summary::text AS changes_summary, s.attachment_urls, s.created_at,
                   COALESCE(u.name, '—') AS changed_by_name,
                   prev.id               AS prev_id,
                   prev.title            AS prev_title,
                   prev.description      AS prev_description,
                   prev.attachment_urls  AS prev_urls
            FROM advertisement_snapshot s
            LEFT JOIN user_information u ON u.id = s.changed_by_user_id
            LEFT JOIN advertisement_snapshot prev
                   ON prev.advertisement_id = s.advertisement_id
                  AND prev.version = s.version - 1
            WHERE s.advertisement_id = :adId
              AND (CAST(:filterUserId AS BIGINT) IS NULL OR s.changed_by_user_id = :filterUserId)
            ORDER BY s.version DESC
            LIMIT 50
            """;

    private static final String SOURCE =
            "advertisement_snapshot s LEFT JOIN user_information u ON u.id = s.changed_by_user_id";

    static final SqlFieldDefinition<Long>     SNAPSHOT_ID      = id("s.id",                         "id");
    static final SqlFieldDefinition<Integer>  VERSION          = intVal("s.version",                "version");
    static final SqlFieldDefinition<String>   ACTION_TYPE_STR  = str("s.action_type",               "action_type");
    static final SqlFieldDefinition<String>   CHANGED_BY_NAME  = str("COALESCE(u.name,'—')",        "changed_by_name");
    static final SqlFieldDefinition<Instant>  CREATED_AT       = instant("s.created_at",            "created_at");
    static final SqlFieldDefinition<String>   TITLE            = str("s.title",                     "title");
    static final SqlFieldDefinition<String>   DESCRIPTION      = str("s.description",               "description");
    static final SqlFieldDefinition<String>   CHANGES_SUMMARY  = str("s.changes_summary::text",     "changes_summary");
    static final SqlFieldDefinition<String[]> ATTACHMENT_URLS  = strArray("s.attachment_urls",      "attachment_urls");
    static final SqlFieldDefinition<Long>     PREV_ID          = longVal("prev.id",                 "prev_id");
    static final SqlFieldDefinition<String>   PREV_TITLE       = str("prev.title",                  "prev_title");
    static final SqlFieldDefinition<String>   PREV_DESCRIPTION = str("prev.description",            "prev_description");
    static final SqlFieldDefinition<String[]> PREV_URLS        = strArray("prev.attachment_urls",   "prev_urls");

    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;

    public AdvertisementHistoryProjection(@Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(List.of(SNAPSHOT_ID, VERSION, ACTION_TYPE_STR, CHANGED_BY_NAME, CREATED_AT,
                      TITLE, DESCRIPTION, CHANGES_SUMMARY, ATTACHMENT_URLS,
                      PREV_ID, PREV_TITLE, PREV_DESCRIPTION, PREV_URLS),
              SOURCE);
        this.objectMapper = objectMapper;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public AdvertisementHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return new AdvertisementHistoryDto(
                SNAPSHOT_ID.extract(rs),
                VERSION.extract(rs),
                ActionType.valueOf(ACTION_TYPE_STR.extract(rs)),
                CHANGED_BY_NAME.extract(rs),
                CREATED_AT.extract(rs),
                TITLE.extract(rs),
                DESCRIPTION.extract(rs),
                parseChanges(CHANGES_SUMMARY.extract(rs)),
                ATTACHMENT_URLS.extract(rs),
                PREV_ID.extract(rs),
                PREV_TITLE.extract(rs),
                PREV_DESCRIPTION.extract(rs),
                PREV_URLS.extract(rs)
        );
    }

    private List<ChangeEntry> parseChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
