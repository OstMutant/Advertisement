package org.ost.advertisement.repository.advertisement;

import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.sqlengine.projection.SqlSelectField;
import org.ost.sqlengine.projection.SqlEntityProjection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.*;
import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public class AdvertisementDescriptor extends SqlEntityProjection<AdvertisementInfoDto> {

    public static final String TABLE        = "advertisement";
    public static final String ALIAS        = "a";
    public static final String SOURCE       = TABLE + " " + ALIAS +
            " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id";
    public static final String COUNT_SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>    ID             = longVal(ALIAS + ".id",             id);
    public static final SqlSelectField<String>  TITLE          = str(ALIAS + ".title",          title);
    public static final SqlSelectField<String>  DESCRIPTION    = str(ALIAS + ".description",    description);
    public static final SqlSelectField<Instant> CREATED_AT     = instant(ALIAS + ".created_at", createdAt);
    public static final SqlSelectField<Instant> UPDATED_AT     = instant(ALIAS + ".updated_at", updatedAt);
    public static final SqlSelectField<Instant> DELETED_AT     = instant(ALIAS + ".deleted_at", "deleted_at");
    public static final SqlSelectField<String>  MAIN_IMAGE_URL = str(ALIAS + ".main_image_url", mainImageUrl);
    public static final SqlSelectField<Integer> IMAGE_COUNT    = intVal(ALIAS + ".image_count",  imageCount);
    public static final SqlSelectField<Long>    USER_ID        = longVal("u.id",                     createdByUserId);
    public static final SqlSelectField<String>  USER_NAME      = str("u.name",                  createdByUserName);
    public static final SqlSelectField<String>  USER_EMAIL     = str("u.email",                 createdByUserEmail);

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AdvertisementDescriptor.TABLE;
        public static final String DELETED_AT         = AdvertisementDescriptor.DELETED_AT.columnName();
        public static final String DELETED_BY_USER_ID = "deleted_by_user_id";
        public static final String MAIN_IMAGE_URL     = AdvertisementDescriptor.MAIN_IMAGE_URL.columnName();
        public static final String IMAGE_COUNT        = AdvertisementDescriptor.IMAGE_COUNT.columnName();
    }

    public AdvertisementDescriptor() {
        super(List.of(ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT,
                      USER_ID, USER_NAME, USER_EMAIL, MAIN_IMAGE_URL, IMAGE_COUNT),
                SOURCE, COUNT_SOURCE);
    }

    @Override
    public AdvertisementInfoDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return AdvertisementInfoDto.builder()
                .id(ID.extract(rs))
                .title(TITLE.extract(rs))
                .description(DESCRIPTION.extract(rs))
                .createdAt(CREATED_AT.extract(rs))
                .updatedAt(UPDATED_AT.extract(rs))
                .createdByUserId(USER_ID.extract(rs))
                .createdByUserName(USER_NAME.extract(rs))
                .createdByUserEmail(USER_EMAIL.extract(rs))
                .mainImageUrl(MAIN_IMAGE_URL.extract(rs))
                .imageCount(IMAGE_COUNT.extract(rs))
                .build();
    }
}
